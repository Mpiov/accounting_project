"""训练样本标签白名单。

本模块集中定义真正允许进入全局模型训练的数据标签，避免上传接口、线性模型训练、
CRF 弱标注和管理后台各自维护一份名单后再次出现口径不一致。
"""

TRAINABLE_POSITIVE_LABELS = {
    # 用户明确确认候选无误后，才能作为正样本训练模型。
    "USER_CONFIRMED_POSITIVE",
    # 用户修改字段后确认，仍说明这条通知是有效账单，因此作为正样本。
    "USER_CONFIRMED_EDITED",
    # 已入账记录被用户编辑，表示原通知确实对应账单，只是字段可能被修正。
    "USER_EDITED_LEDGER",
}

TRAINABLE_NEGATIVE_LABELS = {
    # 用户在待确认页明确选择不入账，才是可靠负样本。
    "USER_DISMISSED_NEGATIVE",
    # 用户明确标记误识别，是最强的负样本来源。
    "USER_MARKED_FALSE_POSITIVE",
    # 用户删除自动入账或确认后的账单，说明该样本不应继续被模型信任。
    "USER_DELETED_LEDGER",
}

TRAINABLE_FEEDBACK_LABELS = TRAINABLE_POSITIVE_LABELS | TRAINABLE_NEGATIVE_LABELS


def normalize_feedback_label(label: object) -> str:
    """归一化反馈标签。

    参数：
        label: 客户端或后台传来的原始标签值。

    返回：
        str: 去除空白并转成大写后的稳定标签。
    """

    return str(label or "").strip().upper()


def is_trainable_feedback_label(label: object) -> bool:
    """判断标签是否允许进入全局训练样本池。

    参数：
        label: 客户端或后台传来的原始标签值。

    返回：
        bool: 只有 6 类用户明确反馈标签返回 True。
    """

    return normalize_feedback_label(label) in TRAINABLE_FEEDBACK_LABELS
