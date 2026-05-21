"""用户反馈服务。

该模块只处理用户反馈的写入和后台查询，避免把反馈列表 SQL 分散在 API 路由和管理后台里。
"""

from .database import init_database, transaction


def create_user_feedback(
    database_path: str,
    user_id: str,
    content: str,
    contact: str | None,
    device_id: str | None,
    app_version: str | None,
    created_at: int,
) -> dict[str, object]:
    """写入一条用户反馈。
    参数：
        database_path: SQLite 数据库文件路径。
        user_id: 当前登录用户 ID。
        content: 用户反馈正文。
        contact: 用户额外联系方式。
        device_id: 客户端设备标识。
        app_version: 客户端版本号。
        created_at: 服务器收到反馈时的毫秒时间戳。
    返回：
        dict[str, object]: 新反馈的主键和创建时间。
    """

    normalized_content = content.strip()
    normalized_contact = _blank_to_none(contact)
    normalized_device_id = _blank_to_none(device_id)
    normalized_app_version = _blank_to_none(app_version)
    if len(normalized_content) < 5:
        raise ValueError("反馈内容至少需要 5 个字")

    init_database(database_path)
    with transaction(database_path) as db:
        cursor = db.execute(
            """
            INSERT INTO user_feedback(
                user_id, content, contact, device_id, app_version, status, created_at, updated_at
            )
            VALUES (?, ?, ?, ?, ?, 'OPEN', ?, ?)
            """,
            (
                user_id,
                normalized_content,
                normalized_contact,
                normalized_device_id,
                normalized_app_version,
                created_at,
                created_at,
            ),
        )
        feedback_id = int(cursor.lastrowid)
    return {"feedbackId": feedback_id, "receivedAt": created_at}


def count_user_feedback(
    database_path: str,
    account_query: str = "",
    query: str = "",
    status: str = "",
) -> int:
    """统计用户反馈数量。
    参数：
        database_path: SQLite 数据库文件路径。
        account_query: 登录邮箱搜索关键字。
        query: 反馈正文、联系方式或设备信息搜索关键字。
        status: 反馈处理状态筛选。
    返回：
        int: 符合筛选条件的反馈总数。
    """

    init_database(database_path)
    where_sql, params = _build_feedback_filters(account_query, query, status)
    with transaction(database_path) as db:
        row = db.execute(
            f"""
            SELECT COUNT(*) AS total
            FROM user_feedback f
            JOIN users u ON u.id = f.user_id
            {where_sql}
            """,
            params,
        ).fetchone()
    return int(row["total"] or 0) if row else 0


def list_user_feedback(
    database_path: str,
    account_query: str = "",
    query: str = "",
    status: str = "",
    page: int = 1,
    page_size: int = 20,
) -> list[dict[str, object]]:
    """分页读取用户反馈。
    参数：
        database_path: SQLite 数据库文件路径。
        account_query: 登录邮箱搜索关键字。
        query: 反馈正文、联系方式或设备信息搜索关键字。
        status: 反馈处理状态筛选。
        page: 当前页码，从 1 开始。
        page_size: 每页数量。
    返回：
        list[dict[str, object]]: 后台展示所需的反馈行。
    """

    init_database(database_path)
    normalized_page = max(1, page)
    normalized_page_size = max(1, min(page_size, 100))
    offset = (normalized_page - 1) * normalized_page_size
    where_sql, params = _build_feedback_filters(account_query, query, status)
    with transaction(database_path) as db:
        rows = db.execute(
            f"""
            SELECT f.id, u.phone, f.content, f.contact, f.device_id, f.app_version,
                   f.status, f.created_at, f.updated_at
            FROM user_feedback f
            JOIN users u ON u.id = f.user_id
            {where_sql}
            ORDER BY f.created_at DESC, f.id DESC
            LIMIT ? OFFSET ?
            """,
            (*params, normalized_page_size, offset),
        ).fetchall()
    return [dict(row) for row in rows]


def list_user_feedback_page(
    database_path: str,
    account_query: str = "",
    query: str = "",
    status: str = "",
    page: int = 1,
    page_size: int = 20,
) -> dict[str, object]:
    """分页读取用户反馈并返回分页元数据。
    参数：
        database_path: SQLite 数据库文件路径。
        account_query: 登录邮箱搜索关键字。
        query: 反馈正文、联系方式或设备信息搜索关键字。
        status: 反馈处理状态筛选。
        page: 当前页码，从 1 开始。
        page_size: 每页数量。
    返回：
        dict[str, object]: items、total、page 和 pageSize。
    """

    normalized_page_size = max(1, min(page_size, 100))
    total = count_user_feedback(database_path, account_query, query, status)
    max_page = max(1, (total + normalized_page_size - 1) // normalized_page_size)
    normalized_page = min(max(1, page), max_page)
    return {
        "items": list_user_feedback(
            database_path=database_path,
            account_query=account_query,
            query=query,
            status=status,
            page=normalized_page,
            page_size=normalized_page_size,
        ),
        "total": total,
        "page": normalized_page,
        "pageSize": normalized_page_size,
    }


def _build_feedback_filters(
    account_query: str,
    query: str,
    status: str,
) -> tuple[str, tuple[object, ...]]:
    """构造反馈列表筛选 SQL。
    参数：
        account_query: 登录邮箱搜索关键字。
        query: 反馈正文、联系方式或设备信息搜索关键字。
        status: 反馈处理状态筛选。
    返回：
        tuple[str, tuple[object, ...]]: WHERE SQL 片段和绑定参数。
    """

    clauses: list[str] = []
    params: list[object] = []
    normalized_account = account_query.strip()
    normalized_query = query.strip()
    normalized_status = status.strip().upper()
    if normalized_account:
        clauses.append("u.phone LIKE ?")
        params.append(f"%{normalized_account}%")
    if normalized_query:
        # 反馈正文、联系方式和设备版本字段都可能帮助定位问题，因此统一做模糊搜索。
        clauses.append(
            """
            (
                f.content LIKE ?
                OR IFNULL(f.contact, '') LIKE ?
                OR IFNULL(f.device_id, '') LIKE ?
                OR IFNULL(f.app_version, '') LIKE ?
            )
            """,
        )
        params.extend([f"%{normalized_query}%"] * 4)
    if normalized_status:
        clauses.append("f.status = ?")
        params.append(normalized_status)
    where_sql = f"WHERE {' AND '.join(clauses)}" if clauses else ""
    return where_sql, tuple(params)


def _blank_to_none(value: str | None) -> str | None:
    """把空字符串归一化为空值。
    参数：
        value: 外部传入的可选文本。
    返回：
        str | None: 去掉首尾空白后的文本，空文本返回 None。
    """

    if value is None:
        return None
    normalized = value.strip()
    return normalized or None
