"""训练标签过滤测试。"""

import json
import sys
from pathlib import Path
import sqlite3
import unittest

ROOT = Path(__file__).resolve().parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from app.training_job import load_labeled_examples
from app.training_job import normalize_selected_sample_ids
from app.training_job import resolve_training_label
from app.training_job import run_global_training


class TrainingLabelTest(unittest.TestCase):
    """验证只有用户明确反馈的 6 类标签能进入模型训练。"""

    def test_unreviewed_system_labels_do_not_resolve_to_training_label(self) -> None:
        """确认自动入账未复核和已过滤未复核都不是训练标签。"""

        self.assertIsNone(resolve_training_label({"feedbackLabel": "AUTO_LEDGER_UNREVIEWED"}))
        self.assertIsNone(resolve_training_label({"feedbackLabel": "FILTERED_UNREVIEWED"}))
        self.assertEqual(resolve_training_label({"feedbackLabel": "USER_CONFIRMED_POSITIVE"}), 1)
        self.assertEqual(resolve_training_label({"feedbackLabel": "USER_MARKED_FALSE_POSITIVE"}), 0)

    def test_load_labeled_examples_uses_only_six_user_feedback_labels(self) -> None:
        """确认训练集加载时会跳过未复核系统标签，只保留 6 类用户反馈样本。"""

        database_path = str(ROOT / "_tmp_training_label_test.sqlite3")
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
        labels = [
            "AUTO_LEDGER_UNREVIEWED",
            "FILTERED_UNREVIEWED",
            "USER_CONFIRMED_POSITIVE",
            "USER_CONFIRMED_EDITED",
            "USER_EDITED_LEDGER",
            "USER_DISMISSED_NEGATIVE",
            "USER_MARKED_FALSE_POSITIVE",
            "USER_DELETED_LEDGER",
        ]
        for index, label in enumerate(labels, start=1):
            payload = {
                "notificationId": index,
                "createdAt": 1_710_000_000_000 + index,
                "feedbackLabel": label,
                "features": {"sourceClass": "TRUSTED_PAYMENT"},
                "rawText": f"测试样本 {label}",
            }
            conn.execute(
                """
                INSERT INTO training_samples(
                    user_id, contribution_scope, client_batch_id, notification_id,
                    sample_created_at, raw_json, inserted_at, updated_at
                )
                VALUES (?, 'GLOBAL', 'batch', ?, ?, ?, ?, ?)
                """,
                (
                    "user-1",
                    index,
                    1_710_000_000_000 + index,
                    json.dumps(payload, ensure_ascii=False),
                    1_710_000_000_000 + index,
                    1_710_000_000_000 + index,
                ),
            )
        conn.commit()
        conn.close()

        examples = load_labeled_examples(database_path)

        self.assertEqual(len(examples), 6)
        self.assertEqual(sum(1 for example in examples if example.label == 1), 3)
        self.assertEqual(sum(1 for example in examples if example.label == 0), 3)

        selected_examples = load_labeled_examples(database_path, selected_sample_ids=[3, 7])
        self.assertEqual([example.sample_id for example in selected_examples], [3, 7])

    def test_run_global_training_requires_selected_samples(self) -> None:
        """确认没有人工选择样本时，训练任务只跳过，不会发布模型。"""

        database_path = str(ROOT / "_tmp_training_requires_selection.sqlite3")
        Path(database_path).unlink(missing_ok=True)
        result = run_global_training(database_path=database_path, trigger_type="test")

        self.assertEqual(result["state"], "SKIPPED")
        self.assertIn("人工选择样本", str(result["message"]))

    def test_normalize_selected_sample_ids_keeps_positive_unique_ids(self) -> None:
        """确认样本 ID 归一化会去掉非法值和重复值。"""

        self.assertEqual(normalize_selected_sample_ids([3, 0, -1, 3, "8"]), [3, 8])


if __name__ == "__main__":
    unittest.main()
