"""后端运行配置。

本文件集中读取环境变量，避免接口代码里散落部署细节。
"""

from dataclasses import dataclass
import os


@dataclass(frozen=True)
class AppConfig:
    """应用配置对象。

    参数：
        database_path: SQLite 数据库文件路径，容器内默认挂载到持久化目录。
        token_secret: 令牌签名密钥，生产环境必须通过环境变量覆盖。
        access_token_seconds: 访问令牌有效期，单位秒。
        refresh_token_seconds: 刷新令牌有效期，单位秒。
        dev_sms_code: 开发阶段固定验证码，未启用真实邮件发送时使用。
        auth_code_delivery: 验证码发送通道，支持 dev 或 smtp。
        smtp_host: SMTP 服务器地址。
        smtp_port: SMTP 服务器端口。
        smtp_username: SMTP 登录用户名。
        smtp_password: SMTP 授权码或应用专用密码。
        smtp_from: 验证码邮件发件人地址。
        smtp_use_ssl: 是否使用 SMTP_SSL 直连。
        smtp_use_starttls: 是否在普通连接上升级 STARTTLS。
        admin_password: 管理后台登录密码，第一版只通过 SSH 隧道访问。
        admin_session_seconds: 管理后台会话有效期，单位秒。
        training_schedule_hour: 定时训练检查的北京时间小时。
        training_min_new_samples: 自动训练需要的新增有效样本数。
        training_min_total_samples: 自动训练需要的总有效样本数。
        training_min_per_class: 自动训练需要的每类最小样本数。
        training_min_accuracy: 自动发布所需的最低验证准确率。
        training_max_false_positive_rate: 自动发布允许的最高负样本误判率。
    """

    database_path: str
    token_secret: str
    access_token_seconds: int
    refresh_token_seconds: int
    dev_sms_code: str
    auth_code_delivery: str
    smtp_host: str
    smtp_port: int
    smtp_username: str
    smtp_password: str
    smtp_from: str
    smtp_use_ssl: bool
    smtp_use_starttls: bool
    admin_password: str
    admin_session_seconds: int
    training_schedule_hour: int
    training_min_new_samples: int
    training_min_total_samples: int
    training_min_per_class: int
    training_min_accuracy: float
    training_max_false_positive_rate: float


def load_config() -> AppConfig:
    """从环境变量加载配置。

    返回：
        AppConfig: 已带默认值的后端配置。
    """

    return AppConfig(
        database_path=os.getenv("AUTOBOOK_DB_PATH", "/data/autobook.sqlite3"),
        token_secret=os.getenv("AUTOBOOK_TOKEN_SECRET", "change-me-before-production"),
        access_token_seconds=int(os.getenv("AUTOBOOK_ACCESS_TOKEN_SECONDS", "7200")),
        refresh_token_seconds=int(os.getenv("AUTOBOOK_REFRESH_TOKEN_SECONDS", "2592000")),
        dev_sms_code=os.getenv("AUTOBOOK_DEV_SMS_CODE", "123456"),
        auth_code_delivery=os.getenv("AUTOBOOK_AUTH_CODE_DELIVERY", "dev").strip().lower(),
        smtp_host=os.getenv("AUTOBOOK_SMTP_HOST", ""),
        smtp_port=int(os.getenv("AUTOBOOK_SMTP_PORT", "465")),
        smtp_username=os.getenv("AUTOBOOK_SMTP_USERNAME", ""),
        smtp_password=os.getenv("AUTOBOOK_SMTP_PASSWORD", ""),
        smtp_from=os.getenv("AUTOBOOK_SMTP_FROM", os.getenv("AUTOBOOK_SMTP_USERNAME", "")),
        smtp_use_ssl=_read_bool("AUTOBOOK_SMTP_USE_SSL", default=True),
        smtp_use_starttls=_read_bool("AUTOBOOK_SMTP_USE_STARTTLS", default=False),
        admin_password=os.getenv("AUTOBOOK_ADMIN_PASSWORD", ""),
        admin_session_seconds=int(os.getenv("AUTOBOOK_ADMIN_SESSION_SECONDS", "7200")),
        training_schedule_hour=int(os.getenv("AUTOBOOK_TRAINING_SCHEDULE_HOUR", "3")),
        training_min_new_samples=int(os.getenv("AUTOBOOK_TRAINING_MIN_NEW_SAMPLES", "100")),
        training_min_total_samples=int(os.getenv("AUTOBOOK_TRAINING_MIN_TOTAL_SAMPLES", "100")),
        training_min_per_class=int(os.getenv("AUTOBOOK_TRAINING_MIN_PER_CLASS", "10")),
        training_min_accuracy=float(os.getenv("AUTOBOOK_TRAINING_MIN_ACCURACY", "0.65")),
        training_max_false_positive_rate=float(os.getenv("AUTOBOOK_TRAINING_MAX_FALSE_POSITIVE_RATE", "0.25")),
    )


def _read_bool(name: str, default: bool) -> bool:
    """读取布尔环境变量。

    参数：
        name: 环境变量名称。
        default: 环境变量未设置时使用的默认值。

    返回：
        bool: 解析后的布尔值，支持 1/true/yes/on。
    """

    raw_value = os.getenv(name)
    if raw_value is None:
        return default
    return raw_value.strip().lower() in {"1", "true", "yes", "on"}
