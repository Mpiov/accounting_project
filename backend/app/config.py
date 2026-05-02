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
        dev_sms_code: 开发阶段固定验证码，接入短信服务后删除该兜底。
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
        admin_password=os.getenv("AUTOBOOK_ADMIN_PASSWORD", ""),
        admin_session_seconds=int(os.getenv("AUTOBOOK_ADMIN_SESSION_SECONDS", "7200")),
        training_schedule_hour=int(os.getenv("AUTOBOOK_TRAINING_SCHEDULE_HOUR", "3")),
        training_min_new_samples=int(os.getenv("AUTOBOOK_TRAINING_MIN_NEW_SAMPLES", "100")),
        training_min_total_samples=int(os.getenv("AUTOBOOK_TRAINING_MIN_TOTAL_SAMPLES", "100")),
        training_min_per_class=int(os.getenv("AUTOBOOK_TRAINING_MIN_PER_CLASS", "10")),
        training_min_accuracy=float(os.getenv("AUTOBOOK_TRAINING_MIN_ACCURACY", "0.65")),
        training_max_false_positive_rate=float(os.getenv("AUTOBOOK_TRAINING_MAX_FALSE_POSITIVE_RATE", "0.25")),
    )
