"""账号令牌与当前用户解析。

这里先用 HMAC 签名令牌满足技术骨架；后续如果接入网关或 OAuth，也只需要替换本文件。
"""

from base64 import urlsafe_b64decode, urlsafe_b64encode
from hashlib import sha256
import hmac
import json
from uuid import uuid4

from fastapi import Header

from .config import AppConfig
from .database import transaction
from .response import fail, now_millis


def hash_value(value: str, secret: str) -> str:
    """计算不可逆摘要。

    参数：
        value: 原始字符串。
        secret: 服务端签名密钥。

    返回：
        str: 十六进制摘要，适合存入数据库。
    """

    return hmac.new(secret.encode("utf-8"), value.encode("utf-8"), sha256).hexdigest()


def sign_token(payload: dict[str, object], secret: str) -> str:
    """签发 HMAC 令牌。

    参数：
        payload: 需要写入令牌的声明。
        secret: 服务端签名密钥。

    返回：
        str: 紧凑的 bearer token 字符串。
    """

    body = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    encoded_body = urlsafe_b64encode(body).decode("ascii").rstrip("=")
    signature = hmac.new(secret.encode("utf-8"), encoded_body.encode("ascii"), sha256).hexdigest()
    return f"{encoded_body}.{signature}"


def parse_token(token: str, secret: str) -> dict[str, object]:
    """校验并解析 HMAC 令牌。

    参数：
        token: 客户端传入的 bearer token。
        secret: 服务端签名密钥。

    返回：
        dict[str, object]: 令牌中的声明。
    """

    try:
        encoded_body, signature = token.split(".", 1)
    except ValueError as exc:
        raise fail(401, "AUTH_REQUIRED", "缺少有效登录态") from exc

    expected = hmac.new(secret.encode("utf-8"), encoded_body.encode("ascii"), sha256).hexdigest()
    # 使用 compare_digest 避免签名比较被计时侧信道推断。
    if not hmac.compare_digest(signature, expected):
        raise fail(401, "AUTH_TOKEN_EXPIRED", "登录已过期，请重新登录")

    padding = "=" * (-len(encoded_body) % 4)
    payload = json.loads(urlsafe_b64decode(f"{encoded_body}{padding}").decode("utf-8"))
    if int(payload.get("exp", 0)) < now_millis():
        raise fail(401, "AUTH_TOKEN_EXPIRED", "登录已过期，请重新登录")
    return payload


def build_token_pair(user_id: str, config: AppConfig) -> dict[str, object]:
    """生成访问令牌和刷新令牌。

    参数：
        user_id: 用户 ID。
        config: 应用配置。

    返回：
        dict[str, object]: 客户端登录态所需的 token 数据。
    """

    issued_at = now_millis()
    access_expires_at = issued_at + config.access_token_seconds * 1000
    refresh_expires_at = issued_at + config.refresh_token_seconds * 1000
    access_token = sign_token({"typ": "access", "sub": user_id, "iat": issued_at, "exp": access_expires_at}, config.token_secret)
    refresh_token = f"rt_{uuid4().hex}{uuid4().hex}"

    with transaction(config.database_path) as db:
        db.execute(
            """
            INSERT INTO refresh_tokens(token_hash, user_id, expires_at, revoked, created_at)
            VALUES (?, ?, ?, 0, ?)
            """,
            (hash_value(refresh_token, config.token_secret), user_id, refresh_expires_at, issued_at),
        )

    return {
        "accessToken": access_token,
        "refreshToken": refresh_token,
        "accessTokenExpiresAt": access_expires_at,
        "refreshTokenExpiresAt": refresh_expires_at,
    }


def require_user(config: AppConfig, authorization: str | None = Header(default=None)) -> dict[str, object]:
    """读取当前登录用户。

    参数：
        config: 应用配置。
        authorization: HTTP Authorization 头。

    返回：
        dict[str, object]: 当前用户数据库行的字典形式。
    """

    if not authorization or not authorization.startswith("Bearer "):
        raise fail(401, "AUTH_REQUIRED", "缺少登录态")

    payload = parse_token(authorization.removeprefix("Bearer ").strip(), config.token_secret)
    if payload.get("typ") != "access":
        raise fail(401, "AUTH_REQUIRED", "缺少有效登录态")

    with transaction(config.database_path) as db:
        row = db.execute("SELECT id, phone, created_at FROM users WHERE id = ?", (payload.get("sub"),)).fetchone()

    if row is None:
        raise fail(401, "AUTH_TOKEN_EXPIRED", "登录已过期，请重新登录")
    return dict(row)
