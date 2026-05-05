"""会员管理共享服务。

CLI、Web 管理后台和后续支付回调都应复用这里的会员状态写入逻辑。
"""

import re
from uuid import uuid4

from .config import load_config
from .database import init_database, transaction
from .response import now_millis

PHONE_PATTERN = re.compile(r"^1\d{10}$")
EMAIL_PATTERN = re.compile(r"^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}$", re.IGNORECASE)
MILLIS_PER_DAY = 24 * 60 * 60 * 1000
CONFIG = load_config()


def auto_ledger_free_trial_limit() -> int:
    """读取免费自动记账体验额度。

    返回：
        int: 管理后台展示会员状态时使用的服务器配置额度，避免后台和 API 出现两个不同的硬编码值。
    """

    return CONFIG.auto_ledger_free_trial_limit


def normalize_phone(raw_phone: str) -> str:
    """归一化后台输入的账号。

    参数：
        raw_phone: 原始账号文本，支持邮箱或 11 位手机号；参数名沿用旧 CLI 契约。

    返回：
        str: 归一化后的邮箱或 11 位中国大陆手机号。
    """

    account = raw_phone.strip().lower()
    if "@" in account:
        if not EMAIL_PATTERN.fullmatch(account):
            raise ValueError("邮箱地址格式不正确")
        return account
    phone = "".join(char for char in account if char.isdigit())
    if PHONE_PATTERN.fullmatch(phone):
        return phone
    raise ValueError("账号必须是邮箱或 11 位中国大陆手机号")


def ensure_user(database_path: str, phone: str) -> str:
    """确保后台输入账号对应的用户存在。

    参数：
        database_path: SQLite 数据库文件路径。
        phone: 已归一化的账号，当前通常是邮箱；字段名沿用数据库旧列名。

    返回：
        str: 服务器用户 ID。
    """

    init_database(database_path)
    with transaction(database_path) as db:
        row = db.execute("SELECT id FROM users WHERE phone = ?", (phone,)).fetchone()
        if row is not None:
            return str(row["id"])

        user_id = f"user_{uuid4().hex}"
        current_time = now_millis()
        db.execute(
            "INSERT INTO users(id, phone, created_at) VALUES (?, ?, ?)",
            (user_id, phone, current_time),
        )
        # 新建账号默认免费，必须由支付或后台明确开通会员。
        db.execute(
            """
            INSERT INTO memberships(user_id, tier, expires_at, model_confidence_enabled, cloud_learning_enabled, updated_at)
            VALUES (?, 'FREE', NULL, 0, 0, ?)
            """,
            (user_id, current_time),
        )
        return user_id


def grant_member(
    database_path: str,
    phone: str,
    days: int,
    model_confidence_enabled: bool = True,
    cloud_learning_enabled: bool = True,
) -> dict[str, object]:
    """开通会员。

    参数：
        database_path: SQLite 数据库文件路径。
        phone: 邮箱或旧手机号账号；字段名沿用后台旧参数。
        days: 会员有效天数。
        model_confidence_enabled: 是否允许机器学习置信度。
        cloud_learning_enabled: 是否允许云端学习。

    返回：
        dict[str, object]: 更新后的会员状态摘要。
    """

    normalized_phone = normalize_phone(phone)
    user_id = ensure_user(database_path, normalized_phone)
    current_time = now_millis()
    expires_at = current_time + max(days, 1) * MILLIS_PER_DAY

    with transaction(database_path) as db:
        # 所有会员能力都由服务器数据库写入，客户端本地不能自行开启。
        db.execute(
            """
            INSERT INTO memberships(user_id, tier, expires_at, model_confidence_enabled, cloud_learning_enabled, updated_at)
            VALUES (?, 'MEMBER', ?, ?, ?, ?)
            ON CONFLICT(user_id) DO UPDATE SET
                tier = excluded.tier,
                expires_at = excluded.expires_at,
                model_confidence_enabled = excluded.model_confidence_enabled,
                cloud_learning_enabled = excluded.cloud_learning_enabled,
                updated_at = excluded.updated_at
            """,
            (
                user_id,
                expires_at,
                1 if model_confidence_enabled else 0,
                1 if cloud_learning_enabled else 0,
                current_time,
            ),
        )

    return get_member_status_by_phone(database_path, normalized_phone)


def revoke_member(database_path: str, phone: str) -> dict[str, object]:
    """撤销会员。

    参数：
        database_path: SQLite 数据库文件路径。
        phone: 邮箱或旧手机号账号；字段名沿用后台旧参数。

    返回：
        dict[str, object]: 更新后的会员状态摘要。
    """

    normalized_phone = normalize_phone(phone)
    user_id = ensure_user(database_path, normalized_phone)
    current_time = now_millis()

    with transaction(database_path) as db:
        # 撤销时同时关闭模型和云学习，避免旧缓存误导联调结论。
        db.execute(
            """
            INSERT INTO memberships(user_id, tier, expires_at, model_confidence_enabled, cloud_learning_enabled, updated_at)
            VALUES (?, 'FREE', NULL, 0, 0, ?)
            ON CONFLICT(user_id) DO UPDATE SET
                tier = excluded.tier,
                expires_at = excluded.expires_at,
                model_confidence_enabled = excluded.model_confidence_enabled,
                cloud_learning_enabled = excluded.cloud_learning_enabled,
                updated_at = excluded.updated_at
            """,
            (user_id, current_time),
        )

    return get_member_status_by_phone(database_path, normalized_phone)


def get_member_status_by_phone(database_path: str, phone: str) -> dict[str, object]:
    """按账号读取会员状态。

    参数：
        database_path: SQLite 数据库文件路径。
        phone: 邮箱或旧手机号账号；字段名沿用后台旧参数。

    返回：
        dict[str, object]: 当前会员状态摘要。
    """

    normalized_phone = normalize_phone(phone)
    init_database(database_path)
    with transaction(database_path) as db:
        row = db.execute(
            """
            SELECT u.id AS user_id, u.phone, u.created_at, m.tier, m.expires_at,
                   m.model_confidence_enabled, m.cloud_learning_enabled,
                   m.auto_ledger_trial_used, m.updated_at
            FROM users u
            LEFT JOIN memberships m ON m.user_id = u.id
            WHERE u.phone = ?
            """,
            (normalized_phone,),
        ).fetchone()

    if row is None:
        trial_limit = auto_ledger_free_trial_limit()
        return {
            "phone": normalized_phone,
            "exists": False,
            "tier": "FREE",
            "expiresAt": None,
            "modelConfidenceEnabled": False,
            "cloudLearningEnabled": False,
            "autoLedgerTrialLimit": trial_limit,
            "autoLedgerTrialUsed": 0,
            "autoLedgerTrialRemaining": trial_limit,
            "activeMember": False,
        }

    return row_to_member_status(row)


def list_member_statuses(
    database_path: str,
    query: str = "",
    limit: int = 50,
) -> list[dict[str, object]]:
    """列出用户会员状态。

    参数：
        database_path: SQLite 数据库文件路径。
        query: 账号搜索关键字，可输入邮箱或旧手机号片段。
        limit: 最大返回数量。

    返回：
        list[dict[str, object]]: 用户会员状态列表。
    """

    init_database(database_path)
    normalized_query = query.strip().lower()
    like_query = f"%{normalized_query}%"
    with transaction(database_path) as db:
        rows = db.execute(
            """
            SELECT u.id AS user_id, u.phone, u.created_at, m.tier, m.expires_at,
                   m.model_confidence_enabled, m.cloud_learning_enabled,
                   m.auto_ledger_trial_used, m.updated_at
            FROM users u
            LEFT JOIN memberships m ON m.user_id = u.id
            WHERE (? = '' OR u.phone LIKE ?)
            ORDER BY COALESCE(m.updated_at, u.created_at) DESC
            LIMIT ?
            """,
            (normalized_query, like_query, max(1, min(limit, 200))),
        ).fetchall()
    return [row_to_member_status(row) for row in rows]


def row_to_member_status(row: object) -> dict[str, object]:
    """把数据库行转换为会员状态字典。

    参数：
        row: sqlite3.Row 查询结果。

    返回：
        dict[str, object]: 管理后台和 CLI 共用的状态结构。
    """

    tier = row["tier"] or "FREE"
    expires_at = row["expires_at"]
    trial_limit = auto_ledger_free_trial_limit()
    # 按当前服务器额度夹取历史已用次数，避免调低免费额度后后台显示负数剩余额度。
    trial_used = max(0, min(int(row["auto_ledger_trial_used"] or 0), trial_limit))
    active_member = tier == "MEMBER" and expires_at is not None and int(expires_at) > now_millis()
    return {
        "phone": row["phone"],
        "exists": True,
        "userId": row["user_id"],
        "tier": "MEMBER" if active_member else "FREE",
        "rawTier": tier,
        "expiresAt": expires_at if active_member else None,
        "modelConfidenceEnabled": bool(row["model_confidence_enabled"]) if active_member else False,
        "cloudLearningEnabled": bool(row["cloud_learning_enabled"]) if active_member else False,
        "autoLedgerTrialLimit": trial_limit,
        "autoLedgerTrialUsed": trial_used,
        "autoLedgerTrialRemaining": max(0, trial_limit - trial_used),
        "activeMember": active_member,
        "createdAt": row["created_at"],
        "updatedAt": row["updated_at"],
    }
