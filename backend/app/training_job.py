"""全局模型训练任务。

本模块把全局样本池里的通知样本训练成轻量逻辑回归模型。
训练任务默认由定时容器每天触发一次，只有新增样本和标签质量满足阈值时才发布新全局模型。
"""

from dataclasses import dataclass
import json
import math
from collections.abc import Sequence
from uuid import uuid4

from .database import init_database, transaction
from .global_model_service import BOOTSTRAP_MODEL_PAYLOAD
from .global_model_service import GLOBAL_MODEL_SCOPE
from .global_model_service import count_global_training_samples
from .global_model_service import get_active_global_model
from .global_model_service import publish_global_model
from .response import now_millis
from .training_labels import TRAINABLE_NEGATIVE_LABELS
from .training_labels import TRAINABLE_POSITIVE_LABELS
from .training_labels import normalize_feedback_label

SOURCE_CLASSES = {
    "source.trusted_payment": "TRUSTED_PAYMENT",
    "source.trusted_bank": "TRUSTED_BANK",
    "source.trusted_finance": "TRUSTED_FINANCE",
    "source.unknown": "UNKNOWN",
}
AMOUNT_KINDS = {
    "amount.with_currency": "WITH_CURRENCY",
    "amount.with_unit": "WITH_UNIT",
    "amount.bare_number": "BARE_NUMBER",
}
BOOLEAN_FEATURES = {
    # 这些键直接对齐 Android 端 `NotificationMlConfidenceModel` 的稳定特征名。
    "direction.income": "hitIncome",
    "direction.expense": "hitExpense",
    "structure.tail_number": "hasTailNumber",
    "structure.strong_account_keyword": "hasStrongAccountKeyword",
    "structure.generic_account_keyword": "hasGenericAccountKeyword",
    "structure.explicit_time": "hasExplicitTime",
    "structure.time_year": "hasTimeYearKeyword",
    "structure.time_month": "hasTimeMonthKeyword",
    "structure.time_day": "hasTimeDayKeyword",
    "structure.time_hour": "hasTimeHourKeyword",
    "structure.clock_time": "hasClockTime",
    "semantic.transaction_scene": "hasTransactionSceneKeyword",
    "semantic.counterparty": "hasCounterpartyKeyword",
    "semantic.extracted_counterparty": "hasExtractedCounterparty",
    "semantic.currency_keyword": "hasCurrencyKeyword",
    "semantic.bank_entity": "hasBankEntityKeyword",
    "semantic.finance_settled": "hasFinanceSettledKeyword",
    "semantic.finance_entity": "hasFinanceEntityKeyword",
    "risk.marketing": "hasMarketingKeyword",
    "risk.asset_valuation_keyword": "hasAssetValuationKeyword",
    "risk.settlement_evidence": "hasSettlementEvidenceKeyword",
    "risk.unreliable_amount": "hasReliableAmount",
    "risk.strong_bank_structure": "hasStrongBankStructureSignal",
    "risk.direction_conflict": "hasDirectionConflict",
    "risk.asset_valuation": "hasUnsettledAssetValuationRisk",
    "feedback.edited_manually": "editedManually",
}
NUMERIC_FEATURE_KEYS = [
    "ruleConfidenceScore",
    "rawTextLengthLog",
    "amountLog",
]
FEATURE_COLUMNS = list(SOURCE_CLASSES.keys()) + list(AMOUNT_KINDS.keys()) + list(BOOLEAN_FEATURES.keys()) + NUMERIC_FEATURE_KEYS
DEFAULT_THRESHOLDS = {
    "high": 0.86,
    "medium": 0.62,
    "threshold.asset_valuation_max_score": 0.45,
    "threshold.direction_conflict_max_score": 0.61,
    "threshold.unreliable_amount_max_score": 0.85,
    "threshold.weak_marketing_max_score": 0.55,
}
POSITIVE_WEIGHT_FLOORS = {
    # 这些基础结构信号来自产品规则判断，训练样本缺少新字段或样本分布偏斜时仍保留默认正向先验。
    "source.trusted_payment": 1.55,
    "source.trusted_bank": 1.40,
    "source.trusted_finance": 0.55,
    "amount.with_currency": 1.20,
    "amount.with_unit": 1.00,
    "direction.income": 1.05,
    "direction.expense": 1.05,
    "structure.tail_number": 0.75,
    "structure.strong_account_keyword": 0.55,
    "structure.generic_account_keyword": 0.25,
    "structure.explicit_time": 0.25,
    "structure.time_year": 0.08,
    "structure.time_month": 0.08,
    "structure.time_day": 0.08,
    "structure.time_hour": 0.08,
    "structure.clock_time": 0.15,
    "semantic.transaction_scene": 0.45,
    "semantic.counterparty": 0.20,
    "semantic.extracted_counterparty": 0.10,
    "semantic.currency_keyword": 0.25,
    "semantic.bank_entity": 0.35,
    "semantic.finance_settled": 0.55,
    "semantic.finance_entity": 0.25,
}


@dataclass(frozen=True)
class TrainingExample:
    """训练样本。

    参数：
        sample_id: 服务端训练样本主键。
        label: 二分类标签，1 表示应该信任候选，0 表示应该压低或过滤。
        features: 结构化特征名到数值的映射。
    """

    sample_id: int
    label: int
    features: dict[str, float]


@dataclass(frozen=True)
class TrainingPolicy:
    """训练发布策略。

    参数：
        min_new_samples: 距离当前线上模型至少需要新增多少条有效标签样本。
        min_total_samples: 总有效标签样本至少需要多少条。
        min_per_class: 正负样本各自至少需要多少条。
        min_accuracy: 验证集准确率低于该值时不自动发布。
        max_false_positive_rate: 验证集负样本误判率高于该值时不自动发布。
        force: 是否跳过数量和质量门槛；标签只有单类时仍然不会训练。
    """

    min_new_samples: int = 1000
    min_total_samples: int = 100
    min_per_class: int = 10
    min_accuracy: float = 0.65
    max_false_positive_rate: float = 0.25
    force: bool = False


def run_global_training(
    database_path: str,
    trigger_type: str = "manual",
    policy: TrainingPolicy | None = None,
    selected_sample_ids: Sequence[int] | None = None,
) -> dict[str, object]:
    """执行一次全局模型训练检查。

    参数：
        database_path: SQLite 数据库文件路径。
        trigger_type: 触发来源，例如 manual、admin 或 scheduled。
        policy: 训练发布策略；为空时使用生产默认门槛。
        selected_sample_ids: 管理员人工选择的样本 ID；为空时不会训练发布模型。

    返回：
        dict[str, object]: 训练任务结果摘要。
    """

    init_database(database_path)
    active_model = get_active_global_model(database_path)
    job_id = f"train_{now_millis()}_{uuid4().hex[:8]}"
    current_policy = policy or TrainingPolicy()
    _insert_training_job(database_path, job_id=job_id, trigger_type=trigger_type)

    try:
        normalized_sample_ids = normalize_selected_sample_ids(selected_sample_ids)
        if not normalized_sample_ids:
            sample_count = count_global_training_samples(database_path)
            return _finish_training_job(
                database_path=database_path,
                job_id=job_id,
                state="SKIPPED",
                sample_count=sample_count,
                message="自动训练已关闭，请在管理后台样本页人工选择样本后发布模型",
            )

        examples = load_labeled_examples(
            database_path=database_path,
            selected_sample_ids=normalized_sample_ids,
        )
        sample_count = count_global_training_samples(database_path)
        positive_count = sum(1 for example in examples if example.label == 1)
        negative_count = sum(1 for example in examples if example.label == 0)
        labeled_count = len(examples)
        # 人工选择样本发布时，样本 ID 集合本身就是管理员确认过的发布范围，不再按线上模型历史样本数抵扣。
        new_sample_count = labeled_count

        skip_message = _build_skip_message(
            policy=current_policy,
            labeled_count=labeled_count,
            positive_count=positive_count,
            negative_count=negative_count,
            new_sample_count=new_sample_count,
        )
        if skip_message:
            default_model = publish_default_model_if_needed(
                database_path=database_path,
                active_model=active_model,
                reason=skip_message,
                training_job_id=job_id,
            )
            state = "DEFAULT_PUBLISHED" if default_model is not None else "SKIPPED"
            model_version = str(default_model["modelVersion"]) if default_model is not None else None
            message = f"{skip_message}；已发布默认模型" if default_model is not None else f"{skip_message}；当前已是默认模型"
            return _finish_training_job(
                database_path=database_path,
                job_id=job_id,
                state=state,
                sample_count=sample_count,
                labeled_sample_count=labeled_count,
                positive_count=positive_count,
                negative_count=negative_count,
                new_sample_count=new_sample_count,
                model_version=model_version,
                message=message,
            )

        trained = _train_logistic_regression(examples)
        metrics = trained["metrics"]
        quality_message = _build_quality_message(policy=current_policy, metrics=metrics)
        if quality_message:
            return _finish_training_job(
                database_path=database_path,
                job_id=job_id,
                state="SKIPPED",
                sample_count=sample_count,
                labeled_sample_count=labeled_count,
                positive_count=positive_count,
                negative_count=negative_count,
                new_sample_count=new_sample_count,
                metrics=metrics,
                message=quality_message,
            )

        model = publish_global_model(
            database_path=database_path,
            weights=trained["weights"],
            thresholds=DEFAULT_THRESHOLDS,
            trained_sample_count=labeled_count,
            feature_columns=FEATURE_COLUMNS,
            metrics=metrics,
            training_job_id=job_id,
        )
        return _finish_training_job(
            database_path=database_path,
            job_id=job_id,
            state="PUBLISHED",
            sample_count=sample_count,
            labeled_sample_count=labeled_count,
            positive_count=positive_count,
            negative_count=negative_count,
            new_sample_count=new_sample_count,
            model_version=str(model["modelVersion"]),
            metrics=metrics,
            message="已发布新的全局模型",
        )
    except Exception as exc:
        return _finish_training_job(
            database_path=database_path,
            job_id=job_id,
            state="FAILED",
            message=f"训练失败：{exc}",
        )


def load_labeled_examples(
    database_path: str,
    selected_sample_ids: Sequence[int] | None = None,
) -> list[TrainingExample]:
    """从全局样本池读取可训练样本。

    参数：
        database_path: SQLite 数据库文件路径。
        selected_sample_ids: 管理员人工选择的样本 ID；为空时读取全部有效标签样本，仅供后台统计使用。

    返回：
        list[TrainingExample]: 已映射好标签和特征的训练样本。
    """

    init_database(database_path)
    normalized_sample_ids = normalize_selected_sample_ids(selected_sample_ids)
    with transaction(database_path) as db:
        if normalized_sample_ids:
            placeholders = ",".join("?" for _ in normalized_sample_ids)
            rows = db.execute(
                f"""
                SELECT id, raw_json
                FROM training_samples
                WHERE contribution_scope = ?
                  AND id IN ({placeholders})
                ORDER BY id ASC
                """,
                (GLOBAL_MODEL_SCOPE, *normalized_sample_ids),
            ).fetchall()
        else:
            rows = db.execute(
                """
                SELECT id, raw_json
                FROM training_samples
                WHERE contribution_scope = ?
                ORDER BY id ASC
                """,
                (GLOBAL_MODEL_SCOPE,),
            ).fetchall()

    examples: list[TrainingExample] = []
    for row in rows:
        try:
            raw_sample = json.loads(str(row["raw_json"]))
        except json.JSONDecodeError:
            continue
        label = resolve_training_label(raw_sample)
        if label is None:
            continue
        examples.append(
            TrainingExample(
                sample_id=int(row["id"]),
                label=label,
                features=extract_feature_vector(raw_sample),
            )
        )
    return examples


def normalize_selected_sample_ids(selected_sample_ids: Sequence[int] | None) -> list[int]:
    """归一化管理员选择的样本 ID。

    参数：
        selected_sample_ids: 表单或命令行传入的样本 ID 序列。

    返回：
        list[int]: 去重、过滤非法值并保持原始顺序后的正整数 ID。
    """

    if selected_sample_ids is None:
        return []
    normalized: list[int] = []
    seen: set[int] = set()
    for raw_id in selected_sample_ids:
        try:
            sample_id = int(raw_id)
        except (TypeError, ValueError):
            continue
        if sample_id <= 0 or sample_id in seen:
            continue
        normalized.append(sample_id)
        seen.add(sample_id)
    return normalized


def resolve_training_label(raw_sample: dict[str, object]) -> int | None:
    """把用户反馈标签映射成二分类训练标签。

    参数：
        raw_sample: 客户端上传的单条 JSON 样本。

    返回：
        int | None: 正样本返回 1，负样本返回 0，暂不可靠的样本返回 None。
    """

    label = normalize_feedback_label(raw_sample.get("feedbackLabel"))
    if label in TRAINABLE_POSITIVE_LABELS:
        return 1
    if label in TRAINABLE_NEGATIVE_LABELS:
        return 0
    # 自动入账未复核、已过滤未复核、待确认、重复待确认和纯原文样本都没有用户监督信号。
    return None


def extract_feature_vector(raw_sample: dict[str, object]) -> dict[str, float]:
    """抽取结构化训练特征。

    参数：
        raw_sample: 客户端上传的单条 JSON 样本。

    返回：
        dict[str, float]: 固定特征列到数值的映射。
    """

    features = raw_sample.get("features")
    feature_dict = features if isinstance(features, dict) else {}
    values = {column: 0.0 for column in FEATURE_COLUMNS}

    source_class = str(feature_dict.get("sourceClass") or _fallback_source_class(raw_sample))
    for column_name, source_value in SOURCE_CLASSES.items():
        values[column_name] = 1.0 if source_class == source_value else 0.0

    amount_kind = str(feature_dict.get("amountKind") or "")
    for column_name, amount_value in AMOUNT_KINDS.items():
        values[column_name] = 1.0 if amount_kind == amount_value else 0.0

    for column_name, source_key in BOOLEAN_FEATURES.items():
        # 结构化特征优先；editedManually 这类反馈字段在样本顶层。
        source = feature_dict if source_key in feature_dict else raw_sample
        feature_enabled = bool(source.get(source_key))
        if column_name == "risk.unreliable_amount":
            # Android 端模型使用“金额不可靠”作为风险特征，训练时需要和 hasReliableAmount 取反。
            feature_enabled = not feature_enabled
        values[column_name] = 1.0 if feature_enabled else 0.0

    values["ruleConfidenceScore"] = round(_to_float(raw_sample.get("confidenceScore")), 6)
    raw_text = str(raw_sample.get("rawText") or "")
    values["rawTextLengthLog"] = min(math.log1p(len(raw_text)) / 8.0, 1.0)
    values["amountLog"] = min(math.log1p(abs(_to_float(raw_sample.get("amount")))) / 8.0, 1.0)
    return values


def list_training_jobs(database_path: str, limit: int = 10) -> list[dict[str, object]]:
    """列出最近训练任务。

    参数：
        database_path: SQLite 数据库文件路径。
        limit: 最大返回数量。

    返回：
        list[dict[str, object]]: 训练任务摘要列表。
    """

    init_database(database_path)
    with transaction(database_path) as db:
        rows = db.execute(
            """
            SELECT job_id, trigger_type, state, started_at, finished_at,
                   sample_count, labeled_sample_count, positive_count, negative_count,
                   new_sample_count, model_version, metrics_json, message
            FROM training_jobs
            ORDER BY started_at DESC
            LIMIT ?
            """,
            (max(1, min(limit, 50)),),
        ).fetchall()
    return [_row_to_training_job(row) for row in rows]


def training_dataset_summary(database_path: str) -> dict[str, object]:
    """统计全局训练数据集。

    参数：
        database_path: SQLite 数据库文件路径。

    返回：
        dict[str, object]: 全局样本池与有效标签数量摘要。
    """

    examples = load_labeled_examples(database_path)
    return {
        "totalSamples": count_global_training_samples(database_path),
        "labeledSamples": len(examples),
        "positiveSamples": sum(1 for example in examples if example.label == 1),
        "negativeSamples": sum(1 for example in examples if example.label == 0),
    }


def _train_logistic_regression(examples: list[TrainingExample]) -> dict[str, object]:
    """训练逻辑回归模型。

    参数：
        examples: 已标注训练样本。

    返回：
        dict[str, object]: 权重和评估指标。
    """

    import numpy as np
    from sklearn.linear_model import LogisticRegression
    from sklearn.metrics import accuracy_score
    from sklearn.model_selection import train_test_split

    x = np.array([[example.features[column] for column in FEATURE_COLUMNS] for example in examples], dtype=float)
    y = np.array([example.label for example in examples], dtype=int)
    validation_mode = "holdout"

    if len(examples) >= 10 and min(int((y == 0).sum()), int((y == 1).sum())) >= 2:
        x_train, x_valid, y_train, y_valid = train_test_split(
            x,
            y,
            test_size=0.2,
            random_state=42,
            stratify=y,
        )
    else:
        # 样本较少时只做训练集自检；自动发布门槛仍由外层 policy 控制。
        validation_mode = "training-set"
        x_train, x_valid, y_train, y_valid = x, x, y, y

    classifier = LogisticRegression(
        max_iter=500,
        class_weight="balanced",
        solver="liblinear",
        random_state=42,
    )
    classifier.fit(x_train, y_train)
    probabilities = classifier.predict_proba(x_valid)[:, 1]
    predictions = (probabilities >= 0.5).astype(int)
    negative_mask = y_valid == 0
    false_positive_count = int(((predictions == 1) & negative_mask).sum())
    negative_count = int(negative_mask.sum())
    metrics = {
        "validationMode": validation_mode,
        "accuracy": round(float(accuracy_score(y_valid, predictions)), 6),
        "falsePositiveRate": round(false_positive_count / max(negative_count, 1), 6),
        "validationSampleCount": int(len(y_valid)),
        "falsePositiveCount": false_positive_count,
        "validationNegativeCount": negative_count,
    }
    weights = {"bias": round(float(classifier.intercept_[0]), 6)}
    for column_name, coefficient in zip(FEATURE_COLUMNS, classifier.coef_[0], strict=True):
        weights[column_name] = round(float(coefficient), 6)
    apply_positive_weight_floors(weights)
    return {"weights": weights, "metrics": metrics}


def apply_positive_weight_floors(weights: dict[str, float]) -> None:
    """为基础正向特征保留默认权重下限。

    参数：
        weights: 逻辑回归训练后准备发布的权重字典。
    """

    for column_name, floor_value in POSITIVE_WEIGHT_FLOORS.items():
        weights[column_name] = round(max(float(weights.get(column_name, 0.0)), floor_value), 6)


def _build_skip_message(
    policy: TrainingPolicy,
    labeled_count: int,
    positive_count: int,
    negative_count: int,
    new_sample_count: int,
) -> str | None:
    """生成数量门槛跳过原因。

    参数：
        policy: 当前训练发布策略。
        labeled_count: 可训练标签样本数。
        positive_count: 正样本数。
        negative_count: 负样本数。
        new_sample_count: 相对当前线上模型的新增有效样本数。

    返回：
        str | None: 需要跳过时返回原因，否则返回 None。
    """

    if positive_count == 0 or negative_count == 0:
        return "正负样本不足，至少需要同时存在正样本和负样本"
    if policy.force:
        return None
    if new_sample_count < policy.min_new_samples:
        return f"新增有效样本不足：{new_sample_count}/{policy.min_new_samples}"
    if labeled_count < policy.min_total_samples:
        return f"有效标签样本不足：{labeled_count}/{policy.min_total_samples}"
    if positive_count < policy.min_per_class or negative_count < policy.min_per_class:
        return f"正负样本分布不足：正样本 {positive_count}，负样本 {negative_count}"
    return None


def publish_default_model_if_needed(
    database_path: str,
    active_model: dict[str, object],
    reason: str,
    training_job_id: str,
) -> dict[str, object] | None:
    """在样本不足时确保线上使用默认模型。

    参数：
        database_path: SQLite 数据库文件路径。
        active_model: 当前线上激活模型。
        reason: 本次训练跳过原因，会写入默认模型指标，便于后台追踪。
        training_job_id: 当前训练任务 ID。

    返回：
        dict[str, object] | None: 需要切回默认模型时返回新模型；已经是默认权重时返回 None。
    """

    if active_model_has_default_weights(active_model):
        return None
    return publish_global_model(
        database_path=database_path,
        weights=dict(BOOTSTRAP_MODEL_PAYLOAD["weights"]),
        thresholds=dict(BOOTSTRAP_MODEL_PAYLOAD["thresholds"]),
        trained_sample_count=0,
        model_version=f"global-linear-confidence-default-{now_millis()}",
        feature_columns=list(BOOTSTRAP_MODEL_PAYLOAD["weights"].keys()),
        metrics={
            "mode": "default_weights",
            "reason": reason,
            "previousModel": str(active_model.get("modelVersion") or ""),
        },
        training_job_id=training_job_id,
    )


def active_model_has_default_weights(active_model: dict[str, object]) -> bool:
    """判断当前激活模型是否已经等价于默认权重。

    参数：
        active_model: 当前线上激活模型。

    返回：
        bool: 默认模型权重全部一致时返回 True。
    """

    weights = active_model.get("weights")
    if not isinstance(weights, dict):
        return False
    default_weights = BOOTSTRAP_MODEL_PAYLOAD["weights"]
    if not isinstance(default_weights, dict):
        return False
    for key, default_value in default_weights.items():
        try:
            actual_value = float(weights.get(key))
            expected_value = float(default_value)
        except (TypeError, ValueError):
            return False
        if abs(actual_value - expected_value) > 0.000001:
            return False
    return True


def _build_quality_message(policy: TrainingPolicy, metrics: dict[str, object]) -> str | None:
    """生成质量门槛跳过原因。

    参数：
        policy: 当前训练发布策略。
        metrics: 模型验证指标。

    返回：
        str | None: 质量不达标时返回原因，否则返回 None。
    """

    if policy.force:
        return None
    accuracy = float(metrics.get("accuracy") or 0.0)
    false_positive_rate = float(metrics.get("falsePositiveRate") or 1.0)
    if accuracy < policy.min_accuracy:
        return f"验证准确率不足：{accuracy:.4f}/{policy.min_accuracy:.4f}"
    if false_positive_rate > policy.max_false_positive_rate:
        return f"负样本误判率过高：{false_positive_rate:.4f}/{policy.max_false_positive_rate:.4f}"
    return None


def _insert_training_job(database_path: str, job_id: str, trigger_type: str) -> None:
    """创建训练任务记录。

    参数：
        database_path: SQLite 数据库文件路径。
        job_id: 训练任务 ID。
        trigger_type: 触发来源。
    """

    with transaction(database_path) as db:
        db.execute(
            """
            INSERT INTO training_jobs(job_id, trigger_type, state, started_at)
            VALUES (?, ?, 'RUNNING', ?)
            """,
            (job_id, trigger_type, now_millis()),
        )


def _finish_training_job(
    database_path: str,
    job_id: str,
    state: str,
    sample_count: int = 0,
    labeled_sample_count: int = 0,
    positive_count: int = 0,
    negative_count: int = 0,
    new_sample_count: int = 0,
    model_version: str | None = None,
    metrics: dict[str, object] | None = None,
    message: str | None = None,
) -> dict[str, object]:
    """结束训练任务记录。

    参数：
        database_path: SQLite 数据库文件路径。
        job_id: 训练任务 ID。
        state: 结束状态。
        sample_count: 全局样本池总数。
        labeled_sample_count: 可训练标签样本数。
        positive_count: 正样本数。
        negative_count: 负样本数。
        new_sample_count: 新增有效样本数。
        model_version: 发布成功时的新模型版本。
        metrics: 训练评估指标。
        message: 任务结果说明。

    返回：
        dict[str, object]: 与数据库记录一致的任务摘要。
    """

    metrics_json = json.dumps(metrics or {}, ensure_ascii=False, separators=(",", ":"))
    finished_at = now_millis()
    with transaction(database_path) as db:
        db.execute(
            """
            UPDATE training_jobs
            SET state = ?,
                finished_at = ?,
                sample_count = ?,
                labeled_sample_count = ?,
                positive_count = ?,
                negative_count = ?,
                new_sample_count = ?,
                model_version = ?,
                metrics_json = ?,
                message = ?
            WHERE job_id = ?
            """,
            (
                state,
                finished_at,
                sample_count,
                labeled_sample_count,
                positive_count,
                negative_count,
                new_sample_count,
                model_version,
                metrics_json,
                message,
                job_id,
            ),
        )
    return {
        "jobId": job_id,
        "state": state,
        "finishedAt": finished_at,
        "sampleCount": sample_count,
        "labeledSampleCount": labeled_sample_count,
        "positiveCount": positive_count,
        "negativeCount": negative_count,
        "newSampleCount": new_sample_count,
        "modelVersion": model_version,
        "metrics": metrics or {},
        "message": message,
    }


def _row_to_training_job(row: object) -> dict[str, object]:
    """转换训练任务数据库行。

    参数：
        row: sqlite3.Row 查询结果。

    返回：
        dict[str, object]: 管理后台可直接展示的任务摘要。
    """

    return {
        "jobId": row["job_id"],
        "triggerType": row["trigger_type"],
        "state": row["state"],
        "startedAt": row["started_at"],
        "finishedAt": row["finished_at"],
        "sampleCount": row["sample_count"],
        "labeledSampleCount": row["labeled_sample_count"],
        "positiveCount": row["positive_count"],
        "negativeCount": row["negative_count"],
        "newSampleCount": row["new_sample_count"],
        "modelVersion": row["model_version"],
        "metrics": json.loads(row["metrics_json"] or "{}"),
        "message": row["message"],
    }


def _fallback_source_class(raw_sample: dict[str, object]) -> str:
    """根据客户端来源枚举兜底推断来源类别。

    参数：
        raw_sample: 客户端上传的单条 JSON 样本。

    返回：
        str: 来源类别名称。
    """

    source_app = str(raw_sample.get("sourceApp") or "")
    if source_app in {"WECHAT", "ALIPAY"}:
        return "TRUSTED_PAYMENT"
    if source_app == "BANK_SMS":
        return "TRUSTED_BANK"
    return "UNKNOWN"


def _to_float(value: object) -> float:
    """安全转换浮点数。

    参数：
        value: 原始输入值。

    返回：
        float: 转换失败时返回 0。
    """

    try:
        return float(value)
    except (TypeError, ValueError):
        return 0.0
