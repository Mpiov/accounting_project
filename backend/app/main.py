"""AutoBook 后端 API 入口。

当前版本是生产后端的技术骨架：接口路径、鉴权、会员门禁和持久化都已打通，
短信、微信支付和模型训练任务后续再接真实供应商或异步任务。
"""

import json
from uuid import uuid4

from fastapi import FastAPI, Header, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from .admin_web import router as admin_router
from .auth import build_token_pair, hash_value, require_user
from .config import load_config
from .database import init_database, transaction
from .global_model_service import GLOBAL_MODEL_SCOPE
from .global_model_service import ensure_bootstrap_global_model
from .global_model_service import get_active_global_model
from .response import exception_response, fail, now_millis, ok
from .schemas import (
    CreateOrderRequest,
    LoginRequest,
    RefreshRequest,
    SmsCodeRequest,
    UploadTrainingSamplesRequest,
)

CONFIG = load_config()
app = FastAPI(title="AutoBook API", version="0.1.0")
app.include_router(admin_router)


@app.on_event("startup")
def startup() -> None:
    """应用启动初始化。

    作用：
        确保 SQLite 表结构存在，容器重启后可以直接继续使用已有数据。
    """

    init_database(CONFIG.database_path)
    ensure_bootstrap_global_model(CONFIG.database_path)


@app.exception_handler(HTTPException)
def handle_http_exception(_: Request, exc: HTTPException) -> JSONResponse:
    """统一业务异常响应。

    参数：
        _: 当前请求对象，异常处理器签名需要但这里不使用。
        exc: FastAPI 捕获到的 HTTPException。

    返回：
        JSONResponse: 契约格式的错误响应。
    """

    return exception_response(exc)


@app.exception_handler(RequestValidationError)
def handle_validation_exception(_: Request, __: RequestValidationError) -> JSONResponse:
    """统一参数校验失败响应。

    参数：
        _: 当前请求对象，异常处理器签名需要但这里不使用。
        __: Pydantic 校验异常，当前不向客户端暴露内部字段细节。

    返回：
        JSONResponse: 契约格式的参数错误响应。
    """

    return exception_response(fail(400, "BAD_REQUEST", "请求字段缺失或格式错误"))


def current_membership(user_id: str) -> dict[str, object]:
    """读取服务端权威会员状态。

    参数：
        user_id: 用户 ID。

    返回：
        dict[str, object]: 可直接返回给客户端的会员状态。
    """

    with transaction(CONFIG.database_path) as db:
        row = db.execute(
            """
            SELECT u.id AS user_id, u.phone, m.tier, m.expires_at,
                   m.model_confidence_enabled, m.cloud_learning_enabled
            FROM users u
            LEFT JOIN memberships m ON m.user_id = u.id
            WHERE u.id = ?
            """,
            (user_id,),
        ).fetchone()

    if row is None:
        raise fail(401, "AUTH_TOKEN_EXPIRED", "登录已过期，请重新登录")

    tier = row["tier"] or "FREE"
    expires_at = row["expires_at"]
    # 会员过期时服务端直接降级，客户端不能凭本地缓存继续使用模型能力。
    active_member = tier == "MEMBER" and expires_at is not None and int(expires_at) > now_millis()
    return {
        "userId": row["user_id"],
        "phone": row["phone"],
        "tier": "MEMBER" if active_member else "FREE",
        "expiresAt": expires_at if active_member else None,
        "modelConfidenceEnabled": bool(row["model_confidence_enabled"]) if active_member else False,
        "cloudLearningEnabled": bool(row["cloud_learning_enabled"]) if active_member else False,
        "serverVerified": True,
    }


def ensure_member_feature(user_id: str, feature: str) -> dict[str, object]:
    """校验会员功能门禁。

    参数：
        user_id: 用户 ID。
        feature: 需要检查的功能字段，如 modelConfidenceEnabled。

    返回：
        dict[str, object]: 已确认有效的会员状态。
    """

    membership = current_membership(user_id)
    if membership.get("tier") != "MEMBER" or not membership.get(feature):
        raise fail(403, "MEMBERSHIP_REQUIRED", "当前账号不是会员")
    return membership


@app.get("/health")
def health() -> dict[str, str]:
    """服务健康检查。

    返回：
        dict[str, str]: 简单健康状态，供 Nginx 或运维检查使用。
    """

    return {"status": "ok"}


@app.post("/v1/auth/sms-code")
def request_sms_code(body: SmsCodeRequest) -> dict[str, object]:
    """发送验证码。

    参数：
        body: 手机号和登录场景。

    返回：
        dict[str, object]: 冷却时间与过期时间。
    """

    issued_at = now_millis()
    expires_at = issued_at + 300_000
    code_hash = hash_value(f"{body.phone}:{body.scene}:{CONFIG.dev_sms_code}", CONFIG.token_secret)

    with transaction(CONFIG.database_path) as db:
        # 当前阶段不接真实短信，把固定开发验证码的摘要写入数据库，保留完整登录流程。
        db.execute(
            """
            INSERT INTO sms_codes(phone, scene, code_hash, expires_at, requested_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(phone, scene) DO UPDATE SET
                code_hash = excluded.code_hash,
                expires_at = excluded.expires_at,
                requested_at = excluded.requested_at
            """,
            (body.phone, body.scene, code_hash, expires_at, issued_at),
        )

    return ok({"cooldownSeconds": 60, "expiresInSeconds": 300})


@app.post("/v1/auth/login")
def login(body: LoginRequest) -> dict[str, object]:
    """验证码登录或自动注册。

    参数：
        body: 手机号、验证码和客户端信息。

    返回：
        dict[str, object]: 用户身份和令牌数据。
    """

    expected_hash = hash_value(f"{body.phone}:login:{body.code}", CONFIG.token_secret)
    now = now_millis()

    with transaction(CONFIG.database_path) as db:
        sms_row = db.execute(
            "SELECT code_hash, expires_at FROM sms_codes WHERE phone = ? AND scene = 'login'",
            (body.phone,),
        ).fetchone()
        if sms_row is None or sms_row["expires_at"] < now or sms_row["code_hash"] != expected_hash:
            raise fail(400, "BAD_REQUEST", "验证码错误或已过期")

        user_row = db.execute("SELECT id, phone FROM users WHERE phone = ?", (body.phone,)).fetchone()
        is_new_user = user_row is None
        user_id = f"user_{uuid4().hex}"
        if is_new_user:
            db.execute("INSERT INTO users(id, phone, created_at) VALUES (?, ?, ?)", (user_id, body.phone, now))
            # 新账号默认免费，后续只由支付回调或后台操作改成会员。
            db.execute(
                """
                INSERT INTO memberships(user_id, tier, expires_at, model_confidence_enabled, cloud_learning_enabled, updated_at)
                VALUES (?, 'FREE', NULL, 0, 0, ?)
                """,
                (user_id, now),
            )
        else:
            user_id = user_row["id"]

    token_pair = build_token_pair(user_id, CONFIG)
    return ok({"userId": user_id, "phone": body.phone, "isNewUser": is_new_user, **token_pair})


@app.post("/v1/auth/refresh")
def refresh_token(body: RefreshRequest) -> dict[str, object]:
    """刷新登录令牌。

    参数：
        body: 刷新令牌。

    返回：
        dict[str, object]: 新的访问令牌和刷新令牌。
    """

    token_hash = hash_value(body.refreshToken, CONFIG.token_secret)
    now = now_millis()

    with transaction(CONFIG.database_path) as db:
        row = db.execute(
            "SELECT user_id, expires_at, revoked FROM refresh_tokens WHERE token_hash = ?",
            (token_hash,),
        ).fetchone()
        if row is None or row["revoked"] or row["expires_at"] < now:
            raise fail(401, "AUTH_TOKEN_EXPIRED", "登录已过期，请重新登录")
        # 刷新令牌采用轮换策略，旧令牌一旦使用就撤销。
        db.execute("UPDATE refresh_tokens SET revoked = 1 WHERE token_hash = ?", (token_hash,))

    return ok(build_token_pair(row["user_id"], CONFIG))


@app.get("/v1/membership/status")
def membership_status(authorization: str | None = Header(default=None)) -> dict[str, object]:
    """查询会员状态。

    参数：
        authorization: Bearer 访问令牌。

    返回：
        dict[str, object]: 服务端权威会员状态。
    """

    user = require_user(CONFIG, authorization)
    return ok(current_membership(user["id"]))


@app.post("/v1/payments/membership-orders")
def create_membership_order(
    body: CreateOrderRequest,
    authorization: str | None = Header(default=None),
) -> dict[str, object]:
    """创建会员订单。

    参数：
        body: 套餐和支付渠道。
        authorization: Bearer 访问令牌。

    返回：
        dict[str, object]: 订单信息；微信支付参数后续接商户号后补齐。
    """

    user = require_user(CONFIG, authorization)
    amount_cents = 1900 if body.plan == "MONTHLY" else 19800
    order_id = f"order_{uuid4().hex}"

    with transaction(CONFIG.database_path) as db:
        db.execute(
            """
            INSERT INTO payment_orders(order_id, user_id, plan, amount_cents, payment_state, paid_at, created_at)
            VALUES (?, ?, ?, ?, 'CREATED', NULL, ?)
            """,
            (order_id, user["id"], body.plan, amount_cents, now_millis()),
        )

    return ok(
        {
            "orderId": order_id,
            "plan": body.plan,
            "amountCents": amount_cents,
            "paymentState": "CREATED",
            "wechatAppPayParams": None,
        }
    )


@app.get("/v1/payments/membership-orders/{order_id}")
def query_membership_order(order_id: str, authorization: str | None = Header(default=None)) -> dict[str, object]:
    """查询会员订单状态。

    参数：
        order_id: 订单 ID。
        authorization: Bearer 访问令牌。

    返回：
        dict[str, object]: 订单支付状态。
    """

    user = require_user(CONFIG, authorization)
    with transaction(CONFIG.database_path) as db:
        row = db.execute(
            "SELECT order_id, payment_state, paid_at FROM payment_orders WHERE order_id = ? AND user_id = ?",
            (order_id, user["id"]),
        ).fetchone()

    if row is None:
        raise fail(404, "BAD_REQUEST", "订单不存在")
    return ok({"orderId": row["order_id"], "paymentState": row["payment_state"], "paidAt": row["paid_at"]})


def _coerce_optional_int(value: object) -> int | None:
    """把客户端传来的本地样本 ID 转换为整数。

    参数：
        value: 可能来自显式 sampleLocalId，也可能来自旧版 JSON 里的 id 字段。

    返回：
        int | None: 可用的本地样本 ID；无法转换或小于等于 0 时返回 None。
    """

    if value is None:
        return None
    try:
        number = int(value)
    except (TypeError, ValueError):
        return None
    return number if number > 0 else None


def _extract_sample_local_id(sample: object) -> int | None:
    """读取训练样本里的客户端本地主键。

    参数：
        sample: Pydantic TrainingSample 实例，可能携带 extra 字段。

    返回：
        int | None: 优先使用 sampleLocalId，旧客户端则退回到 extra 里的 id。
    """

    explicit_id = getattr(sample, "sampleLocalId", None)
    if explicit_id is not None:
        return _coerce_optional_int(explicit_id)
    extra_fields = getattr(sample, "model_extra", None) or {}
    return _coerce_optional_int(extra_fields.get("id"))


@app.post("/v1/cloud-learning/training-samples")
def upload_training_samples(
    body: UploadTrainingSamplesRequest,
    authorization: str | None = Header(default=None),
) -> dict[str, object]:
    """上传全局训练样本。

    参数：
        body: 批量训练样本，样本会进入服务器全局样本池。
        authorization: Bearer 访问令牌。

    返回：
        dict[str, object]: 接收、重复和拒绝计数。
    """

    user = require_user(CONFIG, authorization)
    ensure_member_feature(user["id"], "cloudLearningEnabled")
    accepted = 0
    updated = 0
    duplicated = 0

    with transaction(CONFIG.database_path) as db:
        for sample in body.samples:
            now = now_millis()
            sample_local_id = _extract_sample_local_id(sample)
            sample_payload = sample.model_dump(mode="json")
            raw_json = json.dumps(sample_payload, ensure_ascii=False, separators=(",", ":"))
            existing_row = None
            if sample_local_id is not None:
                existing_row = db.execute(
                    """
                    SELECT id, raw_json
                    FROM training_samples
                    WHERE user_id = ? AND sample_local_id = ?
                    """,
                    (user["id"], sample_local_id),
                ).fetchone()
            if existing_row is None:
                # 兼容旧版客户端：没有 sampleLocalId 时继续用通知 ID + 创建时间寻找旧样本。
                existing_row = db.execute(
                    """
                    SELECT id, raw_json
                    FROM training_samples
                    WHERE user_id = ? AND notification_id = ? AND sample_created_at = ?
                    """,
                    (user["id"], sample.notificationId, sample.createdAt),
                ).fetchone()

            if existing_row is None:
                # 新样本进入 GLOBAL 样本池；训练任务汇总所有用户贡献后发布统一模型。
                db.execute(
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
                        user["id"],
                        GLOBAL_MODEL_SCOPE,
                        body.clientBatchId,
                        body.deviceId,
                        body.appVersion,
                        sample_local_id,
                        sample.notificationId,
                        sample.createdAt,
                        raw_json,
                        now,
                        now,
                    ),
                )
                accepted += 1
                continue

            if existing_row["raw_json"] == raw_json:
                duplicated += 1
                continue

            # 用户后续在待确认或账本编辑页纠错后，客户端会重新上传同一个 sampleLocalId，这里覆盖为最新监督信号。
            db.execute(
                """
                UPDATE training_samples
                SET contribution_scope = ?,
                    client_batch_id = ?,
                    device_id = ?,
                    app_version = ?,
                    sample_local_id = COALESCE(?, sample_local_id),
                    notification_id = ?,
                    sample_created_at = ?,
                    raw_json = ?,
                    updated_at = ?
                WHERE id = ?
                """,
                (
                    GLOBAL_MODEL_SCOPE,
                    body.clientBatchId,
                    body.deviceId,
                    body.appVersion,
                    sample_local_id,
                    sample.notificationId,
                    sample.createdAt,
                    raw_json,
                    now,
                    existing_row["id"],
                ),
            )
            updated += 1

    return ok({"accepted": accepted, "updated": updated, "duplicated": duplicated, "rejected": 0, "nextCursor": None})


@app.get("/v1/cloud-learning/global-model")
def pull_global_model(
    currentVersion: str | None = None,
    authorization: str | None = Header(default=None),
) -> dict[str, object]:
    """拉取会员可用的全局模型。

    参数：
        currentVersion: 客户端当前模型版本。
        authorization: Bearer 访问令牌。

    返回：
        dict[str, object]: 当前发布给所有会员客户端的全局线性置信度模型。
    """

    user = require_user(CONFIG, authorization)
    ensure_member_feature(user["id"], "modelConfidenceEnabled")
    model = get_active_global_model(CONFIG.database_path)
    model_version = str(model["modelVersion"])

    if currentVersion == model_version:
        return ok(
            {
                "modelVersion": model_version,
                "modelScope": model["modelScope"],
                "notModified": True,
                "publishedAt": model["publishedAt"],
            }
        )

    model["notModified"] = False
    return ok(model)
