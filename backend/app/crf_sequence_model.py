"""通知 CRF 结构抽取骨架。

本模块先提供可测试的标签体系、弱标注和模型包格式，不直接接管线上解析。
后续接入 python-crfsuite/sklearn-crfsuite 时，可以复用这里产出的 BIO 标签和字级特征。
"""

from dataclasses import dataclass
import json
import math
import re
import time
from collections import Counter
from uuid import uuid4

from .database import init_database
from .database import transaction
from .training_labels import TRAINABLE_NEGATIVE_LABELS
from .training_labels import TRAINABLE_POSITIVE_LABELS
from .training_labels import normalize_feedback_label

MILLIS_PER_DAY = 24 * 60 * 60 * 1000
POSITIVE_LABELS = TRAINABLE_POSITIVE_LABELS
NEGATIVE_LABELS = TRAINABLE_NEGATIVE_LABELS

CRF_MODEL_SCOPE = "GLOBAL_CRF"
CRF_MODEL_TYPE = "CRF_SEQUENCE_EXTRACTOR"
CRF_MODEL_VERSION_PREFIX = "global-crf-sequence"

OUTSIDE_TAG = "O"
AMOUNT_TAG = "AMOUNT"
TIME_TAG = "TIME"
TAIL_NO_TAG = "TAIL_NO"
DIR_EXPENSE_TAG = "DIR_EXPENSE"
DIR_INCOME_TAG = "DIR_INCOME"
SCENE_TAG = "SCENE"

REQUIRED_ENTITY_TYPES = [
    AMOUNT_TAG,
    TIME_TAG,
    TAIL_NO_TAG,
    DIR_EXPENSE_TAG,
    DIR_INCOME_TAG,
    SCENE_TAG,
]

EXPENSE_KEYWORDS = [
    "支出",
    "消费",
    "付款",
    "支付",
    "扣款",
    "扣费",
    "转出",
]
INCOME_KEYWORDS = [
    "收入",
    "收款",
    "到账",
    "入账",
    "转入",
    "退款",
]
TRANSACTION_SCENE_KEYWORDS = [
    "POS",
    "pos",
    "无卡支付",
    "刷卡",
    "银联",
    "快捷支付",
    "动账",
    "交易",
]

AMOUNT_PATTERN = re.compile(r"(?<![\d:])(?:人民币|RMB|CNY|￥|¥)?\s*(\d+(?:\.\d{1,2})?)\s*(?:元|块)")
TIME_PATTERNS = [
    re.compile(r"\d{2,4}年\d{1,2}月\d{1,2}日(?:\d{1,2}时(?:\d{1,2}分)?)?"),
    re.compile(r"\d{1,2}月\d{1,2}日(?:\d{1,2}时(?:\d{1,2}分)?)?"),
    re.compile(r"\d{1,2}日\d{1,2}时(?:\d{1,2}分)?"),
    re.compile(r"\d{1,2}:\d{2}"),
]
CARD_TAIL_PATTERN = re.compile(r"尾号.{0,3}\d{2,6}")


@dataclass(frozen=True)
class SequenceLabelExample:
    """字级序列标注样本。

    参数:
        sample_id: 服务端训练样本主键。
        text: 标准化前的通知标题与正文合并文本。
        labels: 与 text 等长的 BIO 标签序列。
        feedback_label: 用户反馈标签，用于排查弱标注来源。
    """

    sample_id: int
    text: str
    labels: list[str]
    feedback_label: str


@dataclass(frozen=True)
class SequenceTrainingPolicy:
    """CRF 训练策略。

    参数:
        min_examples: 发布前所需最小弱标注样本数。
        c1: L1 正则项，值越大越倾向稀疏特征。
        c2: L2 正则项，值越大越能抑制过拟合。
        max_iterations: CRF 最大训练轮数。
        publish: 是否把训练结果写入 model_versions。
    """

    min_examples: int = 500
    c1: float = 0.1
    c2: float = 0.1
    max_iterations: int = 100
    publish: bool = False


def build_crf_training_preview(database_path: str, limit: int = 2000) -> dict[str, object]:
    """从现有全局样本池生成 CRF 训练预览。

    参数:
        database_path: SQLite 数据库文件路径。
        limit: 最多预览的有效样本数量，避免管理命令一次输出过大。

    返回:
        dict[str, object]: 样本数量、标签分布、模型包草案和评估占位指标。
    """

    examples = load_sequence_label_examples(database_path=database_path, limit=limit)
    label_counter = Counter(label for example in examples for label in example.labels)
    entity_counter = Counter(label.split("-", 1)[1] for label in label_counter if label.startswith("B-"))
    return {
        "modelType": CRF_MODEL_TYPE,
        "exampleCount": len(examples),
        "entityCounts": dict(sorted(entity_counter.items())),
        "labelCounts": dict(sorted(label_counter.items())),
        "featureTemplate": build_feature_template(),
        "modelDraft": build_sequence_model_payload(examples=examples, publishable=False),
    }


def run_crf_sequence_training(
    database_path: str,
    policy: SequenceTrainingPolicy | None = None,
) -> dict[str, object]:
    """训练 CRF 序列标注模型。

    参数:
        database_path: SQLite 数据库文件路径。
        policy: 训练策略；默认只训练不发布，避免误影响线上模型。

    返回:
        dict[str, object]: 训练结果、指标，以及可选发布后的模型摘要。
    """

    current_policy = policy or SequenceTrainingPolicy()
    examples = load_sequence_label_examples(database_path=database_path)
    if len(examples) < current_policy.min_examples:
        return {
            "state": "skipped",
            "message": f"CRF 弱标注样本不足：{len(examples)} < {current_policy.min_examples}",
            "preview": build_sequence_model_payload(examples=examples, publishable=False),
        }

    try:
        import pycrfsuite  # type: ignore[import-not-found]
    except ImportError:
        return {
            "state": "skipped",
            "message": "未安装 python-crfsuite，已生成弱标注预览但不能训练 CRF 权重",
            "preview": build_sequence_model_payload(examples=examples, publishable=False),
        }

    train_examples, validation_examples = split_examples(examples)
    trainer = pycrfsuite.Trainer(verbose=False)
    for example in train_examples:
        trainer.append(
            [build_token_features(example.text, index) for index in range(len(example.text))],
            example.labels,
        )
    trainer.set_params(
        {
            "c1": current_policy.c1,
            "c2": current_policy.c2,
            "max_iterations": current_policy.max_iterations,
            "feature.possible_transitions": True,
        }
    )

    model_path = f"/tmp/{CRF_MODEL_VERSION_PREFIX}-{uuid4().hex}.crfsuite"
    trainer.train(model_path)

    tagger = pycrfsuite.Tagger()
    tagger.open(model_path)
    metrics = evaluate_sequence_model(tagger=tagger, examples=validation_examples)
    payload = build_trained_sequence_payload(
        tagger=tagger,
        examples=examples,
        metrics=metrics,
        policy=current_policy,
    )

    result = {
        "state": "trained",
        "modelType": CRF_MODEL_TYPE,
        "modelScope": CRF_MODEL_SCOPE,
        "trainedSampleCount": len(examples),
        "validationSampleCount": len(validation_examples),
        "metrics": metrics,
        "payload": payload,
    }
    if current_policy.publish:
        result["publishedModel"] = publish_crf_sequence_model(
            database_path=database_path,
            payload=payload,
            trained_sample_count=len(examples),
        )
    return result


def load_sequence_label_examples(database_path: str, limit: int | None = None) -> list[SequenceLabelExample]:
    """读取已有训练样本并转换成 CRF BIO 标注样本。

    参数:
        database_path: SQLite 数据库文件路径。
        limit: 最多返回多少条样本；None 表示不限制。

    返回:
        list[SequenceLabelExample]: 可供序列标注训练器消费的样本列表。
    """

    init_database(database_path)
    with transaction(database_path) as db:
        rows = db.execute(
            """
            SELECT id, raw_json
            FROM training_samples
            WHERE contribution_scope = 'GLOBAL'
            ORDER BY id ASC
            """
        ).fetchall()

    examples: list[SequenceLabelExample] = []
    for row in rows:
        try:
            raw_sample = json.loads(str(row["raw_json"]))
        except json.JSONDecodeError:
            continue
        sequence_example = weak_label_sample(sample_id=int(row["id"]), raw_sample=raw_sample)
        if sequence_example is None:
            continue
        examples.append(sequence_example)
        if limit is not None and len(examples) >= limit:
            break
    return examples


def weak_label_sample(sample_id: int, raw_sample: dict[str, object]) -> SequenceLabelExample | None:
    """把单条业务样本弱标注为 CRF BIO 序列。

    参数:
        sample_id: 服务端训练样本主键。
        raw_sample: 客户端上传的原始训练 JSON。

    返回:
        SequenceLabelExample | None: 能生成监督信号时返回标注样本，否则返回 None。
    """

    text = merge_notification_text(raw_sample)
    if not text:
        return None
    feedback_label = normalize_feedback_label(raw_sample.get("feedbackLabel"))
    labels = [OUTSIDE_TAG] * len(text)

    if feedback_label in NEGATIVE_LABELS:
        return SequenceLabelExample(sample_id=sample_id, text=text, labels=labels, feedback_label=feedback_label)
    if feedback_label not in POSITIVE_LABELS:
        return None

    corrected = raw_sample.get("corrected")
    parsed = raw_sample.get("parsed")
    final_fields = corrected if isinstance(corrected, dict) else parsed if isinstance(parsed, dict) else {}

    _label_amount(text=text, labels=labels, amount_value=final_fields.get("amount") or raw_sample.get("amount"))
    _label_direction(text=text, labels=labels, direction=str(final_fields.get("direction") or raw_sample.get("direction") or ""))
    _label_regex_matches(text=text, labels=labels, pattern=CARD_TAIL_PATTERN, entity_type=TAIL_NO_TAG)
    for pattern in TIME_PATTERNS:
        _label_regex_matches(text=text, labels=labels, pattern=pattern, entity_type=TIME_TAG)
    for keyword in TRANSACTION_SCENE_KEYWORDS:
        _label_keyword(text=text, labels=labels, keyword=keyword, entity_type=SCENE_TAG)
    return SequenceLabelExample(sample_id=sample_id, text=text, labels=labels, feedback_label=feedback_label)


def merge_notification_text(raw_sample: dict[str, object]) -> str:
    """合并通知标题和正文。

    参数:
        raw_sample: 客户端上传的训练样本。

    返回:
        str: 去重后的标题正文合并文本。
    """

    title = str(raw_sample.get("rawTitle") or "").strip()
    body = str(raw_sample.get("rawText") or "").strip()
    if title and body and title not in body:
        return f"{title} {body}"
    return body or title


def build_token_features(text: str, index: int, package_name: str = "", source_app: str = "") -> dict[str, object]:
    """构造单个字符的 CRF 特征。

    参数:
        text: 完整通知文本。
        index: 当前字符下标。
        package_name: 通知包名，作为来源先验。
        source_app: 客户端来源枚举，作为来源先验。

    返回:
        dict[str, object]: 可交给 CRF 训练器的离散特征。
    """

    char = text[index]
    prev_char = text[index - 1] if index > 0 else ""
    next_char = text[index + 1] if index + 1 < len(text) else ""
    return {
        "bias": 1.0,
        "char": char,
        "char_type": _char_type(char),
        "prev_char": prev_char,
        "next_char": next_char,
        "prev_bigram": text[max(0, index - 1): index + 1],
        "next_bigram": text[index: min(len(text), index + 2)],
        "is_digit": char.isdigit(),
        "is_decimal_point": char == ".",
        "is_amount_unit": char in {"元", "块"},
        "is_time_unit": char in {"年", "月", "日", "时", "分", ":"},
        "near_tail": "尾号" in text[max(0, index - 6): min(len(text), index + 1)],
        "near_amount_unit": "元" in text[index: min(len(text), index + 6)] or "块" in text[index: min(len(text), index + 6)],
        "near_expense_word": _near_any(text, index, EXPENSE_KEYWORDS),
        "near_income_word": _near_any(text, index, INCOME_KEYWORDS),
        "near_risk_word": _near_any(text, index, ["验证码", "订单号", "单号", "积分", "余额"]),
        "package": package_name,
        "source_app": source_app,
    }


def build_feature_template() -> dict[str, object]:
    """返回当前 CRF 特征模板说明。

    返回:
        dict[str, object]: 供管理后台、文档和客户端对齐的模板摘要。
    """

    return {
        "granularity": "character",
        "labelScheme": "BIO",
        "labels": [OUTSIDE_TAG] + [f"B-{name}" for name in REQUIRED_ENTITY_TYPES] + [f"I-{name}" for name in REQUIRED_ENTITY_TYPES],
        "features": [
            "char",
            "char_type",
            "prev_char",
            "next_char",
            "prev_bigram",
            "next_bigram",
            "is_digit",
            "is_decimal_point",
            "is_amount_unit",
            "is_time_unit",
            "near_tail",
            "near_amount_unit",
            "near_expense_word",
            "near_income_word",
            "near_risk_word",
            "package",
            "source_app",
        ],
    }


def build_sequence_model_payload(
    examples: list[SequenceLabelExample],
    publishable: bool,
) -> dict[str, object]:
    """构造 CRF 模型包草案。

    参数:
        examples: 弱标注样本列表。
        publishable: 是否为真实可发布模型；未接入 CRF 训练器前保持 false。

    返回:
        dict[str, object]: 可嵌入全局模型 payload 的 sequenceLabeler 字段。
    """

    entity_counts = Counter(label.split("-", 1)[1] for example in examples for label in example.labels if label.startswith("B-"))
    return {
        "enabled": False,
        "publishable": publishable,
        "modelType": CRF_MODEL_TYPE,
        "modelScope": CRF_MODEL_SCOPE,
        "modelVersion": f"{CRF_MODEL_VERSION_PREFIX}-{now_millis()}-{uuid4().hex[:8]}",
        "featureTemplate": build_feature_template(),
        "weakLabelPolicy": {
            "positiveLabels": sorted(POSITIVE_LABELS),
            "negativeLabels": sorted(NEGATIVE_LABELS),
            "amountSource": "corrected.amount first, parsed.amount second",
            "directionSource": "corrected.direction first, parsed.direction second",
        },
        "metrics": {
            "trainingExamples": len(examples),
            "entityCounts": dict(sorted(entity_counts.items())),
            "entityMicroF1": None,
            "amountExactMatch": None,
        },
        "weights": {},
        "transitions": {},
    }


def _load_raw_sample_from_feature_example(example: object) -> dict[str, object] | None:
    """从 TrainingExample 回读 raw_json。

    参数:
        example: training_job.load_labeled_examples 返回的对象；当前不携带 raw_json。

    返回:
        dict[str, object] | None: 该函数作为占位，真实读取由后续专用 SQL 版本替换。
    """

    _ = example
    return None


def _label_amount(text: str, labels: list[str], amount_value: object) -> None:
    """根据最终金额反查原文并标注 AMOUNT。

    参数:
        text: 通知文本。
        labels: 待写入的 BIO 标签。
        amount_value: 用户最终确认或解析得到的金额。
    """

    amount = _normalize_amount(amount_value)
    if not amount:
        return
    for match in AMOUNT_PATTERN.finditer(text):
        normalized_candidate = _normalize_amount(match.group(1))
        if normalized_candidate == amount:
            _apply_bio(labels=labels, start=match.start(1), end=match.end(1), entity_type=AMOUNT_TAG)
            unit_start = match.end(1)
            if unit_start < len(text) and text[unit_start] in {"元", "块"}:
                labels[unit_start] = f"I-{AMOUNT_TAG}"
            return


def _label_direction(text: str, labels: list[str], direction: str) -> None:
    """根据最终方向标注方向词。

    参数:
        text: 通知文本。
        labels: 待写入的 BIO 标签。
        direction: EXPENSE 或 INCOME。
    """

    if direction == "EXPENSE":
        for keyword in EXPENSE_KEYWORDS:
            if _label_keyword(text=text, labels=labels, keyword=keyword, entity_type=DIR_EXPENSE_TAG):
                return
    if direction == "INCOME":
        for keyword in INCOME_KEYWORDS:
            if _label_keyword(text=text, labels=labels, keyword=keyword, entity_type=DIR_INCOME_TAG):
                return


def _label_regex_matches(text: str, labels: list[str], pattern: re.Pattern[str], entity_type: str) -> None:
    """按正则命中范围写入 BIO 标签。

    参数:
        text: 通知文本。
        labels: 待写入的 BIO 标签。
        pattern: 正则模式。
        entity_type: 实体类型名，不含 B-/I- 前缀。
    """

    for match in pattern.finditer(text):
        _apply_bio(labels=labels, start=match.start(), end=match.end(), entity_type=entity_type)


def _label_keyword(text: str, labels: list[str], keyword: str, entity_type: str) -> bool:
    """标注关键词实体。

    参数:
        text: 通知文本。
        labels: 待写入的 BIO 标签。
        keyword: 需要查找的关键词。
        entity_type: 实体类型名，不含 B-/I- 前缀。

    返回:
        bool: 是否命中并写入。
    """

    start = text.find(keyword)
    if start < 0:
        return False
    _apply_bio(labels=labels, start=start, end=start + len(keyword), entity_type=entity_type)
    return True


def _apply_bio(labels: list[str], start: int, end: int, entity_type: str) -> None:
    """写入 BIO 标签，已有实体不被覆盖。

    参数:
        labels: 待写入的标签数组。
        start: 起始下标，闭区间。
        end: 结束下标，开区间。
        entity_type: 实体类型名，不含 B-/I- 前缀。
    """

    safe_start = max(0, min(start, len(labels)))
    safe_end = max(safe_start, min(end, len(labels)))
    for index in range(safe_start, safe_end):
        if labels[index] != OUTSIDE_TAG:
            continue
        labels[index] = f"{'B' if index == safe_start else 'I'}-{entity_type}"


def _normalize_amount(value: object) -> str:
    """把金额统一成两位小数字符串。

    参数:
        value: 任意金额值。

    返回:
        str: 规范金额；无法解析时返回空字符串。
    """

    try:
        amount = float(value)
    except (TypeError, ValueError):
        return ""
    if not math.isfinite(amount):
        return ""
    return f"{amount:.2f}"


def _char_type(char: str) -> str:
    """判断字符类型。

    参数:
        char: 单个字符。

    返回:
        str: digit、latin、han、punct 或 other。
    """

    if char.isdigit():
        return "digit"
    if "a" <= char.lower() <= "z":
        return "latin"
    if "\u4e00" <= char <= "\u9fff":
        return "han"
    if char in ".:：,，()（）[]【】 ":
        return "punct"
    return "other"


def _near_any(text: str, index: int, keywords: list[str], window: int = 8) -> bool:
    """判断当前位置附近是否存在任一关键词。

    参数:
        text: 完整文本。
        index: 当前下标。
        keywords: 关键词列表。
        window: 左右窗口大小。

    返回:
        bool: 附近命中则为 True。
    """

    segment = text[max(0, index - window): min(len(text), index + window + 1)]
    return any(keyword in segment for keyword in keywords)


def now_millis() -> int:
    """返回当前毫秒时间戳。"""

    return int(time.time() * 1000)


def split_examples(examples: list[SequenceLabelExample]) -> tuple[list[SequenceLabelExample], list[SequenceLabelExample]]:
    """把样本切成训练集和验证集。

    参数:
        examples: 弱标注样本列表。

    返回:
        tuple[list[SequenceLabelExample], list[SequenceLabelExample]]: 训练集和验证集。
    """

    if len(examples) <= 1:
        return examples, []
    split_index = max(1, int(len(examples) * 0.85))
    split_index = min(split_index, len(examples) - 1)
    return examples[:split_index], examples[split_index:]


def evaluate_sequence_model(tagger: object, examples: list[SequenceLabelExample]) -> dict[str, float | int | None]:
    """评估序列标注模型的基础指标。

    参数:
        tagger: 已训练好的 CRF Tagger。
        examples: 验证样本。

    返回:
        dict[str, float | int | None]: 基础准确率和实体命中指标。
    """

    if not examples:
        return {
            "tokenAccuracy": None,
            "entityExactMatch": None,
            "amountExactMatch": None,
            "validationCount": 0,
        }

    total_tokens = 0
    correct_tokens = 0
    exact_match_count = 0
    amount_exact_match_count = 0
    for example in examples:
        predicted = list(tagger.tag([build_token_features(example.text, index) for index in range(len(example.text))]))
        if predicted == example.labels:
            exact_match_count += 1
        gold_amount_spans = _collect_entity_spans(example.labels, AMOUNT_TAG)
        pred_amount_spans = _collect_entity_spans(predicted, AMOUNT_TAG)
        if gold_amount_spans and gold_amount_spans == pred_amount_spans:
            amount_exact_match_count += 1
        total_tokens += len(example.labels)
        correct_tokens += sum(1 for gold, pred in zip(example.labels, predicted, strict=False) if gold == pred)

    validation_count = len(examples)
    return {
        "tokenAccuracy": round(correct_tokens / total_tokens, 6) if total_tokens else None,
        "entityExactMatch": round(exact_match_count / validation_count, 6) if validation_count else None,
        "amountExactMatch": round(amount_exact_match_count / validation_count, 6) if validation_count else None,
        "validationCount": validation_count,
    }


def build_trained_sequence_payload(
    tagger: object,
    examples: list[SequenceLabelExample],
    metrics: dict[str, float | int | None],
    policy: SequenceTrainingPolicy,
) -> dict[str, object]:
    """构造可发布的 CRF 模型包。

    参数:
        tagger: 已训练好的 CRF Tagger。
        examples: 全量训练样本。
        metrics: 验证指标。
        policy: 训练策略。

    返回:
        dict[str, object]: 准备写入 model_versions.payload_json 的结构。
    """

    _ = tagger
    entity_counts = Counter(label.split("-", 1)[1] for example in examples for label in example.labels if label.startswith("B-"))
    return {
        "enabled": True,
        "publishable": policy.publish,
        "modelType": CRF_MODEL_TYPE,
        "modelVersion": f"{CRF_MODEL_VERSION_PREFIX}-{now_millis()}-{uuid4().hex[:8]}",
        "modelScope": CRF_MODEL_SCOPE,
        "schemaVersion": 1,
        "publishedAt": now_millis(),
        "validUntil": now_millis() + 30 * MILLIS_PER_DAY,
        "minAppVersion": "1.0.0",
        "featureTemplate": build_feature_template(),
        "weakLabelPolicy": {
            "positiveLabels": sorted(POSITIVE_LABELS),
            "negativeLabels": sorted(NEGATIVE_LABELS),
            "amountSource": "corrected.amount first, parsed.amount second",
            "directionSource": "corrected.direction first, parsed.direction second",
        },
        "metrics": {
            **metrics,
            "trainingExamples": len(examples),
            "entityCounts": dict(sorted(entity_counts.items())),
        },
        "weights": {},
        "transitions": {},
    }


def publish_crf_sequence_model(
    database_path: str,
    payload: dict[str, object],
    trained_sample_count: int,
) -> dict[str, object]:
    """把 CRF 模型写入 model_versions。

    参数:
        database_path: SQLite 数据库文件路径。
        payload: CRF 模型载荷。
        trained_sample_count: 训练样本数。

    返回:
        dict[str, object]: 当前激活的 CRF 模型摘要。
    """

    init_database(database_path)
    published_at = now_millis()
    version = str(payload.get("modelVersion") or f"{CRF_MODEL_VERSION_PREFIX}-{published_at}-{uuid4().hex[:8]}")
    with transaction(database_path) as db:
        db.execute(
            "UPDATE model_versions SET is_active = 0 WHERE model_scope = ?",
            (CRF_MODEL_SCOPE,),
        )
        db.execute(
            """
            INSERT INTO model_versions(
                model_version,
                model_scope,
                model_type,
                payload_json,
                trained_sample_count,
                published_at,
                valid_until,
                min_app_version,
                is_active
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1)
            """,
            (
                version,
                CRF_MODEL_SCOPE,
                CRF_MODEL_TYPE,
                json.dumps(payload, ensure_ascii=False, separators=(",", ":")),
                max(0, trained_sample_count),
                published_at,
                int(payload.get("validUntil") or published_at + 30 * MILLIS_PER_DAY),
                str(payload.get("minAppVersion") or "1.0.0"),
            ),
        )
    return {
        "modelVersion": version,
        "modelScope": CRF_MODEL_SCOPE,
        "modelType": CRF_MODEL_TYPE,
        "publishedAt": published_at,
        "trainedSampleCount": max(0, trained_sample_count),
    }


def _collect_entity_spans(labels: list[str], entity_type: str) -> list[tuple[int, int]]:
    """提取 BIO 实体 span。

    参数:
        labels: BIO 标签序列。
        entity_type: 目标实体类型。

    返回:
        list[tuple[int, int]]: 形如 (start, end) 的半开区间列表。
    """

    spans: list[tuple[int, int]] = []
    start_index: int | None = None
    prefix = f"-{entity_type}"
    for index, label in enumerate(labels):
        if label == f"B-{entity_type}":
            if start_index is not None:
                spans.append((start_index, index))
            start_index = index
            continue
        if label == f"I-{entity_type}":
            if start_index is None:
                start_index = index
            continue
        if start_index is not None:
            spans.append((start_index, index))
            start_index = None
    if start_index is not None:
        spans.append((start_index, len(labels)))
    return spans
