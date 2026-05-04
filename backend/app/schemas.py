"""请求模型定义。

Pydantic 模型集中放置，保持路由函数更短、更容易和接口契约对齐。
"""

from typing import Any, Literal

from pydantic import BaseModel, Field


class SmsCodeRequest(BaseModel):
    """登录验证码请求。

    参数：
        phone: 登录邮箱；字段名暂沿用旧客户端契约，后续统一重命名为 email。
        scene: 验证码场景，当前只支持 login。
    """

    phone: str = Field(min_length=5, max_length=254)
    scene: Literal["login"] = "login"


class LoginRequest(BaseModel):
    """邮箱验证码登录或注册请求。

    参数：
        phone: 登录邮箱；字段名暂沿用旧客户端契约，后续统一重命名为 email。
        code: 用户输入的验证码。
        deviceId: 客户端设备标识。
        appVersion: 客户端版本号。
    """

    phone: str = Field(min_length=5, max_length=254)
    code: str = Field(min_length=4, max_length=12)
    deviceId: str | None = None
    appVersion: str | None = None


class RefreshRequest(BaseModel):
    """刷新令牌请求。

    参数：
        refreshToken: 登录接口返回的刷新令牌。
    """

    refreshToken: str = Field(min_length=16)


class CreateOrderRequest(BaseModel):
    """创建会员订单请求。

    参数：
        plan: 会员套餐，目前正式开放的是新发布限时年卡，其它套餐先保留协议占位。
        payChannel: 支付渠道。
    """

    plan: Literal["TWO_YEAR", "YEARLY", "QUARTERLY", "MONTHLY"] = "YEARLY"
    payChannel: Literal["WECHAT_APP"] = "WECHAT_APP"


class TrainingSample(BaseModel):
    """全局训练样本原始结构。

    参数：
        sampleLocalId: 客户端本地训练样本主键，用于后续纠错样本覆盖旧版本。
        notificationId: 客户端通知记录 ID。
        createdAt: 样本创建时间。
        其它字段: 通知原文、规则解析结果和用户反馈等训练字段，服务端按原样写入全局样本池。
    """

    sampleLocalId: int | None = None
    notificationId: int
    createdAt: int

    model_config = {"extra": "allow"}


class UploadTrainingSamplesRequest(BaseModel):
    """上传全局训练样本请求。

    参数：
        clientBatchId: 客户端批次 ID。
        deviceId: 客户端设备标识。
        appVersion: 客户端版本。
        samples: 样本数组，服务器会统一汇总训练全局模型。
    """

    clientBatchId: str = Field(min_length=1, max_length=128)
    deviceId: str | None = None
    appVersion: str | None = None
    samples: list[TrainingSample] = Field(default_factory=list, max_length=200)


JsonDict = dict[str, Any]
