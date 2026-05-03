"""验证码发送通道。

本模块把验证码创建和真实发信解耦，便于当前用个人邮箱 SMTP 过渡，后续再切换到企业邮件推送服务。
"""

from email.message import EmailMessage
import smtplib
import ssl

from .config import AppConfig


class AuthCodeDeliveryError(RuntimeError):
    """验证码发送失败异常。

    参数：
        message: 可以安全返回给客户端的失败原因摘要。
    """


def deliver_auth_code(config: AppConfig, recipient_email: str, code: str, expires_in_seconds: int) -> None:
    """发送登录验证码。

    参数：
        config: 后端运行配置，包含发送通道和 SMTP 参数。
        recipient_email: 接收验证码的邮箱地址。
        code: 本次登录验证码，调用方负责生成和保存哈希。
        expires_in_seconds: 验证码有效期，单位秒。
    """

    if config.auth_code_delivery == "dev":
        # 开发模式不发送真实邮件，保留固定验证码便于无邮箱配置时联调。
        return
    if config.auth_code_delivery == "smtp":
        _deliver_by_smtp(config, recipient_email, code, expires_in_seconds)
        return
    raise AuthCodeDeliveryError("验证码发送通道配置错误")


def _deliver_by_smtp(config: AppConfig, recipient_email: str, code: str, expires_in_seconds: int) -> None:
    """通过 SMTP 发送验证码邮件。

    参数：
        config: SMTP 主机、端口、用户名、授权码和发件人配置。
        recipient_email: 收件邮箱。
        code: 本次验证码。
        expires_in_seconds: 验证码有效期，单位秒。
    """

    _validate_smtp_config(config)
    message = _build_auth_code_message(
        sender=config.smtp_from,
        recipient=recipient_email,
        code=code,
        expires_in_seconds=expires_in_seconds,
    )
    try:
        if config.smtp_use_ssl:
            context = ssl.create_default_context()
            with smtplib.SMTP_SSL(config.smtp_host, config.smtp_port, timeout=10, context=context) as smtp:
                smtp.login(config.smtp_username, config.smtp_password)
                smtp.send_message(message)
            return

        with smtplib.SMTP(config.smtp_host, config.smtp_port, timeout=10) as smtp:
            if config.smtp_use_starttls:
                smtp.starttls(context=ssl.create_default_context())
            smtp.login(config.smtp_username, config.smtp_password)
            smtp.send_message(message)
    except smtplib.SMTPException as exc:
        # 不把 SMTP 授权码、验证码或服务端原始细节返回给客户端。
        raise AuthCodeDeliveryError("验证码邮件发送失败，请稍后重试") from exc
    except OSError as exc:
        raise AuthCodeDeliveryError("验证码邮件服务暂时不可用") from exc


def _validate_smtp_config(config: AppConfig) -> None:
    """校验 SMTP 必要配置是否完整。

    参数：
        config: 当前后端配置对象。
    """

    required_values = [
        config.smtp_host,
        str(config.smtp_port),
        config.smtp_username,
        config.smtp_password,
        config.smtp_from,
    ]
    if any(not value for value in required_values):
        raise AuthCodeDeliveryError("验证码邮件服务未配置")


def _build_auth_code_message(
    sender: str,
    recipient: str,
    code: str,
    expires_in_seconds: int,
) -> EmailMessage:
    """构造验证码邮件内容。

    参数：
        sender: 发件邮箱地址。
        recipient: 收件邮箱地址。
        code: 本次验证码。
        expires_in_seconds: 验证码有效期，单位秒。
    """

    expires_minutes = max(1, expires_in_seconds // 60)
    message = EmailMessage()
    message["Subject"] = "AutoBook 登录验证码"
    message["From"] = sender
    message["To"] = recipient
    message.set_content(
        "\n".join(
            [
                "你的 AutoBook 登录验证码是：",
                "",
                code,
                "",
                f"验证码 {expires_minutes} 分钟内有效，请勿转发给他人。",
                "如果不是你本人操作，可以忽略这封邮件。",
            ],
        ),
    )
    return message
