"""机器学习训练环境自检命令。

该命令只检查依赖是否能在服务器容器里正常导入，不执行真实训练。
"""

from importlib import import_module
import json


def collect_package_versions() -> dict[str, str]:
    """收集训练依赖版本。

    返回：
        dict[str, str]: 包名到版本号的映射。
    """

    package_modules = {
        "numpy": "numpy",
        "scipy": "scipy",
        "scikit-learn": "sklearn",
        "joblib": "joblib",
        "jieba": "jieba",
    }
    versions: dict[str, str] = {}
    for package_name, module_name in package_modules.items():
        module = import_module(module_name)
        # 不同包暴露版本号的字段不完全一致，这里统一兜底成 unknown，避免自检脚本本身过于脆弱。
        versions[package_name] = str(getattr(module, "__version__", "unknown"))
    return versions


def run_smoke_training() -> dict[str, object]:
    """执行最小训练冒烟。

    返回：
        dict[str, object]: 训练结果摘要，用于确认 scikit-learn 基础链路可用。
    """

    from sklearn.feature_extraction.text import CountVectorizer
    from sklearn.linear_model import LogisticRegression
    from sklearn.pipeline import Pipeline

    # 用极小中文样本验证分词、向量化和逻辑回归训练能完整跑通。
    samples = [
        "微信支付 向便利店支付18.80元",
        "支付宝 收款到账32.00元",
        "银行短信 扣款100.00元",
        "系统通知 天气提醒",
    ]
    labels = [1, 1, 1, 0]
    pipeline = Pipeline(
        steps=[
            ("vectorizer", CountVectorizer()),
            ("classifier", LogisticRegression(max_iter=200)),
        ],
    )
    pipeline.fit(samples, labels)
    prediction = int(pipeline.predict(["微信支付成功 12.50元"])[0])
    return {
        "sampleCount": len(samples),
        "prediction": prediction,
    }


def main() -> None:
    """命令行入口。"""

    result = {
        "ok": True,
        "versions": collect_package_versions(),
        "smokeTraining": run_smoke_training(),
    }
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
