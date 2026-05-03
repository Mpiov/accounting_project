"""服务器本地 Web 管理后台。

第一版后台只绑定服务器本机端口，管理员通过 SSH 隧道访问，不通过公网 Nginx 暴露。
"""

from html import escape
import hmac
from urllib.parse import parse_qs, quote

from fastapi import APIRouter, Request
from fastapi.responses import HTMLResponse, RedirectResponse, Response

from .admin_service import grant_member
from .admin_service import list_member_statuses
from .admin_service import revoke_member
from .auth import parse_token, sign_token
from .config import load_config
from .global_model_service import count_global_training_samples
from .global_model_service import get_active_global_model
from .response import now_millis
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
    input[type="text"], input[type="password"], input[type="number"] {{
      width: min(280px, 100%);
      height: 38px;
      border: 1px solid var(--line);
      border-radius: 6px;
      padding: 0 10px;
      color: var(--text);
      background: #fff;
      font-size: 14px;
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
    @media (max-width: 720px) {{
      header {{ padding: 14px 16px; }}
      main {{ padding: 16px; }}
      table {{ display: block; overflow-x: auto; }}
      input[type="text"], input[type="password"], input[type="number"] {{ width: 100%; }}
    }}
  </style>
</head>
<body>
  <header>
    <h1>AutoBook Admin</h1>
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
def admin_index(request: Request, q: str = "", message: str = "") -> Response:
    """展示会员管理首页。

    参数：
        request: FastAPI 请求对象。
        q: 邮箱或旧手机号搜索关键字。
        message: 操作结果提示。

    返回：
        HTMLResponse | RedirectResponse: 后台首页或登录页跳转。
    """

    if not has_admin_session(request):
        return redirect_login()

    config = load_config()
    members = list_member_statuses(config.database_path, query=q)
    global_model_section = render_global_model_section(config.database_path)
    training_section = render_training_section(config.database_path)
    message_html = f'<div class="message">{escape(message)}</div>' if message else ""
    rows = "\n".join(render_member_row(member) for member in members)
    body = f"""
    {message_html}
    {global_model_section}
    {training_section}
    <section>
      <h2>搜索</h2>
      <form method="get" action="/admin">
        <label>
          邮箱/账号
          <input type="text" name="q" value="{escape(q)}">
        </label>
        <button type="submit">查询</button>
      </form>
    </section>
    <section>
      <h2>发放会员</h2>
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
            <th>模型</th>
            <th>云学习</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>{rows or '<tr><td colspan="6" class="muted">暂无用户</td></tr>'}</tbody>
      </table>
    </section>
    """
    return render_page(title="会员管理", body=body)


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


def render_training_section(database_path: str) -> str:
    """渲染训练任务摘要。

    参数：
        database_path: SQLite 数据库文件路径。

    返回：
        str: 管理后台首页中的训练任务 HTML。
    """

    try:
        summary = training_dataset_summary(database_path)
        jobs = list_training_jobs(database_path, limit=5)
        rows = "\n".join(render_training_job_row(job) for job in jobs)
        return f"""
        <section>
          <h2>全局训练</h2>
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
              <input type="number" name="min_new_samples" value="100" min="1" max="100000">
            </label>
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
          <h2>全局训练</h2>
          <p class="muted">读取失败：{escape(str(exc))}</p>
        </section>
        """


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
                min_new_samples=int(form.get("min_new_samples", "100")),
                min_total_samples=100,
                min_per_class=10,
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
    return f"""
    <tr>
      <td>{escape(phone)}</td>
      <td>{escape(tier)}</td>
      <td>{escape(format_millis(member.get("expiresAt")))}</td>
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
