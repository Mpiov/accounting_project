"""全局模型训练调度器。

自动训练已关闭。该模块保留为容器兼容入口，但不会定时触发模型训练或发布。
模型只能由管理员在后台样本页人工选择样本后发布。
"""

import time

SLEEP_SECONDS = 24 * 60 * 60


def main() -> None:
    """兼容旧部署的常驻入口。

    返回：
        None: 该函数只保持容器存活，不会触发训练。
    """

    while True:
        print("自动训练已关闭：请在管理后台样本页人工选择样本后发布模型", flush=True)
        time.sleep(SLEEP_SECONDS)


if __name__ == "__main__":
    main()
