"""训练样本后台查看服务。

该模块只负责从 SQLite 读取训练样本并整理成管理后台可展示的数据结构。
"""

import json
import sqlite3

from .admin_service import ensure_user, normalize_phone
from .database import init_database, transaction
from .global_model_service import GLOBAL_MODEL_SCOPE
from .response import now_millis
from .training_labels import TRAINABLE_FEEDBACK_LABELS

TRAINING_SAMPLE_LABELS = [
    "",
    "AUTO_LEDGER_UNREVIEWED",
    "PENDING_AWAITING_USER",
    "FILTERED_UNREVIEWED",
    "DUPLICATE_AWAITING_USER",
    "RAW_NOTIFICATION_UNREVIEWED",
    "USER_CONFIRMED_POSITIVE",
    "USER_CONFIRMED_EDITED",
    "USER_DISMISSED_NEGATIVE",
    "USER_MARKED_FALSE_POSITIVE",
    "USER_EDITED_LEDGER",
    "USER_DELETED_LEDGER",
]

DEFAULT_ADMIN_SAMPLE_SOURCE = "admin_web"
DEFAULT_ADMIN_BATCH_ID = "admin_manual"


def create_manual_training_sample(
    database_path: str,
    phone: str,
    raw_title: str,
    raw_text: str,
    source_app: str,
    package_name: str,
    feedback_label: str,
    amount: str,
    direction: str,
    category: str,
    note: str,
    raw_payload_json: str = "",
) -> dict[str, object]:
    """创建后台人工样本。

    参数：
        database_path: SQLite 数据库文件路径。
        phone: 样本归属账号，当前通常是登录邮箱；字段名沿用旧库约定。
        raw_title: 通知标题。
        raw_text: 通知原文。
        source_app: 来源 App 枚举或展示名。
        package_name: 来源包名。
        feedback_label: 人工指定的样本标签。
        amount: 人工填写的金额；为空时按 0 处理。
        direction: 人工填写的收支方向。
        category: 人工填写的分类。
        note: 人工备注。
        raw_payload_json: 高级模式下直接粘贴的完整样本 JSON；非空时会优先使用它。

    返回：
        dict[str, object]: 新增后的样本摘要。
    """

    normalized_phone = normalize_phone(phone)
    normalized_label = _normalize_training_label(feedback_label)
    current_time = now_millis()
    payload = _build_admin_sample_payload(
        raw_payload_json=raw_payload_json,
        raw_title=raw_title,
        raw_text=raw_text,
        source_app=source_app,
        package_name=package_name,
        feedback_label=normalized_label,
        amount=amount,
        direction=direction,
        category=category,
        note=note,
        current_time=current_time,
    )
    notification_id = _coerce_optional_int(payload.get("notificationId")) or -current_time
    sample_created_at = _coerce_optional_int(payload.get("createdAt")) or current_time
    sample_local_id = _coerce_optional_int(payload.get("sampleLocalId"))
    payload["notificationId"] = notification_id
    payload["createdAt"] = sample_created_at
    if sample_local_id is not None:
        payload["sampleLocalId"] = sample_local_id

    raw_json = json.dumps(payload, ensure_ascii=False, separators=(",", ":"), sort_keys=True)
    user_id = ensure_user(database_path, normalized_phone)
    init_database(database_path)
    try:
        with transaction(database_path) as db:
            # 人工样本直接进入全局训练池，下一次全局训练会统一纳入。
            cursor = db.execute(
                """
                INSERT INTO training_samples(
                    user_id,
                    contribution_scope,
                    client_batch_id,
                    device_id,
                    app_version,
                    sample_local_id,
                    notification_id,
                    sample_created_at,
                    raw_json,
                    inserted_at,
                    updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    user_id,
                    GLOBAL_MODEL_SCOPE,
                    DEFAULT_ADMIN_BATCH_ID,
                    DEFAULT_ADMIN_SAMPLE_SOURCE,
                    "admin",
                    sample_local_id,
                    notification_id,
                    sample_created_at,
                    raw_json,
                    current_time,
                    current_time,
                ),
            )
            sample_id = cursor.lastrowid
    except sqlite3.IntegrityError as exc:
        raise ValueError("样本已存在：同一账号下通知 ID 和创建时间不能重复") from exc

    sample = get_training_sample(database_path, int(sample_id))
    if sample is None:
        raise ValueError("样本创建后读取失败")
    return sample


def delete_training_sample(database_path: str, sample_id: str) -> dict[str, object]:
    """删除指定训练样本。

    参数：
        database_path: SQLite 数据库文件路径。
        sample_id: 后台表格展示的样本 ID。

    返回：
        dict[str, object]: 被删除的样本摘要。
    """

    normalized_sample_id = _coerce_optional_int(sample_id)
    if normalized_sample_id is None:
        raise ValueError("样本 ID 不正确")

    init_database(database_path)
    with transaction(database_path) as db:
        row = db.execute(
            """
            SELECT ts.id, ts.user_id, u.phone, ts.contribution_scope, ts.client_batch_id,
                   ts.device_id, ts.app_version, ts.sample_local_id, ts.notification_id,
                   ts.sample_created_at, ts.raw_json, ts.inserted_at, ts.updated_at
            FROM training_samples ts
            JOIN users u ON u.id = ts.user_id
            WHERE ts.id = ?
            """,
            (normalized_sample_id,),
        ).fetchone()
        if row is None:
            raise ValueError("样本不存在")
        sample = row_to_training_sample(row)
        # 训练任务只读取 training_samples，删除后该样本不会再参与后续训练。
        db.execute("DELETE FROM training_samples WHERE id = ?", (normalized_sample_id,))
    return sample


def get_training_sample(database_path: str, sample_id: int) -> dict[str, object] | None:
    """按 ID 读取单条训练样本。

    参数：
        database_path: SQLite 数据库文件路径。
        sample_id: 样本 ID。

    返回：
        dict[str, object] | None: 样本不存在时返回 None。
    """

    init_database(database_path)
    with transaction(database_path) as db:
        row = db.execute(
            """
            SELECT ts.id, ts.user_id, u.phone, ts.contribution_scope, ts.client_batch_id,
                   ts.device_id, ts.app_version, ts.sample_local_id, ts.notification_id,
                   ts.sample_created_at, ts.raw_json, ts.inserted_at, ts.updated_at
            FROM training_samples ts
            JOIN users u ON u.id = ts.user_id
            WHERE ts.id = ?
            """,
            (sample_id,),
        ).fetchone()
    return row_to_training_sample(row) if row is not None else None


def list_training_samples(
    database_path: str,
    query: str = "",
    feedback_label: str = "",
    limit: int = 100,
    offset: int = 0,
    account_query: str = "",
) -> list[dict[str, object]]:
    """列出训练样本明细。

    参数：
        database_path: SQLite 数据库文件路径。
        account_query: 账号筛选关键字，支持邮箱或旧手机号片段。
        query: 搜索关键字，支持样本 ID、通知 ID、原文片段；为兼容旧链接也保留邮箱匹配。
        feedback_label: 用户反馈标签筛选，空字符串表示全部。
        limit: 最大返回数量。
        offset: 跳过的样本数量，用于后台分页。

    返回：
        list[dict[str, object]]: 可直接交给管理后台渲染的样本行。
    """

    init_database(database_path)
    normalized_account = account_query.strip().lower()
    account_like_query = f"%{normalized_account}%"
    normalized_query = query.strip().lower()
    like_query = f"%{normalized_query}%"
    numeric_query = normalized_query if normalized_query.isdigit() else "__NO_NUMERIC_MATCH__"
    normalized_label = feedback_label.strip().upper()
    label_query = f'%"feedbackLabel":"{normalized_label}"%' if normalized_label else "%"

    with transaction(database_path) as db:
        rows = db.execute(
            """
            SELECT ts.id, ts.user_id, u.phone, ts.contribution_scope, ts.client_batch_id,
                   ts.device_id, ts.app_version, ts.sample_local_id, ts.notification_id,
                   ts.sample_created_at, ts.raw_json, ts.inserted_at, ts.updated_at
            FROM training_samples ts
            JOIN users u ON u.id = ts.user_id
            WHERE (? = '' OR lower(u.phone) LIKE ?)
              AND (? = ''
                   OR lower(u.phone) LIKE ?
                   OR CAST(ts.id AS TEXT) = ?
                   OR CAST(ts.sample_local_id AS TEXT) = ?
                   OR CAST(ts.notification_id AS TEXT) = ?
                   OR lower(ts.raw_json) LIKE ?)
              AND (? = '' OR ts.raw_json LIKE ?)
            ORDER BY ts.inserted_at DESC, ts.id DESC
            LIMIT ? OFFSET ?
            """,
            (
                normalized_account,
                account_like_query,
                normalized_query,
                like_query,
                numeric_query,
                numeric_query,
                numeric_query,
                like_query,
                normalized_label,
                label_query,
                _clamp_sample_page_size(limit),
                max(0, offset),
            ),
        ).fetchall()
    return [row_to_training_sample(row) for row in rows]


def count_training_samples(
    database_path: str,
    query: str = "",
    feedback_label: str = "",
    account_query: str = "",
) -> int:
    """统计符合筛选条件的训练样本数量。

    参数：
        database_path: SQLite 数据库文件路径。
        account_query: 账号筛选关键字，支持邮箱或旧手机号片段。
        query: 搜索关键字，支持样本 ID、通知 ID、原文片段；为兼容旧链接也保留邮箱匹配。
        feedback_label: 用户反馈标签筛选，空字符串表示全部。

    返回：
        int: 当前筛选条件下的训练样本总数。
    """

    init_database(database_path)
    normalized_account = account_query.strip().lower()
    account_like_query = f"%{normalized_account}%"
    normalized_query = query.strip().lower()
    like_query = f"%{normalized_query}%"
    numeric_query = normalized_query if normalized_query.isdigit() else "__NO_NUMERIC_MATCH__"
    normalized_label = feedback_label.strip().upper()
    label_query = f'%"feedbackLabel":"{normalized_label}"%' if normalized_label else "%"

    with transaction(database_path) as db:
        row = db.execute(
            """
            SELECT COUNT(*) AS total
            FROM training_samples ts
            JOIN users u ON u.id = ts.user_id
            WHERE (? = '' OR lower(u.phone) LIKE ?)
              AND (? = ''
                   OR lower(u.phone) LIKE ?
                   OR CAST(ts.id AS TEXT) = ?
                   OR CAST(ts.sample_local_id AS TEXT) = ?
                   OR CAST(ts.notification_id AS TEXT) = ?
                   OR lower(ts.raw_json) LIKE ?)
              AND (? = '' OR ts.raw_json LIKE ?)
            """,
            (
                normalized_account,
                account_like_query,
                normalized_query,
                like_query,
                numeric_query,
                numeric_query,
                numeric_query,
                like_query,
                normalized_label,
                label_query,
            ),
        ).fetchone()
    return int(row["total"] if row is not None else 0)


def list_training_sample_page(
    database_path: str,
    query: str = "",
    feedback_label: str = "",
    page: int = 1,
    page_size: int = 20,
    account_query: str = "",
) -> dict[str, object]:
    """分页列出训练样本明细。

    参数：
        database_path: SQLite 数据库文件路径。
        account_query: 账号筛选关键字，支持邮箱或旧手机号片段。
        query: 搜索关键字，支持样本 ID、通知 ID、原文片段；为兼容旧链接也保留邮箱匹配。
        feedback_label: 用户反馈标签筛选，空字符串表示全部。
        page: 当前页码，从 1 开始。
        page_size: 每页样本数量。

    返回：
        dict[str, object]: 包含样本列表、总数、页码和每页数量的分页结果。
    """

    normalized_page = max(1, page)
    normalized_page_size = _clamp_sample_page_size(page_size)
    total = count_training_samples(
        database_path,
        account_query=account_query,
        query=query,
        feedback_label=feedback_label,
    )
    total_pages = max(1, (total + normalized_page_size - 1) // normalized_page_size)
    normalized_page = min(normalized_page, total_pages)
    # 样本原文较大，分页读取能显著降低后台页面响应体积。
    offset = (normalized_page - 1) * normalized_page_size
    items = list_training_samples(
        database_path,
        account_query=account_query,
        query=query,
        feedback_label=feedback_label,
        limit=normalized_page_size,
        offset=offset,
    )
    return {
        "items": items,
        "total": total,
        "page": normalized_page,
        "pageSize": normalized_page_size,
    }


def _clamp_sample_page_size(page_size: int) -> int:
    """限制样本后台每页数量。

    参数：
        page_size: 调用方请求的每页数量。

    返回：
        int: 限制在 1 到 300 之间的安全分页大小。
    """

    try:
        normalized_page_size = int(page_size)
    except (TypeError, ValueError):
        normalized_page_size = 20
    return max(1, min(normalized_page_size, 300))


def row_to_training_sample(row: sqlite3.Row) -> dict[str, object]:
    """把训练样本数据库行转换为后台展示结构。

    参数：
        row: SQLite 查询结果行。

    返回：
        dict[str, object]: 摘要字段、纠错字段和完整 JSON。
    """

    raw_json = str(row["raw_json"] or "{}")
    payload = _parse_sample_json(raw_json)
    parsed = payload.get("parsed") if isinstance(payload.get("parsed"), dict) else {}
    corrected = payload.get("corrected") if isinstance(payload.get("corrected"), dict) else None
    changed_fields = payload.get("changedFields")
    return {
        "id": row["id"],
        "phone": row["phone"],
        "contributionScope": row["contribution_scope"],
        "clientBatchId": row["client_batch_id"],
        "deviceId": row["device_id"],
        "appVersion": row["app_version"],
        "sampleLocalId": row["sample_local_id"],
        "notificationId": row["notification_id"],
        "sampleCreatedAt": row["sample_created_at"],
        "insertedAt": row["inserted_at"],
        "updatedAt": row["updated_at"],
        "rawTitle": payload.get("rawTitle"),
        "rawText": payload.get("rawText"),
        "packageName": payload.get("packageName"),
        "sourceApp": payload.get("sourceApp"),
        "modelVersion": payload.get("modelVersion"),
        "parsePath": payload.get("parsePath"),
        "feedbackLabel": payload.get("feedbackLabel"),
        "feedbackSource": payload.get("feedbackSource"),
        "feedbackAt": payload.get("feedbackAt"),
        "feedbackNote": payload.get("feedbackNote"),
        "confidenceLevel": payload.get("confidenceLevel"),
        "confidenceScore": payload.get("confidenceScore"),
        "routeDestination": payload.get("routeDestination"),
        "processStatus": payload.get("processStatus"),
        "parsed": parsed,
        "corrected": corrected,
        "changedFields": changed_fields if isinstance(changed_fields, list) else [],
        "rawJsonPretty": _pretty_json(payload, raw_json),
    }


def _parse_sample_json(raw_json: str) -> dict[str, object]:
    """解析样本 JSON。

    参数：
        raw_json: 数据库中保存的原始 JSON 文本。

    返回：
        dict[str, object]: 解析成功的样本字典；失败时返回空字典。
    """

    try:
        parsed = json.loads(raw_json)
    except json.JSONDecodeError:
        return {}
    return parsed if isinstance(parsed, dict) else {}


def _pretty_json(payload: dict[str, object], raw_json: str) -> str:
    """生成后台展示用的格式化 JSON。

    参数：
        payload: 已解析的 JSON 字典。
        raw_json: 解析失败时回退展示的原始文本。

    返回：
        str: 缩进后的 JSON 字符串。
    """

    if not payload:
        return raw_json
    return json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True)


def _build_admin_sample_payload(
    raw_payload_json: str,
    raw_title: str,
    raw_text: str,
    source_app: str,
    package_name: str,
    feedback_label: str,
    amount: str,
    direction: str,
    category: str,
    note: str,
    current_time: int,
) -> dict[str, object]:
    """构造后台人工样本 JSON。

    参数：
        raw_payload_json: 管理员粘贴的完整样本 JSON。
        raw_title: 通知标题。
        raw_text: 通知原文。
        source_app: 来源 App。
        package_name: 来源包名。
        feedback_label: 已校验的反馈标签。
        amount: 金额文本。
        direction: 收支方向文本。
        category: 分类文本。
        note: 后台备注。
        current_time: 当前服务器毫秒时间戳。

    返回：
        dict[str, object]: 可直接写入 raw_json 的样本对象。
    """

    if raw_payload_json.strip():
        payload = _parse_manual_json(raw_payload_json)
        # 粘贴完整 JSON 时仍允许表单字段补齐缺失的关键训练字段。
        if not payload.get("feedbackLabel"):
            payload["feedbackLabel"] = feedback_label
        if not payload.get("feedbackSource"):
            payload["feedbackSource"] = DEFAULT_ADMIN_SAMPLE_SOURCE
        if not payload.get("feedbackAt"):
            payload["feedbackAt"] = current_time
        if not payload.get("rawTitle"):
            payload["rawTitle"] = raw_title.strip()
        if not payload.get("rawText"):
            payload["rawText"] = raw_text.strip()
        if not payload.get("sourceApp"):
            payload["sourceApp"] = source_app.strip()
        if not payload.get("packageName"):
            payload["packageName"] = package_name.strip()
        return payload

    if not raw_text.strip():
        raise ValueError("通知原文不能为空；如需高级新增，请填写完整 JSON")

    normalized_amount = _coerce_float(amount)
    normalized_direction = direction.strip().upper() or "EXPENSE"
    normalized_category = category.strip() or "其它"
    normalized_note = note.strip()
    parsed_snapshot = {
        "amount": normalized_amount,
        "direction": normalized_direction,
        "channel": source_app.strip() or "OTHER",
        "timestamp": current_time,
        "category": normalized_category,
        "note": normalized_note,
    }
    return {
        "notificationId": -current_time,
        "createdAt": current_time,
        "rawTitle": raw_title.strip(),
        "rawText": raw_text.strip(),
        "rawPostedAt": current_time,
        "sourceApp": source_app.strip() or "OTHER",
        "packageName": package_name.strip(),
        "parsePath": "ADMIN_MANUAL",
        "amount": normalized_amount,
        "direction": normalized_direction,
        "channel": source_app.strip() or "OTHER",
        "candidateTimestamp": current_time,
        "candidateCategory": normalized_category,
        "candidateNote": normalized_note,
        "confidenceLevel": "ADMIN",
        "confidenceScore": 1.0,
        "confidenceReason": "后台人工新增样本",
        "features": {},
        "routeDestination": "PENDING",
        "processStatus": "ADMIN_MANUAL",
        "feedbackLabel": feedback_label,
        "feedbackSource": DEFAULT_ADMIN_SAMPLE_SOURCE,
        "feedbackAt": current_time,
        "editedManually": True,
        "feedbackNote": normalized_note,
        "parsed": parsed_snapshot,
        "corrected": parsed_snapshot if feedback_label in {"USER_CONFIRMED_EDITED", "USER_EDITED_LEDGER"} else None,
        "changedFields": [],
    }


def _parse_manual_json(raw_payload_json: str) -> dict[str, object]:
    """解析后台手工粘贴的样本 JSON。

    参数：
        raw_payload_json: 管理员输入的 JSON 文本。

    返回：
        dict[str, object]: JSON 对象。
    """

    try:
        payload = json.loads(raw_payload_json)
    except json.JSONDecodeError as exc:
        raise ValueError(f"完整 JSON 格式不正确：{exc.msg}") from exc
    if not isinstance(payload, dict):
        raise ValueError("完整 JSON 必须是对象")
    return payload


def _normalize_training_label(feedback_label: str) -> str:
    """校验并归一化训练标签。

    参数：
        feedback_label: 表单提交的反馈标签。

    返回：
        str: 可写入样本 JSON 的标签。
    """

    normalized_label = feedback_label.strip().upper()
    if normalized_label not in TRAINING_SAMPLE_LABELS or not normalized_label:
        raise ValueError("请选择有效的样本标签")
    if normalized_label not in TRAINABLE_FEEDBACK_LABELS:
        raise ValueError("后台新增训练样本只能选择用户明确反馈后的 6 类有效标签")
    return normalized_label


def _coerce_optional_int(value: object) -> int | None:
    """把可选值转换为正整数或负整数。

    参数：
        value: 表单或 JSON 中的数字。

    返回：
        int | None: 无法转换时返回 None。
    """

    if value is None or value == "":
        return None
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def _coerce_float(value: object) -> float:
    """把金额文本转换为浮点数。

    参数：
        value: 表单金额。

    返回：
        float: 金额数值；为空时返回 0。
    """

    if value is None or str(value).strip() == "":
        return 0.0
    try:
        return float(str(value).strip())
    except ValueError as exc:
        raise ValueError("金额必须是数字") from exc
