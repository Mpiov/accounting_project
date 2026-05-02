"""全局模型定时训练调度器。

该模块作为独立 Docker 容器运行，每天固定时间触发一次训练检查。
真正是否训练和发布由 `training_job` 的样本数量、标签分布和验证指标共同决定。
"""

from datetime import datetime, timedelta
import time
from zoneinfo import ZoneInfo

from .config import load_config
from .training_job import TrainingPolicy
from .training_job import run_global_training

SCHEDULE_TIMEZONE = ZoneInfo("Asia/Shanghai")


def seconds_until_next_run(schedule_hour: int) -> float:
    """计算距离下一次训练检查还有多少秒。

    参数：
        schedule_hour: 每天触发训练检查的小时，使用北京时间 0-23。

    返回：
        float: 等待秒数。
    """

    now = datetime.now(SCHEDULE_TIMEZONE)
    target = now.replace(hour=schedule_hour, minute=0, second=0, microsecond=0)
    if target <= now:
        target += timedelta(days=1)
    return max(1.0, (target - now).total_seconds())


def run_once() -> dict[str, object]:
    """执行一次默认策略训练检查。

    返回：
        dict[str, object]: 训练任务结果摘要。
    """

    config = load_config()
    return run_global_training(
        database_path=config.database_path,
        trigger_type="scheduled",
        policy=TrainingPolicy(
            min_new_samples=config.training_min_new_samples,
            min_total_samples=config.training_min_total_samples,
            min_per_class=config.training_min_per_class,
            min_accuracy=config.training_min_accuracy,
            max_false_positive_rate=config.training_max_false_positive_rate,
        ),
    )


def main() -> None:
    """调度器入口。

    返回：
        None: 该函数会在容器内常驻运行。
    """

    config = load_config()
    while True:
        delay_seconds = seconds_until_next_run(config.training_schedule_hour)
        print(f"下一次全局模型训练检查将在 {delay_seconds:.0f} 秒后触发", flush=True)
        time.sleep(delay_seconds)
        result = run_once()
        print(f"全局模型训练检查完成：{result}", flush=True)


if __name__ == "__main__":
    main()
