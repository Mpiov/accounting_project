"""全局模型注册服务。

本模块负责管理服务器发布给所有会员客户端的统一模型版本。
训练样本可以来自不同用户，但最终只产出一个全局模型，不做单用户定制模型。
"""

import json
from uuid import uuid4

from .database import init_database, transaction
from .response import now_millis

GLOBAL_MODEL_SCOPE = "GLOBAL"
GLOBAL_MODEL_TYPE = "LINEAR_CONFIDENCE"
BOOTSTRAP_MODEL_VERSION = "global-linear-confidence-bootstrap-20260502"
MILLIS_PER_DAY = 24 * 60 * 60 * 1000

BOOTSTRAP_MODEL_PAYLOAD: dict[str, object] = {
    "weights": {
        "bias": -2.0,
        "source.trusted_payment": 1.55,
        "source.trusted_bank": 1.4,
        "amount.with_currency": 1.2,
        "amount.with_unit": 1.0,
        "structure.tail_number": 0.75,
        "structure.explicit_time": 0.25,
        "structure.time_year": 0.08,
        "structure.time_month": 0.08,
        "structure.time_day": 0.08,
        "structure.time_hour": 0.08,
        "structure.clock_time": 0.15,
        "semantic.transaction_scene": 0.45,
        "direction.income": 1.05,
        "direction.expense": 1.05,
        "risk.marketing": -1.15,
        "risk.asset_valuation": -1.6,
    },
    "thresholds": {
        "high": 0.86,
        "medium": 0.62,
        "threshold.asset_valuation_max_score": 0.45,
        "threshold.direction_conflict_max_score": 0.61,
        "threshold.unreliable_amount_max_score": 0.85,
        "threshold.weak_marketing_max_score": 0.55,
    },
    "signature": "unsigned-dev-global-model",
}


def ensure_bootstrap_global_model(database_path: str) -> None:
    """确保数据库里至少存在一个全局模型。

    参数：
        database_path: SQLite 数据库文件路径。

    返回：
        None: 该函数只负责补齐模型注册表。
    """

    init_database(database_path)
    with transaction(database_path) as db:
        row = db.execute(
            """
            SELECT model_version
            FROM model_versions
            WHERE model_scope = ? AND is_active = 1
            LIMIT 1
            """,
            (GLOBAL_MODEL_SCOPE,),
        ).fetchone()
        if row is not None:
            return

        published_at = now_millis()
        # 冷启动阶段先发布一个人工权重模型，后续训练任务会用全局样本池覆盖它。
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
            VALUES (?, ?, ?, ?, 0, ?, ?, '1.0.0', 1)
            """,
            (
                BOOTSTRAP_MODEL_VERSION,
                GLOBAL_MODEL_SCOPE,
                GLOBAL_MODEL_TYPE,
                json.dumps(BOOTSTRAP_MODEL_PAYLOAD, ensure_ascii=False, separators=(",", ":")),
                published_at,
                published_at + 30 * MILLIS_PER_DAY,
            ),
        )


def get_active_global_model(database_path: str) -> dict[str, object]:
    """读取当前激活的全局模型。

    参数：
        database_path: SQLite 数据库文件路径。

    返回：
        dict[str, object]: 可直接返回给客户端的模型摘要和模型载荷。
    """

    ensure_bootstrap_global_model(database_path)
    with transaction(database_path) as db:
        row = db.execute(
            """
            SELECT model_version, model_scope, model_type, payload_json,
                   trained_sample_count, published_at, valid_until, min_app_version
            FROM model_versions
            WHERE model_scope = ? AND is_active = 1
            ORDER BY published_at DESC, id DESC
            LIMIT 1
            """,
            (GLOBAL_MODEL_SCOPE,),
        ).fetchone()

    if row is None:
        raise RuntimeError("全局模型初始化失败")

    payload = json.loads(str(row["payload_json"]))
    return {
        "modelVersion": row["model_version"],
        "modelScope": row["model_scope"],
        "modelType": row["model_type"],
        "validUntil": row["valid_until"],
        "minAppVersion": row["min_app_version"],
        "trainedSampleCount": row["trained_sample_count"],
        "publishedAt": row["published_at"],
        "weights": payload.get("weights", {}),
        "thresholds": payload.get("thresholds", {}),
        "signature": payload.get("signature"),
        "featureColumns": payload.get("featureColumns", []),
        "metrics": payload.get("metrics", {}),
        "trainingJobId": payload.get("trainingJobId"),
    }


def publish_global_model(
    database_path: str,
    weights: dict[str, float],
    thresholds: dict[str, float],
    trained_sample_count: int,
    model_version: str | None = None,
    min_app_version: str = "1.0.0",
    feature_columns: list[str] | None = None,
    metrics: dict[str, object] | None = None,
    training_job_id: str | None = None,
) -> dict[str, object]:
    """发布新的全局模型版本。

    参数：
        database_path: SQLite 数据库文件路径。
        weights: 训练得到的线性模型权重。
        thresholds: 高、中置信度阈值。
        trained_sample_count: 本次训练实际使用的全局样本数量。
        model_version: 外部指定模型版本；为空时自动生成。
        min_app_version: 允许使用该模型的最低客户端版本。
        feature_columns: 训练时使用的特征列顺序，便于客户端和后台排查。
        metrics: 训练评估指标。
        training_job_id: 产出该模型的训练任务 ID。

    返回：
        dict[str, object]: 发布后的当前全局模型。
    """

    init_database(database_path)
    published_at = now_millis()
    version = model_version or f"global-linear-confidence-{published_at}-{uuid4().hex[:8]}"
    payload = {
        "weights": weights,
        "thresholds": thresholds,
        "signature": "unsigned-dev-global-model",
        "featureColumns": feature_columns or [],
        "metrics": metrics or {},
        "trainingJobId": training_job_id,
    }

    with transaction(database_path) as db:
        # 同一时间只允许一个全局模型对客户端生效，灰度发布后续再单独加表。
        db.execute(
            "UPDATE model_versions SET is_active = 0 WHERE model_scope = ?",
            (GLOBAL_MODEL_SCOPE,),
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
                GLOBAL_MODEL_SCOPE,
                GLOBAL_MODEL_TYPE,
                json.dumps(payload, ensure_ascii=False, separators=(",", ":")),
                max(0, trained_sample_count),
                published_at,
                published_at + 30 * MILLIS_PER_DAY,
                min_app_version,
            ),
        )

    return get_active_global_model(database_path)


def count_global_training_samples(database_path: str) -> int:
    """统计全局样本池大小。

    参数：
        database_path: SQLite 数据库文件路径。

    返回：
        int: 已接收的全局训练样本数量。
    """

    init_database(database_path)
    with transaction(database_path) as db:
        row = db.execute(
            """
            SELECT COUNT(*) AS sample_count
            FROM training_samples
            WHERE contribution_scope = ?
            """,
            (GLOBAL_MODEL_SCOPE,),
        ).fetchone()
    return int(row["sample_count"] if row is not None else 0)
