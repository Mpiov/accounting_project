"""服务器本地 Web 管理后台。

第一版后台只绑定服务器本机端口，管理员通过 SSH 隧道访问，不通过公网 Nginx 暴露。
"""

from html import escape
import hmac
from urllib.parse import parse_qs, quote, urlencode

from fastapi import APIRouter, Request
from fastapi.responses import HTMLResponse, RedirectResponse, Response

from .admin_service import grant_member
from .admin_service import list_member_status_page
from .admin_service import revoke_member
from .auth import parse_token, sign_token
from .config import AppConfig
from .config import load_config
from .global_model_service import count_global_training_samples
from .global_model_service import get_active_global_model
from .payment_service import list_payment_order_page
from .payment_service import list_payment_orders
from .payment_service import mark_payment_order_paid
from .response import now_millis
from .training_sample_service import TRAINING_SAMPLE_LABELS
from .training_sample_service import create_manual_training_sample
from .training_sample_service import delete_training_sample
from .training_sample_service import list_training_sample_page
from .training_job import TrainingPolicy
from .training_job import list_training_jobs
from .training_job import run_global_training
from .training_job import training_dataset_summary

router = APIRouter(prefix="/admin", include_in_schema=False)

ADMIN_COOKIE_NAME = "autobook_admin_session"


def render_page(title: str, body: str) -> HTMLResponse:
    """渲染管理后台页面。

    参数：
        title: 页面标题。
        body: 页面主体 HTML。

    返回：
        HTMLResponse: 完整 HTML 页面。
    """

    html = f"""<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>{escape(title)} - AutoBook Admin</title>
  <style>
    :root {{
      color-scheme: light;
      --bg: #f6f7f9;
      --panel: #ffffff;
      --text: #172033;
      --muted: #667085;
      --line: #d7dce5;
      --accent: #0f766e;
      --accent-soft: #eef6f5;
      --warning: #b54708;
      --danger: #b42318;
    }}
    * {{ box-sizing: border-box; }}
    body {{
      margin: 0;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      background: var(--bg);
      color: var(--text);
    }}
    header {{
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 18px 24px;
      background: var(--panel);
      border-bottom: 1px solid var(--line);
    }}
    main {{
      max-width: 1120px;
      margin: 0 auto;
      padding: 24px;
    }}
    h1 {{ margin: 0; font-size: 22px; }}
    nav {{
      display: flex;
      gap: 10px;
      margin-top: 8px;
      font-size: 14px;
    }}
    nav a {{
      color: var(--accent);
      text-decoration: none;
      font-weight: 600;
    }}
    h2 {{ margin: 0 0 14px; font-size: 17px; }}
    section {{
      background: var(--panel);
      border: 1px solid var(--line);
      border-radius: 8px;
      padding: 18px;
      margin-bottom: 16px;
    }}
    form {{
      display: flex;
      flex-wrap: wrap;
      align-items: end;
      gap: 12px;
      margin: 0;
    }}
    label {{
      display: grid;
      gap: 6px;
      color: var(--muted);
      font-size: 13px;
    }}
    input[type="text"], input[type="password"], input[type="number"], input[type="email"], select, textarea {{
      width: min(280px, 100%);
      border: 1px solid var(--line);
      border-radius: 6px;
      padding: 0 10px;
      color: var(--text);
      background: #fff;
      font-size: 14px;
    }}
    input[type="text"], input[type="password"], input[type="number"], input[type="email"], select {{ height: 38px; }}
    textarea {{
      min-height: 92px;
      padding-top: 9px;
      line-height: 1.45;
      resize: vertical;
    }}
    input[type="checkbox"] {{ width: 16px; height: 16px; }}
    button {{
      height: 38px;
      border: 1px solid var(--accent);
      border-radius: 6px;
      padding: 0 14px;
      background: var(--accent);
      color: #fff;
      font-size: 14px;
      cursor: pointer;
    }}
    button.secondary {{
      background: #fff;
      color: var(--accent);
    }}
    button.danger {{
      border-color: var(--danger);
      background: var(--danger);
    }}
    table {{
      width: 100%;
      border-collapse: collapse;
      font-size: 14px;
    }}
    th, td {{
      padding: 10px 8px;
      border-bottom: 1px solid var(--line);
      text-align: left;
      vertical-align: middle;
    }}
    th {{ color: var(--muted); font-weight: 600; }}
    .message {{
      margin-bottom: 16px;
      padding: 10px 12px;
      border: 1px solid var(--line);
      border-radius: 6px;
      background: #eef6f5;
      color: var(--accent);
      font-size: 14px;
    }}
    .muted {{ color: var(--muted); }}
    .inline-form {{ display: inline-flex; gap: 8px; }}
    .wide-form {{ align-items: stretch; }}
    .wide-form label.full {{ width: 100%; }}
    .wide-form label.full input {{ width: 100%; }}
    .wide-form label.full textarea {{ width: 100%; }}
    .badge {{
      display: inline-flex;
      align-items: center;
      height: 24px;
      border-radius: 999px;
      padding: 0 9px;
      background: var(--accent-soft);
      color: var(--accent);
      font-size: 12px;
      font-weight: 700;
    }}
    .pager {{
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      margin-top: 14px;
      color: var(--muted);
      font-size: 13px;
    }}
    .pager-actions {{
      display: flex;
      align-items: center;
      gap: 8px;
    }}
    .pager a {{
      display: inline-flex;
      align-items: center;
      height: 30px;
      border: 1px solid var(--line);
      border-radius: 6px;
      padding: 0 10px;
      color: var(--accent);
      background: #fff;
      text-decoration: none;
      font-weight: 700;
    }}
    .pager .disabled {{
      display: inline-flex;
      align-items: center;
      height: 30px;
      border: 1px solid var(--line);
      border-radius: 6px;
      padding: 0 10px;
      color: #98a2b3;
      background: #f8fafc;
    }}
    .state-created, .state-paying {{ background: #fff7ed; color: var(--warning); }}
    .state-paid {{ background: var(--accent-soft); color: var(--accent); }}
    .state-expired, .state-cancelled, .state-failed {{ background: #fef3f2; color: var(--danger); }}
    .label-positive {{ background: var(--accent-soft); color: var(--accent); }}
    .label-negative {{ background: #fef3f2; color: var(--danger); }}
    .label-pending {{ background: #fff7ed; color: var(--warning); }}
    .amount {{ font-weight: 700; color: var(--text); }}
    .section-title {{
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      margin-bottom: 14px;
    }}
    .section-title h2 {{ margin: 0; }}
    .sample-raw {{
      max-width: 320px;
      white-space: pre-wrap;
      word-break: break-word;
      line-height: 1.45;
    }}
    .sample-json {{
      max-width: 960px;
      max-height: 520px;
      overflow: auto;
      margin: 10px 0 0;
      padding: 12px;
      border: 1px solid var(--line);
      border-radius: 6px;
      background: #f8fafc;
      color: var(--text);
      font-size: 12px;
      line-height: 1.5;
      white-space: pre-wrap;
    }}
    details summary {{
      color: var(--accent);
      cursor: pointer;
      font-weight: 700;
    }}
    @media (max-width: 720px) {{
      header {{ padding: 14px 16px; }}
      main {{ padding: 16px; }}
      table {{ display: block; overflow-x: auto; }}
      input[type="text"], input[type="password"], input[type="number"], input[type="email"], select {{ width: 100%; }}
    }}
  </style>
</head>
<body>
  <header>
    <div>
      <h1>AutoBook Admin</h1>
      <nav>
        <a href="/admin">会员与训练</a>
        <a href="/admin/orders">订单</a>
        <a href="/admin/samples">样本</a>
      </nav>
    </div>
    <form method="post" action="/admin/logout">
      <button class="secondary" type="submit">退出</button>
    </form>
  </header>
  <main>{body}</main>
</body>
</html>"""
    return HTMLResponse(html)


def render_login(message: str = "") -> HTMLResponse:
    """渲染登录页。

    参数：
        message: 登录失败或配置缺失提示。

    返回：
        HTMLResponse: 登录页。
    """

    message_html = f'<div class="message">{escape(message)}</div>' if message else ""
    return render_page(
        title="登录",
        body=f"""
        {message_html}
        <section>
          <h2>登录</h2>
          <form method="post" action="/admin/login">
            <label>
              管理密码
              <input type="password" name="password" autocomplete="current-password" required>
            </label>
            <button type="submit">进入</button>
          </form>
        </section>
        """,
    )


async def read_form(request: Request) -> dict[str, str]:
    """读取 HTML 表单。

    参数：
        request: FastAPI 请求对象。

    返回：
        dict[str, str]: 表单字段字典。
    """

    body = (await request.body()).decode("utf-8")
    parsed = parse_qs(body, keep_blank_values=True)
    # HTML 表单同名字段当前只取第一个值，复选框通过字段是否存在判断。
    return {key: values[0] if values else "" for key, values in parsed.items()}


def read_int_form(form: dict[str, str], name: str, default: int) -> int:
    """读取整数表单字段。

    参数：
        form: 已解析的 HTML 表单字段字典。
        name: 需要读取的字段名。
        default: 字段为空时使用的配置默认值。
    返回：
        int: 表单中填写的整数；空值会回退到配置，非法值交给上层异常提示。
    """

    raw_value = form.get(name, "").strip()
    # 后台表单允许管理员不填写，此时应沿用服务器环境配置，避免悄悄退回硬编码旧值。
    return int(raw_value) if raw_value else default


def build_admin_session() -> str:
    """生成管理后台会话。

    返回：
        str: 已签名的管理后台会话令牌。
    """

    config = load_config()
    issued_at = now_millis()
    expires_at = issued_at + config.admin_session_seconds * 1000
    return sign_token(
        payload={"typ": "admin", "iat": issued_at, "exp": expires_at},
        secret=config.token_secret,
    )


def has_admin_session(request: Request) -> bool:
    """校验管理后台会话。

    参数：
        request: FastAPI 请求对象。

    返回：
        bool: 当前请求是否已登录后台。
    """

    config = load_config()
    token = request.cookies.get(ADMIN_COOKIE_NAME)
    if not token:
        return False
    try:
        payload = parse_token(token, config.token_secret)
    except Exception:
        return False
    return payload.get("typ") == "admin"


def redirect_login() -> RedirectResponse:
    """跳转到登录页。

    返回：
        RedirectResponse: 登录页跳转响应。
    """

    return RedirectResponse(url="/admin/login", status_code=303)


@router.get("/login")
def login_page(request: Request) -> Response:
    """展示登录页。

    参数：
        request: FastAPI 请求对象。

    返回：
        HTMLResponse | RedirectResponse: 登录页或后台首页跳转。
    """

    if has_admin_session(request):
        return RedirectResponse(url="/admin", status_code=303)
    return render_login()


@router.post("/login")
async def login(request: Request) -> Response:
    """处理后台登录。

    参数：
        request: FastAPI 请求对象。

    返回：
        HTMLResponse | RedirectResponse: 登录失败页或后台首页跳转。
    """

    config = load_config()
    form = await read_form(request)
    password = form.get("password", "")
    if not config.admin_password:
        return render_login("后台密码未配置")
    if not hmac.compare_digest(password, config.admin_password):
        return render_login("管理密码不正确")

    response = RedirectResponse(url="/admin", status_code=303)
    response.set_cookie(
        key=ADMIN_COOKIE_NAME,
        value=build_admin_session(),
        httponly=True,
        samesite="lax",
        max_age=config.admin_session_seconds,
    )
    return response


@router.post("/logout")
def logout() -> RedirectResponse:
    """退出后台登录。

    返回：
        RedirectResponse: 登录页跳转。
    """

    response = RedirectResponse(url="/admin/login", status_code=303)
    response.delete_cookie(ADMIN_COOKIE_NAME)
    return response


@router.get("")
def admin_index(
    request: Request,
    q: str = "",
    message: str = "",
    page: int = 1,
    page_size: int = 20,
) -> Response:
    """展示会员管理首页。

    参数：
        request: FastAPI 请求对象。
        q: 邮箱或旧手机号搜索关键字。
        message: 操作结果提示。
        page: 用户列表页码。
        page_size: 用户列表每页数量。

    返回：
        HTMLResponse | RedirectResponse: 后台首页或登录页跳转。
    """

    if not has_admin_session(request):
        return redirect_login()

    config = load_config()
    member_page = list_member_status_page(config.database_path, query=q, page=page, page_size=page_size)
    members = member_page["items"]
    orders_section = render_recent_orders_section(config.database_path)
    global_model_section = render_global_model_section(config.database_path)
    training_section = render_training_section(config)
    message_html = f'<div class="message">{escape(message)}</div>' if message else ""
    rows = "\n".join(render_member_row(member) for member in members)
    pager_html = render_pagination(
        path="/admin",
        current_page=int(member_page["page"]),
        page_size=int(member_page["pageSize"]),
        total=int(member_page["total"]),
        params={"q": q},
    )
    body = f"""
    {message_html}
    {orders_section}
    {global_model_section}
    {training_section}
    <section>
      <h2>搜索</h2>
      <form method="get" action="/admin">
        <label>
          邮箱/账号
          <input type="text" name="q" value="{escape(q)}">
        </label>
        {render_page_size_select(int(member_page["pageSize"]))}
        <input type="hidden" name="page" value="1">
        <button type="submit">查询</button>
      </form>
    </section>
    <section>
      <h2>手动发放会员</h2>
      <p class="muted">主要用于补偿、测试或特殊情况；普通订单请优先在“订单”页确认收款并自动开通。</p>
      <form method="post" action="/admin/memberships/grant">
        <label>
          邮箱
          <input type="email" name="phone" required>
        </label>
        <label>
          天数
          <input type="number" name="days" value="30" min="1" max="3650" required>
        </label>
        <label>
          <span>机器学习</span>
          <input type="checkbox" name="model_confidence_enabled" checked>
        </label>
        <label>
          <span>云端学习</span>
          <input type="checkbox" name="cloud_learning_enabled" checked>
        </label>
        <button type="submit">发放</button>
      </form>
    </section>
    <section>
      <h2>用户</h2>
      <table>
        <thead>
          <tr>
            <th>邮箱/账号</th>
            <th>等级</th>
            <th>到期</th>
            <th>自动记账体验</th>
            <th>模型</th>
            <th>云学习</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>{rows or '<tr><td colspan="7" class="muted">暂无用户</td></tr>'}</tbody>
      </table>
      {pager_html}
    </section>
    """
    return render_page(title="会员管理", body=body)


@router.get("/orders")
def admin_orders(
    request: Request,
    q: str = "",
    state: str = "",
    message: str = "",
    page: int = 1,
    page_size: int = 20,
) -> Response:
    """展示订单管理页。

    参数：
        request: FastAPI 请求对象。
        q: 邮箱或订单号搜索关键字。
        state: 订单状态筛选。
        message: 操作结果提示。
        page: 订单列表页码。
        page_size: 订单列表每页数量。

    返回：
        Response: 订单后台页面或登录页跳转。
    """

    if not has_admin_session(request):
        return redirect_login()

    config = load_config()
    order_page = list_payment_order_page(
        config.database_path,
        query=q,
        state=state,
        page=page,
        page_size=page_size,
    )
    orders = order_page["items"]
    rows = "\n".join(render_payment_order_row(order) for order in orders)
    pager_html = render_pagination(
        path="/admin/orders",
        current_page=int(order_page["page"]),
        page_size=int(order_page["pageSize"]),
        total=int(order_page["total"]),
        params={"q": q, "state": state},
    )
    message_html = f'<div class="message">{escape(message)}</div>' if message else ""
    body = f"""
    {message_html}
    <section>
      <div class="section-title">
        <h2>订单</h2>
        <a class="badge" href="/admin">返回会员管理</a>
      </div>
      <form method="get" action="/admin/orders">
        <label>
          邮箱/订单号
          <input type="text" name="q" value="{escape(q)}">
        </label>
        <label>
          状态
          <select name="state">
            {render_payment_state_options(state)}
          </select>
        </label>
        {render_page_size_select(int(order_page["pageSize"]))}
        <input type="hidden" name="page" value="1">
        <button type="submit">查询</button>
      </form>
    </section>
    <section>
      <div class="section-title">
        <h2>最近订单</h2>
        <span class="muted">待支付订单超时后会自动变为过期。</span>
      </div>
      <table>
        <thead>
          <tr>
            <th>订单号</th>
            <th>邮箱/账号</th>
            <th>套餐</th>
            <th>金额</th>
            <th>状态</th>
            <th>创建</th>
            <th>过期</th>
            <th>支付</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>{rows or '<tr><td colspan="9" class="muted">暂无订单</td></tr>'}</tbody>
      </table>
      {pager_html}
    </section>
    """
    return render_page(title="订单管理", body=body)


@router.get("/samples")
def admin_samples(
    request: Request,
    account: str = "",
    q: str = "",
    label: str = "",
    message: str = "",
    page: int = 1,
    page_size: int = 20,
) -> Response:
    """展示训练样本明细页。

    参数：
        request: FastAPI 请求对象。
        account: 账号筛选关键字，支持邮箱或旧手机号片段。
        q: 样本 ID、通知 ID 或通知原文搜索关键字。
        label: 反馈标签筛选。
        message: 操作结果提示。
        page: 样本列表页码。
        page_size: 样本列表每页数量。

    返回：
        Response: 样本后台页面或登录页跳转。
    """

    if not has_admin_session(request):
        return redirect_login()

    config = load_config()
    sample_page = list_training_sample_page(
        config.database_path,
        account_query=account,
        query=q,
        feedback_label=label,
        page=page,
        page_size=page_size,
    )
    samples = sample_page["items"]
    rows = "\n".join(render_training_sample_row(sample) for sample in samples)
    pager_html = render_pagination(
        path="/admin/samples",
        current_page=int(sample_page["page"]),
        page_size=int(sample_page["pageSize"]),
        total=int(sample_page["total"]),
        params={"account": account, "q": q, "label": label},
    )
    message_html = f'<div class="message">{escape(message)}</div>' if message else ""
    body = f"""
    {message_html}
    <section>
      <div class="section-title">
        <h2>训练样本</h2>
        <a class="badge" href="/admin">返回会员管理</a>
      </div>
      <form method="get" action="/admin/samples">
        <label>
          账号邮箱
          <input type="text" name="account" value="{escape(account)}">
        </label>
        <label>
          样本/通知/原文
          <input type="text" name="q" value="{escape(q)}">
        </label>
        <label>
          标签
          <select name="label">
            {render_training_sample_label_options(label)}
          </select>
        </label>
        {render_page_size_select(int(sample_page["pageSize"]))}
        <input type="hidden" name="page" value="1">
        <button type="submit">查询</button>
      </form>
    </section>
    <section>
      <div class="section-title">
        <h2>新增样本</h2>
        <span class="muted">普通模式填写原文即可；高级模式可直接粘贴完整 JSON。</span>
      </div>
      <form class="wide-form" method="post" action="/admin/samples/create">
        <label>
          账号邮箱
          <input type="email" name="phone" placeholder="user@example.com" required>
        </label>
        <label>
          标签
          <select name="feedback_label">
            {render_training_sample_label_options("USER_CONFIRMED_POSITIVE")}
          </select>
        </label>
        <label>
          来源
          <input type="text" name="source_app" value="OTHER">
        </label>
        <label>
          包名
          <input type="text" name="package_name" placeholder="com.example.app">
        </label>
        <label>
          金额
          <input type="number" name="amount" step="0.01" placeholder="19.90">
        </label>
        <label>
          收支方向
          <select name="direction">
            <option value="EXPENSE" selected>支出</option>
            <option value="INCOME">收入</option>
          </select>
        </label>
        <label>
          分类
          <input type="text" name="category" placeholder="餐饮 / 交通 / 工资">
        </label>
        <label class="full">
          通知标题
          <input type="text" name="raw_title" placeholder="微信支付">
        </label>
        <label class="full">
          通知原文
          <textarea name="raw_text" placeholder="已支付 19.90 元，商户：..."></textarea>
        </label>
        <label class="full">
          备注
          <textarea name="note" placeholder="后台人工补充样本原因"></textarea>
        </label>
        <label class="full">
          完整 JSON（可选）
          <textarea name="raw_payload_json" placeholder='{{"rawText":"...","feedbackLabel":"USER_CONFIRMED_POSITIVE"}}'></textarea>
        </label>
        <button type="submit">新增样本</button>
      </form>
    </section>
    <section>
      <div class="section-title">
        <h2>样本明细</h2>
        <span class="muted">按页展示样本；展开“完整 JSON”可查看服务器保存的每一行原始样本。</span>
      </div>
      <table>
        <thead>
          <tr>
            <th>ID</th>
            <th>账号</th>
            <th>来源</th>
            <th>通知原文</th>
            <th>解析</th>
            <th>纠错</th>
            <th>标签</th>
            <th>时间</th>
            <th>详情</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>{rows or '<tr><td colspan="10" class="muted">暂无样本</td></tr>'}</tbody>
      </table>
      {pager_html}
    </section>
    """
    return render_page(title="训练样本", body=body)


@router.post("/samples/create")
async def create_sample(request: Request) -> Response:
    """新增后台人工训练样本。

    参数：
        request: FastAPI 请求对象，表单中包含账号、原文、标签和可选完整 JSON。

    返回：
        Response: 操作完成后的样本页跳转。
    """

    if not has_admin_session(request):
        return redirect_login()

    config = load_config()
    form = await read_form(request)
    try:
        sample = create_manual_training_sample(
            database_path=config.database_path,
            phone=form.get("phone", ""),
            raw_title=form.get("raw_title", ""),
            raw_text=form.get("raw_text", ""),
            source_app=form.get("source_app", ""),
            package_name=form.get("package_name", ""),
            feedback_label=form.get("feedback_label", ""),
            amount=form.get("amount", ""),
            direction=form.get("direction", ""),
            category=form.get("category", ""),
            note=form.get("note", ""),
            raw_payload_json=form.get("raw_payload_json", ""),
        )
        message = f"已新增样本 #{sample['id']}，账号 {sample['phone']}"
        return RedirectResponse(url=f"/admin/samples?q={sample['id']}&message={quote(message)}", status_code=303)
    except ValueError as exc:
        return RedirectResponse(url=f"/admin/samples?message={quote(str(exc))}", status_code=303)


@router.post("/samples/delete")
async def delete_sample(request: Request) -> Response:
    """删除后台训练样本。

    参数：
        request: FastAPI 请求对象，表单中包含 sample_id。

    返回：
        Response: 操作完成后的样本页跳转。
    """

    if not has_admin_session(request):
        return redirect_login()

    config = load_config()
    form = await read_form(request)
    try:
        sample = delete_training_sample(config.database_path, sample_id=form.get("sample_id", ""))
        message = f"已删除样本 #{sample['id']}，账号 {sample['phone']}"
        return RedirectResponse(url=f"/admin/samples?message={quote(message)}", status_code=303)
    except ValueError as exc:
        return RedirectResponse(url=f"/admin/samples?message={quote(str(exc))}", status_code=303)


@router.post("/orders/mark-paid")
async def mark_order_paid(request: Request) -> Response:
    """人工确认订单收款并开通会员。

    参数：
        request: FastAPI 请求对象。

    返回：
        Response: 操作完成后的订单页跳转。
    """

    if not has_admin_session(request):
        return redirect_login()

    config = load_config()
    form = await read_form(request)
    order_id = form.get("order_id", "")
    try:
        result = mark_payment_order_paid(config.database_path, order_id=order_id)
        message = (
            f"已确认收款：{result['phone']}，"
            f"{result['planLabel']} 有效期至 {format_millis(result['memberExpiresAt'])}"
        )
        return RedirectResponse(url=f"/admin/orders?q={result['orderId']}&message={quote(message)}", status_code=303)
    except Exception as exc:
        return RedirectResponse(url=f"/admin/orders?message={quote(str(exc))}", status_code=303)


def render_recent_orders_section(database_path: str) -> str:
    """渲染首页最近订单摘要。

    参数：
        database_path: SQLite 数据库文件路径。

    返回：
        str: 后台首页订单摘要 HTML。
    """

    try:
        orders = list_payment_orders(database_path, limit=5)
        rows = "\n".join(render_payment_order_row(order, compact=True) for order in orders)
        return f"""
        <section>
          <div class="section-title">
            <h2>订单处理</h2>
            <a class="badge" href="/admin/orders">查看全部</a>
          </div>
          <table>
            <thead>
              <tr>
                <th>订单号</th>
                <th>邮箱/账号</th>
                <th>套餐</th>
                <th>金额</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>{rows or '<tr><td colspan="6" class="muted">暂无订单</td></tr>'}</tbody>
          </table>
        </section>
        """
    except Exception as exc:
        # 首页订单摘要失败时仍允许管理员继续发会员和触发训练。
        return f"""
        <section>
          <div class="section-title">
            <h2>订单处理</h2>
            <a class="badge" href="/admin/orders">查看全部</a>
          </div>
          <p class="muted">读取失败：{escape(str(exc))}</p>
        </section>
        """


def render_global_model_section(database_path: str) -> str:
    """渲染全局模型摘要。

    参数：
        database_path: SQLite 数据库文件路径。

    返回：
        str: 管理后台首页中的全局模型摘要 HTML。
    """

    try:
        model = get_active_global_model(database_path)
        sample_count = count_global_training_samples(database_path)
        return f"""
        <section>
          <h2>全局模型</h2>
          <table>
            <tbody>
              <tr><th>当前版本</th><td>{escape(str(model.get("modelVersion", "-")))}</td></tr>
              <tr><th>模型范围</th><td>{escape(str(model.get("modelScope", "GLOBAL")))}</td></tr>
              <tr><th>训练样本</th><td>{sample_count}</td></tr>
              <tr><th>发布时间</th><td>{escape(format_millis(model.get("publishedAt")))}</td></tr>
            </tbody>
          </table>
        </section>
        """
    except Exception as exc:
        # 后台摘要失败不能影响人工发会员，直接展示错误即可。
        return f"""
        <section>
          <h2>全局模型</h2>
          <p class="muted">读取失败：{escape(str(exc))}</p>
        </section>
        """


def render_training_section(config: AppConfig) -> str:
    """渲染训练任务摘要。

    参数：
        config: 后端运行配置，包含数据库路径和当前训练门槛。

    返回：
        str: 管理后台首页中的训练任务 HTML。
    """

    try:
        database_path = config.database_path
        summary = training_dataset_summary(database_path)
        jobs = list_training_jobs(database_path, limit=5)
        rows = "\n".join(render_training_job_row(job) for job in jobs)
        return f"""
        <section>
          <div class="section-title">
            <h2>全局训练</h2>
            <a class="badge" href="/admin/samples">查看样本</a>
          </div>
          <table>
            <tbody>
              <tr><th>全局样本</th><td>{summary["totalSamples"]}</td></tr>
              <tr><th>有效标签</th><td>{summary["labeledSamples"]}</td></tr>
              <tr><th>正/负样本</th><td>{summary["positiveSamples"]} / {summary["negativeSamples"]}</td></tr>
            </tbody>
          </table>
          <form method="post" action="/admin/training/run" style="margin-top: 14px;">
            <label>
              新增样本门槛
              <input type="number" name="min_new_samples" value="{config.training_min_new_samples}" min="1" max="100000">
            </label>
            <p class="muted">
              当前自动训练配置：新增有效样本 ≥ {config.training_min_new_samples}，总有效样本 ≥ {config.training_min_total_samples}，正负样本各 ≥ {config.training_min_per_class}。
            </p>
            <label>
              <span>强制训练</span>
              <input type="checkbox" name="force">
            </label>
            <button type="submit">手动训练</button>
          </form>
          <table style="margin-top: 14px;">
            <thead>
              <tr>
                <th>状态</th>
                <th>触发</th>
                <th>有效样本</th>
                <th>新增</th>
                <th>模型</th>
                <th>说明</th>
              </tr>
            </thead>
            <tbody>{rows or '<tr><td colspan="6" class="muted">暂无训练任务</td></tr>'}</tbody>
          </table>
        </section>
        """
    except Exception as exc:
        # 训练摘要失败不影响会员管理主功能，直接展示错误便于排查。
        return f"""
        <section>
          <div class="section-title">
            <h2>全局训练</h2>
            <a class="badge" href="/admin/samples">查看样本</a>
          </div>
          <p class="muted">读取失败：{escape(str(exc))}</p>
        </section>
        """


def render_pagination(
    path: str,
    current_page: int,
    page_size: int,
    total: int,
    params: dict[str, object],
) -> str:
    """渲染后台列表分页控件。

    参数：
        path: 当前列表页面路径。
        current_page: 当前页码，从 1 开始。
        page_size: 每页记录数量。
        total: 当前筛选条件下的总记录数。
        params: 需要在翻页时保留的筛选参数。

    返回：
        str: 分页信息和上一页、下一页链接 HTML。
    """

    total_pages = max(1, (max(0, total) + max(1, page_size) - 1) // max(1, page_size))
    normalized_page = max(1, min(current_page, total_pages))
    start = 0 if total <= 0 else (normalized_page - 1) * page_size + 1
    end = min(total, normalized_page * page_size)
    prev_html = render_page_link(path, "上一页", normalized_page - 1, page_size, params, normalized_page > 1)
    next_html = render_page_link(
        path,
        "下一页",
        normalized_page + 1,
        page_size,
        params,
        normalized_page < total_pages,
    )
    return f"""
    <div class="pager">
      <span>共 {total} 条，第 {normalized_page}/{total_pages} 页，当前 {start}-{end} 条</span>
      <div class="pager-actions">{prev_html}{next_html}</div>
    </div>
    """


def render_page_size_select(selected_page_size: int) -> str:
    """渲染每页数量选择框。

    参数：
        selected_page_size: 当前列表正在使用的每页数量。

    返回：
        str: 每页数量 `<select>` HTML。
    """

    options = []
    for size in [20, 50, 100]:
        selected = " selected" if selected_page_size == size else ""
        options.append(f'<option value="{size}"{selected}>{size} 条/页</option>')
    return f"""
    <label>
      每页
      <select name="page_size">
        {"".join(options)}
      </select>
    </label>
    """


def render_page_link(
    path: str,
    label: str,
    page: int,
    page_size: int,
    params: dict[str, object],
    enabled: bool,
) -> str:
    """渲染单个分页链接。

    参数：
        path: 当前列表页面路径。
        label: 链接显示文本。
        page: 目标页码。
        page_size: 每页记录数量。
        params: 翻页时需要保留的筛选参数。
        enabled: 是否允许点击。

    返回：
        str: `<a>` 或禁用态 `<span>` HTML。
    """

    if not enabled:
        return f'<span class="disabled">{escape(label)}</span>'
    query_params = {
        key: value
        for key, value in params.items()
        if value is not None and str(value) != ""
    }
    query_params["page"] = page
    query_params["page_size"] = page_size
    # urlencode 统一处理中文搜索关键字，避免手写拼接导致 URL 乱码。
    href = f"{path}?{urlencode(query_params)}"
    return f'<a href="{escape(href)}">{escape(label)}</a>'


@router.post("/memberships/grant")
async def grant_membership(request: Request) -> Response:
    """处理会员发放。

    参数：
        request: FastAPI 请求对象。

    返回：
        Response: 操作结果跳转。
    """

    if not has_admin_session(request):
        return redirect_login()

    config = load_config()
    form = await read_form(request)
    try:
        status = grant_member(
            database_path=config.database_path,
            phone=form.get("phone", ""),
            days=int(form.get("days", "30")),
            model_confidence_enabled="model_confidence_enabled" in form,
            cloud_learning_enabled="cloud_learning_enabled" in form,
        )
        message = f"已发放会员：{status['phone']}"
        return RedirectResponse(url=f"/admin?q={status['phone']}&message={quote(message)}", status_code=303)
    except Exception as exc:
        return RedirectResponse(url=f"/admin?message={quote(str(exc))}", status_code=303)


@router.post("/memberships/revoke")
async def revoke_membership(request: Request) -> Response:
    """处理会员撤销。

    参数：
        request: FastAPI 请求对象。

    返回：
        Response: 操作结果跳转。
    """

    if not has_admin_session(request):
        return redirect_login()

    config = load_config()
    form = await read_form(request)
    try:
        status = revoke_member(database_path=config.database_path, phone=form.get("phone", ""))
        message = f"已撤销会员：{status['phone']}"
        return RedirectResponse(url=f"/admin?q={status['phone']}&message={quote(message)}", status_code=303)
    except Exception as exc:
        return RedirectResponse(url=f"/admin?message={quote(str(exc))}", status_code=303)


@router.post("/training/run")
async def run_training_job(request: Request) -> Response:
    """手动触发全局训练检查。

    参数：
        request: FastAPI 请求对象。

    返回：
        Response: 操作结果跳转。
    """

    if not has_admin_session(request):
        return redirect_login()

    config = load_config()
    form = await read_form(request)
    try:
        result = run_global_training(
            database_path=config.database_path,
            trigger_type="admin",
            policy=TrainingPolicy(
                min_new_samples=read_int_form(form, "min_new_samples", config.training_min_new_samples),
                min_total_samples=config.training_min_total_samples,
                min_per_class=config.training_min_per_class,
                min_accuracy=config.training_min_accuracy,
                max_false_positive_rate=config.training_max_false_positive_rate,
                force="force" in form,
            ),
        )
        message = f"训练任务 {result['state']}：{result.get('message') or result['jobId']}"
        return RedirectResponse(url=f"/admin?message={quote(message)}", status_code=303)
    except Exception as exc:
        return RedirectResponse(url=f"/admin?message={quote(str(exc))}", status_code=303)


def render_member_row(member: dict[str, object]) -> str:
    """渲染用户表格行。

    参数：
        member: 单个用户会员状态。

    返回：
        str: 表格行 HTML。
    """

    phone = str(member.get("phone", ""))
    tier = str(member.get("tier", "FREE"))
    trial_used = int(member.get("autoLedgerTrialUsed", 0) or 0)
    # 管理服务会返回服务器配置的体验总次数；这里不再写死 10，避免后台展示和 API 配置不一致。
    trial_limit = int(member.get("autoLedgerTrialLimit", 0) or 0)
    return f"""
    <tr>
      <td>{escape(phone)}</td>
      <td>{escape(tier)}</td>
      <td>{escape(format_millis(member.get("expiresAt")))}</td>
      <td>{trial_used}/{trial_limit}</td>
      <td>{format_bool(member.get("modelConfidenceEnabled"))}</td>
      <td>{format_bool(member.get("cloudLearningEnabled"))}</td>
      <td>
        <form class="inline-form" method="post" action="/admin/memberships/revoke">
          <input type="hidden" name="phone" value="{escape(phone)}">
          <button class="danger" type="submit">撤销</button>
        </form>
      </td>
    </tr>
    """


def render_payment_order_row(order: dict[str, object], compact: bool = False) -> str:
    """渲染支付订单表格行。

    参数：
        order: 单个支付订单摘要。
        compact: 是否使用首页紧凑列布局。

    返回：
        str: 表格行 HTML。
    """

    order_id = str(order.get("orderId", ""))
    phone = str(order.get("phone", ""))
    plan_label = str(order.get("planLabel", order.get("plan", "-")))
    amount = format_amount_cents(order.get("amountCents"))
    state = str(order.get("paymentState", "-"))
    action_html = render_payment_order_action(order)
    if compact:
        short_order_id = order_id[:18] + "..." if len(order_id) > 21 else order_id
        return f"""
        <tr>
          <td title="{escape(order_id)}">{escape(short_order_id)}</td>
          <td>{escape(phone)}</td>
          <td>{escape(plan_label)}</td>
          <td class="amount">{escape(amount)}</td>
          <td>{format_payment_state(state)}</td>
          <td>{action_html}</td>
        </tr>
        """

    return f"""
    <tr>
      <td>{escape(order_id)}</td>
      <td>{escape(phone)}</td>
      <td>{escape(plan_label)}</td>
      <td class="amount">{escape(amount)}</td>
      <td>{format_payment_state(state)}</td>
      <td>{escape(format_millis(order.get("createdAt")))}</td>
      <td>{escape(format_millis(order.get("expiresAt")))}</td>
      <td>{escape(format_millis(order.get("paidAt")))}</td>
      <td>{action_html}</td>
    </tr>
    """


def render_training_sample_row(sample: dict[str, object]) -> str:
    """渲染训练样本表格行。

    参数：
        sample: 单条训练样本摘要和完整 JSON。

    返回：
        str: 表格行 HTML。
    """

    sample_id = str(sample.get("id", ""))
    source_text = "\n".join(
        value
        for value in [
            str(sample.get("sourceApp") or "-"),
            str(sample.get("packageName") or ""),
        ]
        if value
    )
    raw_text = "\n".join(
        value
        for value in [
            str(sample.get("rawTitle") or ""),
            str(sample.get("rawText") or ""),
        ]
        if value
    )
    corrected = sample.get("corrected")
    updated_at = sample.get("updatedAt")
    inserted_at = sample.get("insertedAt")
    update_text = ""
    if updated_at and updated_at != inserted_at:
        update_text = f"<br><span class=\"muted\">更新 {escape(format_millis(updated_at))}</span>"
    return f"""
    <tr>
      <td>
        <strong>{escape(sample_id)}</strong><br>
        <span class="muted">本地 {escape(format_optional(sample.get("sampleLocalId")))}</span><br>
        <span class="muted">通知 {escape(format_optional(sample.get("notificationId")))}</span>
      </td>
      <td>{escape(str(sample.get("phone") or "-"))}</td>
      <td class="sample-raw">{escape(source_text)}</td>
      <td class="sample-raw">{escape(truncate_text(raw_text, 260))}</td>
      <td>{format_sample_snapshot(sample.get("parsed"))}</td>
      <td>{format_sample_snapshot(corrected) if corrected else '<span class="muted">-</span>'}</td>
      <td>
        {format_training_label(str(sample.get("feedbackLabel") or ""))}<br>
        <span class="muted">{escape(str(sample.get("feedbackSource") or "-"))}</span>
      </td>
      <td>
        {escape(format_millis(inserted_at))}
        {update_text}
      </td>
      <td>{render_training_sample_detail(sample)}</td>
      <td>{render_training_sample_action(sample_id)}</td>
    </tr>
    """


def render_training_sample_action(sample_id: str) -> str:
    """渲染训练样本操作区。

    参数：
        sample_id: 当前样本 ID。

    返回：
        str: 删除按钮表单 HTML。
    """

    return f"""
    <form class="inline-form" method="post" action="/admin/samples/delete">
      <input type="hidden" name="sample_id" value="{escape(sample_id)}">
      <button class="danger" type="submit" onclick="return confirm('确认删除样本 #{escape(sample_id)}？')">删除</button>
    </form>
    """


def render_training_sample_detail(sample: dict[str, object]) -> str:
    """渲染训练样本详情。

    参数：
        sample: 单条训练样本摘要和完整 JSON。

    返回：
        str: 可展开查看的详情 HTML。
    """

    detail_lines = [
        f"批次：{format_optional(sample.get('clientBatchId'))}",
        f"设备：{format_optional(sample.get('deviceId'))}",
        f"App：{format_optional(sample.get('appVersion'))}",
        f"模型：{format_optional(sample.get('modelVersion'))}",
        f"解析路径：{format_optional(sample.get('parsePath'))}",
        f"置信度：{format_optional(sample.get('confidenceLevel'))} / {format_optional(sample.get('confidenceScore'))}",
        f"路由：{format_optional(sample.get('routeDestination'))} / {format_optional(sample.get('processStatus'))}",
        f"反馈时间：{format_millis(sample.get('feedbackAt'))}",
        f"反馈备注：{format_optional(sample.get('feedbackNote'))}",
        f"变更字段：{format_changed_fields(sample.get('changedFields'))}",
    ]
    return f"""
    <details>
      <summary>完整 JSON</summary>
      <p class="muted">{escape(chr(10).join(detail_lines))}</p>
      <pre class="sample-json">{escape(str(sample.get("rawJsonPretty") or "{}"))}</pre>
    </details>
    """


def render_training_sample_label_options(selected_label: str) -> str:
    """渲染训练样本标签筛选选项。

    参数：
        selected_label: 当前筛选标签。

    返回：
        str: `<option>` HTML 文本。
    """

    normalized = selected_label.strip().upper()
    return "\n".join(
        f'<option value="{escape(label)}"{" selected" if label == normalized else ""}>{escape(training_label_text(label))}</option>'
        for label in TRAINING_SAMPLE_LABELS
    )


def format_sample_snapshot(value: object) -> str:
    """格式化解析或纠错字段快照。

    参数：
        value: `parsed` 或 `corrected` 字典。

    返回：
        str: 多行 HTML 文本。
    """

    if not isinstance(value, dict) or not value:
        return '<span class="muted">-</span>'
    lines = [
        f"金额 {format_optional(value.get('amount'))}",
        f"方向 {format_optional(value.get('direction'))}",
        f"分类 {format_optional(value.get('category'))}",
        f"时间 {format_millis(value.get('timestamp'))}",
    ]
    note = value.get("note")
    if note:
        lines.append(f"备注 {truncate_text(str(note), 80)}")
    return "<br>".join(escape(line) for line in lines)


def format_training_label(label: str) -> str:
    """格式化训练样本反馈标签。

    参数：
        label: 样本反馈标签。

    返回：
        str: 带样式的标签 HTML。
    """

    normalized = label.strip().upper()
    positive_labels = {"AUTO_LEDGER_UNREVIEWED", "USER_CONFIRMED_POSITIVE", "USER_CONFIRMED_EDITED", "USER_EDITED_LEDGER"}
    negative_labels = {"FILTERED_UNREVIEWED", "USER_DISMISSED_NEGATIVE", "USER_MARKED_FALSE_POSITIVE", "USER_DELETED_LEDGER"}
    css_class = "label-pending"
    if normalized in positive_labels:
        css_class = "label-positive"
    if normalized in negative_labels:
        css_class = "label-negative"
    return f'<span class="badge {css_class}">{escape(training_label_text(normalized))}</span>'


def training_label_text(label: str) -> str:
    """把样本标签转换为中文。

    参数：
        label: 样本反馈标签。

    返回：
        str: 中文展示文案。
    """

    label_map = {
        "": "全部",
        "AUTO_LEDGER_UNREVIEWED": "自动入账",
        "PENDING_AWAITING_USER": "待用户确认",
        "FILTERED_UNREVIEWED": "已过滤",
        "DUPLICATE_AWAITING_USER": "重复待确认",
        "RAW_NOTIFICATION_UNREVIEWED": "原文样本",
        "USER_CONFIRMED_POSITIVE": "用户确认",
        "USER_CONFIRMED_EDITED": "用户修改确认",
        "USER_DISMISSED_NEGATIVE": "用户忽略",
        "USER_MARKED_FALSE_POSITIVE": "用户标误识别",
        "USER_EDITED_LEDGER": "账本被编辑",
        "USER_DELETED_LEDGER": "账本被删除",
    }
    return label_map.get(label, label or "-")


def format_changed_fields(value: object) -> str:
    """格式化用户纠错字段。

    参数：
        value: changedFields 数组。

    返回：
        str: 逗号分隔字段列表。
    """

    if not isinstance(value, list) or not value:
        return "-"
    return "，".join(str(item) for item in value)


def truncate_text(value: str, max_length: int) -> str:
    """截断长文本。

    参数：
        value: 原始文本。
        max_length: 最大字符数。

    返回：
        str: 适合表格展示的文本。
    """

    if len(value) <= max_length:
        return value
    return value[:max_length] + "..."


def format_optional(value: object) -> str:
    """格式化可空值。

    参数：
        value: 任意待展示值。

    返回：
        str: 空值返回短横线。
    """

    if value is None or value == "":
        return "-"
    return str(value)


def render_payment_order_action(order: dict[str, object]) -> str:
    """渲染订单操作按钮。

    参数：
        order: 单个支付订单摘要。

    返回：
        str: 可放进表格单元格的 HTML。
    """

    if not order.get("canConfirmPaid"):
        return '<span class="muted">-</span>'
    order_id = str(order.get("orderId", ""))
    # 当前阶段还没有微信支付回调，管理员确认收到款后点击该按钮自动开通会员。
    return f"""
    <form class="inline-form" method="post" action="/admin/orders/mark-paid">
      <input type="hidden" name="order_id" value="{escape(order_id)}">
      <button type="submit">确认收款并开通</button>
    </form>
    """


def render_payment_state_options(selected_state: str) -> str:
    """渲染订单状态筛选下拉选项。

    参数：
        selected_state: 当前选中的订单状态。

    返回：
        str: `<option>` HTML 文本。
    """

    normalized = selected_state.strip().upper()
    options = [
        ("", "全部"),
        ("CREATED", "待支付"),
        ("PAYING", "支付中"),
        ("PAID", "已支付"),
        ("EXPIRED", "已过期"),
        ("CANCELLED", "已取消"),
        ("FAILED", "失败"),
    ]
    return "\n".join(
        f'<option value="{escape(value)}"{" selected" if value == normalized else ""}>{escape(label)}</option>'
        for value, label in options
    )


def format_payment_state(state: str) -> str:
    """格式化订单状态。

    参数：
        state: 订单状态枚举。

    返回：
        str: 带状态样式的 HTML。
    """

    label_map = {
        "CREATED": "待支付",
        "PAYING": "支付中",
        "PAID": "已支付",
        "CANCELLED": "已取消",
        "EXPIRED": "已过期",
        "FAILED": "失败",
    }
    normalized = state.strip().upper()
    css_class = f"state-{normalized.lower()}"
    return f'<span class="badge {escape(css_class)}">{escape(label_map.get(normalized, normalized or "-"))}</span>'


def format_amount_cents(value: object) -> str:
    """格式化订单金额。

    参数：
        value: 金额，单位分。

    返回：
        str: 人民币展示文本。
    """

    if value is None:
        return "-"
    return f"￥{int(value) / 100:.2f}"


def render_training_job_row(job: dict[str, object]) -> str:
    """渲染训练任务表格行。

    参数：
        job: 单个训练任务摘要。

    返回：
        str: 表格行 HTML。
    """

    return f"""
    <tr>
      <td>{escape(str(job.get("state", "-")))}</td>
      <td>{escape(str(job.get("triggerType", "-")))}</td>
      <td>{escape(str(job.get("labeledSampleCount", 0)))}</td>
      <td>{escape(str(job.get("newSampleCount", 0)))}</td>
      <td>{escape(str(job.get("modelVersion") or "-"))}</td>
      <td>{escape(str(job.get("message") or "-"))}</td>
    </tr>
    """


def format_bool(value: object) -> str:
    """格式化布尔值。

    参数：
        value: 待格式化值。

    返回：
        str: 中文布尔展示。
    """

    return "开启" if bool(value) else "关闭"


def format_millis(value: object) -> str:
    """格式化毫秒时间。

    参数：
        value: 毫秒时间戳。

    返回：
        str: 简短时间展示。
    """

    if value is None:
        return "-"
    from datetime import datetime

    return datetime.fromtimestamp(int(value) / 1000).strftime("%Y-%m-%d %H:%M")
