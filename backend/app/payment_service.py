"""会员支付订单服务。

该模块集中处理订单金额、过期状态和人工确认收款后的会员开通逻辑。
"""

import sqlite3
from datetime import date, datetime, time, timedelta, timezone

from .database import init_database, transaction
from .response import now_millis

MILLIS_PER_MINUTE = 60 * 1000
MILLIS_PER_DAY = 24 * 60 * 60 * 1000
PAYMENT_PENDING_STATES = {"CREATED", "PAYING"}
PAYMENT_FINAL_STATES = {"PAID", "CANCELLED", "EXPIRED", "FAILED"}
PAYMENT_STATE_OPTIONS = {"", *PAYMENT_PENDING_STATES, *PAYMENT_FINAL_STATES}

PLAN_AMOUNT_CENTS = {
    "TWO_YEAR": 11900,
    "YEARLY": 1900,
    "QUARTERLY": 5000,
    "MONTHLY": 1800,
}

PLAN_MEMBER_DAYS = {
    "TWO_YEAR": 730,
    "YEARLY": 365,
    "QUARTERLY": 90,
    "MONTHLY": 30,
}

PLAN_LABELS = {
    "TWO_YEAR": "2年VIP",
    "YEARLY": "年卡会员",
    "QUARTERLY": "季卡会员",
    "MONTHLY": "月卡会员",
}

PROMOTION_TIMEZONE = timezone(timedelta(hours=8), name="Asia/Shanghai")
YEARLY_PROMOTION_START_DATE = date(2026, 5, 4)
YEARLY_PROMOTION_DAYS = 30
YEARLY_PROMOTION_END_DATE = YEARLY_PROMOTION_START_DATE + timedelta(days=YEARLY_PROMOTION_DAYS)


def date_start_millis(day: date) -> int:
    """计算上海时区某天零点的毫秒时间戳。

    参数：
        day: 需要转换的日期。

    返回：
        int: 对应日期在上海时区零点的 Unix 毫秒时间戳。
    """

    point = datetime.combine(day, time.min, tzinfo=PROMOTION_TIMEZONE)
    return int(point.timestamp() * 1000)


def yearly_promotion_window_millis() -> tuple[int, int]:
    """获取 19 元年卡首发活动窗口。

    返回：
        tuple[int, int]: 活动开始和结束毫秒时间戳，结束时间为开区间。
    """

    return (
        date_start_millis(YEARLY_PROMOTION_START_DATE),
        date_start_millis(YEARLY_PROMOTION_END_DATE),
    )


def is_yearly_promotion_active(current_time: int | None = None) -> bool:
    """判断 19 元年卡活动当前是否有效。

    参数：
        current_time: 需要判定的服务器毫秒时间戳；为空时使用当前服务器时间。

    返回：
        bool: 位于活动窗口内时返回 True。
    """

    now = current_time or now_millis()
    starts_at, ends_at = yearly_promotion_window_millis()
    # 服务端按同一活动窗口兜底校验，避免客户端时间被修改后仍能创建低价订单。
    return starts_at <= now < ends_at


def normalize_payment_plan(plan: str) -> str:
    """归一化会员套餐。

    参数：
        plan: 客户端或后台传入的套餐枚举文本。

    返回：
        str: 可用于订单入库的套餐枚举。
    """

    normalized_plan = plan.strip().upper()
    if normalized_plan not in PLAN_AMOUNT_CENTS:
        raise ValueError("会员套餐不存在")
    return normalized_plan


def payment_order_expires_at(created_at: int, expire_minutes: int) -> int:
    """计算订单过期时间。

    参数：
        created_at: 订单创建时间，毫秒时间戳。
        expire_minutes: 订单有效分钟数，小于 1 时按 1 分钟兜底。

    返回：
        int: 订单过期毫秒时间戳。
    """

    return created_at + max(1, expire_minutes) * MILLIS_PER_MINUTE


def expire_stale_payment_orders(db: sqlite3.Connection, current_time: int | None = None) -> int:
    """把已超时的待支付订单标记为过期。

    参数：
        db: 当前 SQLite 连接，调用方负责事务提交。
        current_time: 当前毫秒时间戳；为空时读取服务器时间。

    返回：
        int: 本次被更新为 EXPIRED 的订单数量。
    """

    now = current_time or now_millis()
    cursor = db.execute(
        """
        UPDATE payment_orders
        SET payment_state = 'EXPIRED'
        WHERE payment_state IN ('CREATED', 'PAYING')
          AND expires_at IS NOT NULL
          AND expires_at <= ?
        """,
        (now,),
    )
    return cursor.rowcount


def create_membership_payment_order(
    database_path: str,
    user_id: str,
    plan: str,
    expire_minutes: int,
) -> dict[str, object]:
    """创建会员支付订单。

    参数：
        database_path: SQLite 数据库文件路径。
        user_id: 当前登录用户 ID。
        plan: 会员套餐枚举。
        expire_minutes: 订单有效分钟数。

    返回：
        dict[str, object]: 已创建的订单摘要。
    """

    normalized_plan = normalize_payment_plan(plan)
    created_at = now_millis()
    # 年卡当前是首发低价活动，订单创建必须由服务端按真实时间二次校验。
    if normalized_plan == "YEARLY" and not is_yearly_promotion_active(created_at):
        raise ValueError("19 元年卡限时活动已结束")
    expires_at = payment_order_expires_at(created_at, expire_minutes)
    from uuid import uuid4

    order_id = f"order_{uuid4().hex}"
    init_database(database_path)
    with transaction(database_path) as db:
        db.execute(
            """
            INSERT INTO payment_orders(order_id, user_id, plan, amount_cents, payment_state, paid_at, expires_at, created_at)
            VALUES (?, ?, ?, ?, 'CREATED', NULL, ?, ?)
            """,
            (order_id, user_id, normalized_plan, PLAN_AMOUNT_CENTS[normalized_plan], expires_at, created_at),
        )

    return {
        "orderId": order_id,
        "plan": normalized_plan,
        "amountCents": PLAN_AMOUNT_CENTS[normalized_plan],
        "paymentState": "CREATED",
        "paidAt": None,
        "expiresAt": expires_at,
        "createdAt": created_at,
    }


def query_membership_payment_order(
    database_path: str,
    user_id: str,
    order_id: str,
) -> dict[str, object] | None:
    """查询当前用户的会员订单。

    参数：
        database_path: SQLite 数据库文件路径。
        user_id: 当前登录用户 ID。
        order_id: 服务器订单号。

    返回：
        dict[str, object] | None: 订单状态；订单不存在时返回 None。
    """

    init_database(database_path)
    with transaction(database_path) as db:
        expire_stale_payment_orders(db)
        row = db.execute(
            """
            SELECT order_id, payment_state, paid_at, expires_at
            FROM payment_orders
            WHERE order_id = ? AND user_id = ?
            """,
            (order_id, user_id),
        ).fetchone()

    if row is None:
        return None
    return {
        "orderId": row["order_id"],
        "paymentState": row["payment_state"],
        "paidAt": row["paid_at"],
        "expiresAt": row["expires_at"],
    }


def list_payment_orders(
    database_path: str,
    query: str = "",
    state: str = "",
    limit: int = 100,
    offset: int = 0,
) -> list[dict[str, object]]:
    """列出后台订单。

    参数：
        database_path: SQLite 数据库文件路径。
        query: 邮箱或订单号搜索关键字。
        state: 订单状态筛选，空字符串表示全部。
        limit: 最大返回数量。
        offset: 跳过的订单数量，用于后台分页。

    返回：
        list[dict[str, object]]: 后台订单列表。
    """

    init_database(database_path)
    normalized_query = query.strip().lower()
    like_query = f"%{normalized_query}%"
    normalized_state = state.strip().upper()
    if normalized_state not in PAYMENT_STATE_OPTIONS:
        normalized_state = ""

    with transaction(database_path) as db:
        expire_stale_payment_orders(db)
        rows = db.execute(
            """
            SELECT o.order_id, o.user_id, u.phone, o.plan, o.amount_cents,
                   o.payment_state, o.paid_at, o.expires_at, o.created_at
            FROM payment_orders o
            JOIN users u ON u.id = o.user_id
            WHERE (? = '' OR lower(o.order_id) LIKE ? OR lower(u.phone) LIKE ?)
              AND (? = '' OR o.payment_state = ?)
            ORDER BY o.created_at DESC
            LIMIT ? OFFSET ?
            """,
            (
                normalized_query,
                like_query,
                like_query,
                normalized_state,
                normalized_state,
                _clamp_order_page_size(limit),
                max(0, offset),
            ),
        ).fetchall()

    return [row_to_payment_order(row) for row in rows]


def count_payment_orders(database_path: str, query: str = "", state: str = "") -> int:
    """统计符合筛选条件的订单数量。

    参数：
        database_path: SQLite 数据库文件路径。
        query: 邮箱或订单号搜索关键字。
        state: 订单状态筛选，空字符串表示全部。

    返回：
        int: 当前筛选条件下的订单总数。
    """

    init_database(database_path)
    normalized_query = query.strip().lower()
    like_query = f"%{normalized_query}%"
    normalized_state = state.strip().upper()
    if normalized_state not in PAYMENT_STATE_OPTIONS:
        normalized_state = ""

    with transaction(database_path) as db:
        expire_stale_payment_orders(db)
        row = db.execute(
            """
            SELECT COUNT(*) AS total
            FROM payment_orders o
            JOIN users u ON u.id = o.user_id
            WHERE (? = '' OR lower(o.order_id) LIKE ? OR lower(u.phone) LIKE ?)
              AND (? = '' OR o.payment_state = ?)
            """,
            (
                normalized_query,
                like_query,
                like_query,
                normalized_state,
                normalized_state,
            ),
        ).fetchone()
    return int(row["total"] if row is not None else 0)


def list_payment_order_page(
    database_path: str,
    query: str = "",
    state: str = "",
    page: int = 1,
    page_size: int = 20,
) -> dict[str, object]:
    """分页列出后台订单。

    参数：
        database_path: SQLite 数据库文件路径。
        query: 邮箱或订单号搜索关键字。
        state: 订单状态筛选，空字符串表示全部。
        page: 当前页码，从 1 开始。
        page_size: 每页订单数量。

    返回：
        dict[str, object]: 包含订单列表、总数、页码和每页数量的分页结果。
    """

    normalized_page = max(1, page)
    normalized_page_size = _clamp_order_page_size(page_size)
    total = count_payment_orders(database_path, query=query, state=state)
    total_pages = max(1, (total + normalized_page_size - 1) // normalized_page_size)
    normalized_page = min(normalized_page, total_pages)
    # 后台页面只读取当前页，避免订单增长后一次性加载造成卡顿。
    offset = (normalized_page - 1) * normalized_page_size
    items = list_payment_orders(
        database_path,
        query=query,
        state=state,
        limit=normalized_page_size,
        offset=offset,
    )
    return {
        "items": items,
        "total": total,
        "page": normalized_page,
        "pageSize": normalized_page_size,
    }


def _clamp_order_page_size(page_size: int) -> int:
    """限制订单后台每页数量。

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


def mark_payment_order_paid(database_path: str, order_id: str) -> dict[str, object]:
    """人工确认订单已收款并开通会员。

    参数：
        database_path: SQLite 数据库文件路径。
        order_id: 需要确认收款的服务器订单号。

    返回：
        dict[str, object]: 确认后的订单和会员摘要。
    """

    normalized_order_id = order_id.strip()
    if not normalized_order_id:
        raise ValueError("订单号不能为空")

    init_database(database_path)
    current_time = now_millis()
    with transaction(database_path) as db:
        expire_stale_payment_orders(db, current_time=current_time)
        row = db.execute(
            """
            SELECT o.order_id, o.user_id, u.phone, o.plan, o.amount_cents,
                   o.payment_state, o.paid_at, o.expires_at, o.created_at,
                   m.tier, m.expires_at AS member_expires_at
            FROM payment_orders o
            JOIN users u ON u.id = o.user_id
            LEFT JOIN memberships m ON m.user_id = o.user_id
            WHERE o.order_id = ?
            """,
            (normalized_order_id,),
        ).fetchone()
        if row is None:
            raise ValueError("订单不存在")
        if row["payment_state"] == "PAID":
            raise ValueError("订单已经确认收款")
        if row["payment_state"] not in PAYMENT_PENDING_STATES:
            raise ValueError(f"订单当前状态为 {row['payment_state']}，不能确认收款")

        plan = normalize_payment_plan(str(row["plan"]))
        base_expires_at = row["member_expires_at"]
        # 续费用户从原有效期继续累加，过期或免费用户从当前时间开始计算。
        base_time = current_time
        if row["tier"] == "MEMBER" and base_expires_at is not None and int(base_expires_at) > current_time:
            base_time = int(base_expires_at)
        member_expires_at = base_time + PLAN_MEMBER_DAYS[plan] * MILLIS_PER_DAY

        db.execute(
            """
            UPDATE payment_orders
            SET payment_state = 'PAID', paid_at = ?
            WHERE order_id = ?
            """,
            (current_time, normalized_order_id),
        )
        db.execute(
            """
            INSERT INTO memberships(user_id, tier, expires_at, model_confidence_enabled, cloud_learning_enabled, updated_at)
            VALUES (?, 'MEMBER', ?, 1, 1, ?)
            ON CONFLICT(user_id) DO UPDATE SET
                tier = excluded.tier,
                expires_at = excluded.expires_at,
                model_confidence_enabled = excluded.model_confidence_enabled,
                cloud_learning_enabled = excluded.cloud_learning_enabled,
                updated_at = excluded.updated_at
            """,
            (row["user_id"], member_expires_at, current_time),
        )

    return {
        "orderId": normalized_order_id,
        "phone": row["phone"],
        "plan": plan,
        "planLabel": PLAN_LABELS.get(plan, plan),
        "paymentState": "PAID",
        "paidAt": current_time,
        "memberExpiresAt": member_expires_at,
    }


def row_to_payment_order(row: sqlite3.Row) -> dict[str, object]:
    """把数据库行转换成后台订单结构。

    参数：
        row: SQLite 查询结果行。

    返回：
        dict[str, object]: 订单展示所需字段。
    """

    payment_state = str(row["payment_state"])
    expires_at = row["expires_at"]
    return {
        "orderId": row["order_id"],
        "userId": row["user_id"],
        "phone": row["phone"],
        "plan": row["plan"],
        "planLabel": PLAN_LABELS.get(str(row["plan"]), str(row["plan"])),
        "amountCents": row["amount_cents"],
        "paymentState": payment_state,
        "paidAt": row["paid_at"],
        "expiresAt": expires_at,
        "createdAt": row["created_at"],
        "canConfirmPaid": payment_state in PAYMENT_PENDING_STATES
        and expires_at is not None
        and int(expires_at) > now_millis(),
    }
