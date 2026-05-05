"""服务器本地管理命令。

当前只提供会员状态的临时管理能力，不暴露公网 HTTP 管理接口。
"""

from argparse import ArgumentParser, Namespace
import json

from .admin_service import get_member_status_by_phone
from .admin_service import grant_member as grant_member_status
from .admin_service import normalize_phone
from .admin_service import revoke_member as revoke_member_status
from .config import AppConfig
from .config import load_config
from .global_model_service import count_global_training_samples
from .global_model_service import get_active_global_model
from .training_job import TrainingPolicy
from .training_job import list_training_jobs
from .training_job import run_global_training


def grant_member(args: Namespace) -> dict[str, object]:
    """开通临时会员。

    参数：
        args: argparse 解析后的命令行参数。

    返回：
        dict[str, object]: 更新后的会员状态摘要。
    """

    config = load_config()
    return grant_member_status(
        database_path=config.database_path,
        phone=args.phone,
        days=args.days,
        model_confidence_enabled=not args.disable_model_confidence,
        cloud_learning_enabled=not args.disable_cloud_learning,
    )


def revoke_member(args: Namespace) -> dict[str, object]:
    """撤销会员。

    参数：
        args: argparse 解析后的命令行参数。

    返回：
        dict[str, object]: 更新后的会员状态摘要。
    """

    config = load_config()
    return revoke_member_status(database_path=config.database_path, phone=args.phone)


def show_member(args: Namespace) -> dict[str, object]:
    """查看会员状态。

    参数：
        args: argparse 解析后的命令行参数。

    返回：
        dict[str, object]: 当前会员状态摘要。
    """

    config = load_config()
    return get_member_status_by_phone(config.database_path, normalize_phone(args.phone))


def show_global_model(_: Namespace) -> dict[str, object]:
    """查看当前全局模型。

    参数：
        _: argparse 解析后的命令行参数，当前命令不需要额外字段。

    返回：
        dict[str, object]: 当前激活的全局模型摘要。
    """

    config = load_config()
    model = get_active_global_model(config.database_path)
    model["globalTrainingSampleCount"] = count_global_training_samples(config.database_path)
    return model


def run_training(args: Namespace) -> dict[str, object]:
    """手动执行一次全局训练检查。

    参数：
        args: argparse 解析后的命令行参数。

    返回：
        dict[str, object]: 训练任务结果摘要。
    """

    config = load_config()
    return run_global_training(
        database_path=config.database_path,
        trigger_type=args.trigger,
        policy=build_training_policy(args=args, config=config),
    )


def build_training_policy(args: Namespace, config: AppConfig) -> TrainingPolicy:
    """合成命令行训练策略。

    参数：
        args: argparse 解析后的命令行参数，允许管理员临时覆盖训练门槛。
        config: 当前服务器环境配置，作为 CLI 未传参时的默认来源。
    返回：
        TrainingPolicy: 传给全局训练任务的最终策略。
    """

    return TrainingPolicy(
        # CLI 不传参数时必须读取环境配置，保证和定时训练、管理后台手动训练使用同一套门槛。
        min_new_samples=args.min_new_samples
        if args.min_new_samples is not None
        else config.training_min_new_samples,
        min_total_samples=args.min_total_samples
        if args.min_total_samples is not None
        else config.training_min_total_samples,
        min_per_class=args.min_per_class
        if args.min_per_class is not None
        else config.training_min_per_class,
        min_accuracy=args.min_accuracy
        if args.min_accuracy is not None
        else config.training_min_accuracy,
        max_false_positive_rate=args.max_false_positive_rate
        if args.max_false_positive_rate is not None
        else config.training_max_false_positive_rate,
        force=args.force,
    )


def show_training_jobs(args: Namespace) -> dict[str, object]:
    """查看最近训练任务。

    参数：
        args: argparse 解析后的命令行参数。

    返回：
        dict[str, object]: 最近训练任务列表。
    """

    config = load_config()
    return {"jobs": list_training_jobs(config.database_path, limit=args.limit)}


def build_parser() -> ArgumentParser:
    """构建命令行解析器。

    返回：
        ArgumentParser: 已注册子命令的解析器。
    """

    parser = ArgumentParser(description="AutoBook 服务器本地管理工具")
    subparsers = parser.add_subparsers(dest="command", required=True)

    grant_parser = subparsers.add_parser("grant-member", help="按邮箱或旧手机号临时开通会员")
    grant_parser.add_argument("--phone", required=True, help="邮箱或旧手机号，参数名暂沿用 --phone")
    grant_parser.add_argument("--days", type=int, default=30, help="会员有效天数，默认 30 天")
    grant_parser.add_argument(
        "--disable-model-confidence",
        action="store_true",
        help="只开会员身份，不开启机器学习置信度",
    )
    grant_parser.add_argument(
        "--disable-cloud-learning",
        action="store_true",
        help="只开会员身份，不允许上传样本和拉取模型",
    )
    grant_parser.set_defaults(handler=grant_member)

    revoke_parser = subparsers.add_parser("revoke-member", help="按邮箱或旧手机号撤销会员")
    revoke_parser.add_argument("--phone", required=True, help="邮箱或旧手机号，参数名暂沿用 --phone")
    revoke_parser.set_defaults(handler=revoke_member)

    show_parser = subparsers.add_parser("show-member", help="按邮箱或旧手机号查看会员状态")
    show_parser.add_argument("--phone", required=True, help="邮箱或旧手机号，参数名暂沿用 --phone")
    show_parser.set_defaults(handler=show_member)

    model_parser = subparsers.add_parser("show-global-model", help="查看当前发布给会员客户端的全局模型")
    model_parser.set_defaults(handler=show_global_model)

    training_parser = subparsers.add_parser("run-global-training", help="执行一次全局模型训练检查")
    training_parser.add_argument("--trigger", default="manual", help="触发来源，默认 manual")
    training_parser.add_argument("--force", action="store_true", help="忽略数量和质量门槛；正负样本必须同时存在")
    training_parser.add_argument("--min-new-samples", type=int, default=None, help="自动训练所需新增有效样本数；不填则读取服务器配置")
    training_parser.add_argument("--min-total-samples", type=int, default=None, help="自动训练所需总有效样本数；不填则读取服务器配置")
    training_parser.add_argument("--min-per-class", type=int, default=None, help="自动训练所需每类最小样本数；不填则读取服务器配置")
    training_parser.add_argument("--min-accuracy", type=float, default=None, help="自动发布所需最低验证准确率；不填则读取服务器配置")
    training_parser.add_argument(
        "--max-false-positive-rate",
        type=float,
        default=None,
        help="自动发布允许的最高负样本误判率；不填则读取服务器配置",
    )
    training_parser.set_defaults(handler=run_training)

    jobs_parser = subparsers.add_parser("show-training-jobs", help="查看最近全局训练任务")
    jobs_parser.add_argument("--limit", type=int, default=10, help="返回任务数量")
    jobs_parser.set_defaults(handler=show_training_jobs)

    return parser


def main() -> None:
    """命令行入口。"""

    parser = build_parser()
    args = parser.parse_args()
    result = args.handler(args)
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
