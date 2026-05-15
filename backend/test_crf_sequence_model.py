"""CRF 序列抽取骨架测试。

这些测试只覆盖弱标注、特征模板和训练包草案，不依赖 python-crfsuite。
"""

import sys
from pathlib import Path
import sqlite3
import unittest

ROOT = Path(__file__).resolve().parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from app.crf_sequence_model import AMOUNT_TAG
from app.crf_sequence_model import CRF_MODEL_SCOPE
from app.crf_sequence_model import CRF_MODEL_TYPE
from app.crf_sequence_model import SCENE_TAG
from app.crf_sequence_model import SequenceLabelExample
from app.crf_sequence_model import SequenceTrainingPolicy
from app.crf_sequence_model import TAIL_NO_TAG
from app.crf_sequence_model import TIME_TAG
from app.crf_sequence_model import build_feature_template
from app.crf_sequence_model import build_crf_training_preview
from app.crf_sequence_model import build_sequence_model_payload
from app.crf_sequence_model import build_token_features
from app.crf_sequence_model import split_examples
from app.crf_sequence_model import weak_label_sample


class CrfSequenceModelTest(unittest.TestCase):
    """CRF 骨架基础行为测试。"""

    def test_weak_label_sample_marks_amount_time_tail_and_scene(self) -> None:
        """确认弱标注能抓到主金额、尾号、时间和动账场景。"""

        sample = weak_label_sample(
            sample_id=1,
            raw_sample={
                "rawTitle": "交易提醒",
                "rawText": "尾号3026卡5月14日17:30POS支出(无卡支付)17.20元。",
                "feedbackLabel": "USER_CONFIRMED_POSITIVE",
                "parsed": {"amount": 17.2, "direction": "EXPENSE"},
            },
        )
        self.assertIsNotNone(sample)
        assert sample is not None
        self.assertEqual(len(sample.text), len(sample.labels))
        self.assertIn(f"B-{AMOUNT_TAG}", sample.labels)
        self.assertIn(f"B-{TIME_TAG}", sample.labels)
        self.assertIn(f"B-{TAIL_NO_TAG}", sample.labels)
        self.assertIn(f"B-{SCENE_TAG}", sample.labels)

    def test_feature_template_contains_required_labels(self) -> None:
        """确认特征模板里包含第一版必需标签。"""

        template = build_feature_template()
        self.assertEqual(template["labelScheme"], "BIO")
        self.assertIn(f"B-{AMOUNT_TAG}", template["labels"])
        self.assertIn(f"B-{TIME_TAG}", template["labels"])
        self.assertIn(f"B-{TAIL_NO_TAG}", template["labels"])

    def test_payload_preview_has_scope_and_type(self) -> None:
        """确认模型包草案字段符合独立 CRF 作用域。"""

        examples = [
            SequenceLabelExample(sample_id=1, text="abc", labels=["O", "O", "O"], feedback_label="USER_DISMISSED_NEGATIVE")
        ]
        payload = build_sequence_model_payload(examples=examples, publishable=False)
        self.assertEqual(payload["modelScope"], CRF_MODEL_SCOPE)
        self.assertEqual(payload["modelType"], CRF_MODEL_TYPE)
        self.assertFalse(payload["enabled"])

    def test_split_examples_keeps_validation_set_non_empty(self) -> None:
        """确认样本切分不会把验证集切空。"""

        examples = [SequenceLabelExample(sample_id=i, text="abc", labels=["O", "O", "O"], feedback_label="USER_CONFIRMED_POSITIVE") for i in range(10)]
        train_examples, validation_examples = split_examples(examples)
        self.assertGreater(len(train_examples), 0)
        self.assertGreater(len(validation_examples), 0)

    def test_token_features_contains_time_and_amount_flags(self) -> None:
        """确认单字特征里保留时间和金额附近信号。"""

        features = build_token_features("尾号3026卡5月14日17:30POS支出17.20元", 20, package_name="com.example", source_app="BANK_SMS")
        self.assertIn("char", features)
        self.assertIn("near_amount_unit", features)
        self.assertIn("is_time_unit", features)

    def test_preview_uses_training_samples_table(self) -> None:
        """确认预览函数能从服务器样本表读 raw_json。"""

        database_path = str(ROOT / "_tmp_crf_test.sqlite3")
        Path(database_path).unlink(missing_ok=True)
        conn = sqlite3.connect(database_path)
        conn.execute(
            """
            CREATE TABLE training_samples (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id TEXT NOT NULL,
                contribution_scope TEXT NOT NULL DEFAULT 'GLOBAL',
                client_batch_id TEXT NOT NULL,
                device_id TEXT,
                app_version TEXT,
                sample_local_id INTEGER,
                notification_id INTEGER NOT NULL,
                sample_created_at INTEGER NOT NULL,
                raw_json TEXT NOT NULL,
                inserted_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL DEFAULT 0
            )
            """
        )
        conn.execute(
            """
            INSERT INTO training_samples(
                user_id, contribution_scope, client_batch_id, device_id, app_version,
                sample_local_id, notification_id, sample_created_at, raw_json, inserted_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                "user-1",
                "GLOBAL",
                "batch-1",
                "device-1",
                "0.1.4",
                1,
                1001,
                1710000000000,
                '{"notificationId":1001,"createdAt":1710000000000,"feedbackLabel":"USER_CONFIRMED_POSITIVE","rawTitle":"交易提醒","rawText":"尾号3026卡5月14日17:30POS支出17.20元","parsed":{"amount":17.2,"direction":"EXPENSE"}}',
                1710000000000,
                0,
            ),
        )
        conn.commit()
        conn.close()

        preview = build_crf_training_preview(database_path=database_path, limit=50)
        self.assertEqual(preview["modelType"], CRF_MODEL_TYPE)
        self.assertGreaterEqual(preview["exampleCount"], 1)
        self.assertIn("AMOUNT", preview["entityCounts"])



if __name__ == "__main__":
    unittest.main()
