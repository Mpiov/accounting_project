"""SQLite 数据库访问层。

当前阶段使用标准库 sqlite3，减少小服务器部署负担；后续用户量上来后可以平滑迁移到 PostgreSQL。
"""

from collections.abc import Iterator
from contextlib import contextmanager
from pathlib import Path
import sqlite3


def _table_columns(connection: sqlite3.Connection, table_name: str) -> set[str]:
    """读取数据表已有字段名。

    参数：
        connection: 当前 SQLite 连接。
        table_name: 需要检查的数据表名。

    返回：
        set[str]: 该表当前存在的字段名集合。
    """

    rows = connection.execute(f"PRAGMA table_info({table_name})").fetchall()
    return {str(row["name"]) for row in rows}


def _ensure_column(
    connection: sqlite3.Connection,
    table_name: str,
    column_name: str,
    column_sql: str,
) -> None:
    """在旧数据库上补齐新增字段。

    参数：
        connection: 当前 SQLite 连接。
        table_name: 需要补字段的数据表名。
        column_name: 目标字段名。
        column_sql: `ALTER TABLE ADD COLUMN` 后面的字段定义。

    返回：
        None: 字段已存在时不做任何操作。
    """

    if column_name in _table_columns(connection, table_name):
        return
    # SQLite 对已有表加字段使用 ADD COLUMN，保证服务器上旧数据平滑升级。
    connection.execute(f"ALTER TABLE {table_name} ADD COLUMN {column_sql}")


def connect(database_path: str) -> sqlite3.Connection:
    """创建数据库连接。

    参数：
        database_path: SQLite 数据库文件路径。

    返回：
        sqlite3.Connection: 已开启 Row 行对象的连接。
    """

    Path(database_path).parent.mkdir(parents=True, exist_ok=True)
    connection = sqlite3.connect(database_path)
    connection.row_factory = sqlite3.Row
    # 外键约束默认关闭，这里显式开启，保证用户删除时关联数据行为一致。
    connection.execute("PRAGMA foreign_keys = ON")
    return connection


@contextmanager
def transaction(database_path: str) -> Iterator[sqlite3.Connection]:
    """开启一个自动提交或回滚的事务。

    参数：
        database_path: SQLite 数据库文件路径。

    返回：
        Iterator[sqlite3.Connection]: 事务内可使用的数据库连接。
    """

    connection = connect(database_path)
    try:
        yield connection
        connection.commit()
    except Exception:
        connection.rollback()
        raise
    finally:
        connection.close()


def init_database(database_path: str) -> None:
    """初始化数据库表结构。

    参数：
        database_path: SQLite 数据库文件路径。
    """

    with transaction(database_path) as db:
        # 用户表继续复用 phone 字段存放登录账号，当前新账号实际写入邮箱。
        db.executescript(
            """
            CREATE TABLE IF NOT EXISTS users (
                id TEXT PRIMARY KEY,
                phone TEXT NOT NULL UNIQUE,
                created_at INTEGER NOT NULL
            );

            CREATE TABLE IF NOT EXISTS sms_codes (
                phone TEXT NOT NULL,
                scene TEXT NOT NULL,
                code_hash TEXT NOT NULL,
                expires_at INTEGER NOT NULL,
                requested_at INTEGER NOT NULL,
                PRIMARY KEY (phone, scene)
            );

            CREATE TABLE IF NOT EXISTS refresh_tokens (
                token_hash TEXT PRIMARY KEY,
                user_id TEXT NOT NULL,
                expires_at INTEGER NOT NULL,
                revoked INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                FOREIGN KEY (user_id) REFERENCES users(id)
            );

            CREATE TABLE IF NOT EXISTS memberships (
                user_id TEXT PRIMARY KEY,
                tier TEXT NOT NULL,
                expires_at INTEGER,
                model_confidence_enabled INTEGER NOT NULL DEFAULT 0,
                cloud_learning_enabled INTEGER NOT NULL DEFAULT 0,
                updated_at INTEGER NOT NULL,
                FOREIGN KEY (user_id) REFERENCES users(id)
            );

            CREATE TABLE IF NOT EXISTS payment_orders (
                order_id TEXT PRIMARY KEY,
                user_id TEXT NOT NULL,
                plan TEXT NOT NULL,
                amount_cents INTEGER NOT NULL,
                payment_state TEXT NOT NULL,
                paid_at INTEGER,
                created_at INTEGER NOT NULL,
                FOREIGN KEY (user_id) REFERENCES users(id)
            );

            CREATE TABLE IF NOT EXISTS training_samples (
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
                updated_at INTEGER NOT NULL DEFAULT 0,
                UNIQUE (user_id, notification_id, sample_created_at),
                FOREIGN KEY (user_id) REFERENCES users(id)
            );

            CREATE TABLE IF NOT EXISTS model_versions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                model_version TEXT NOT NULL UNIQUE,
                model_scope TEXT NOT NULL,
                model_type TEXT NOT NULL,
                payload_json TEXT NOT NULL,
                trained_sample_count INTEGER NOT NULL DEFAULT 0,
                published_at INTEGER NOT NULL,
                valid_until INTEGER,
                min_app_version TEXT NOT NULL DEFAULT '1.0.0',
                is_active INTEGER NOT NULL DEFAULT 0
            );

            CREATE TABLE IF NOT EXISTS training_jobs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                job_id TEXT NOT NULL UNIQUE,
                trigger_type TEXT NOT NULL,
                state TEXT NOT NULL,
                started_at INTEGER NOT NULL,
                finished_at INTEGER,
                sample_count INTEGER NOT NULL DEFAULT 0,
                labeled_sample_count INTEGER NOT NULL DEFAULT 0,
                positive_count INTEGER NOT NULL DEFAULT 0,
                negative_count INTEGER NOT NULL DEFAULT 0,
                new_sample_count INTEGER NOT NULL DEFAULT 0,
                model_version TEXT,
                metrics_json TEXT,
                message TEXT
            );

            CREATE INDEX IF NOT EXISTS idx_model_versions_active
            ON model_versions(model_scope, is_active, published_at);

            CREATE INDEX IF NOT EXISTS idx_training_jobs_started
            ON training_jobs(started_at DESC);
            """
        )
        _ensure_column(
            db,
            table_name="training_samples",
            column_name="contribution_scope",
            column_sql="contribution_scope TEXT NOT NULL DEFAULT 'GLOBAL'",
        )
        _ensure_column(
            db,
            table_name="training_samples",
            column_name="device_id",
            column_sql="device_id TEXT",
        )
        _ensure_column(
            db,
            table_name="training_samples",
            column_name="app_version",
            column_sql="app_version TEXT",
        )
        _ensure_column(
            db,
            table_name="training_samples",
            column_name="sample_local_id",
            column_sql="sample_local_id INTEGER",
        )
        _ensure_column(
            db,
            table_name="training_samples",
            column_name="updated_at",
            column_sql="updated_at INTEGER NOT NULL DEFAULT 0",
        )
        # 旧库补齐 contribution_scope 后再创建索引，避免 SQLite 在字段不存在时中断迁移。
        db.execute(
            """
            CREATE INDEX IF NOT EXISTS idx_training_samples_global
            ON training_samples(contribution_scope, inserted_at)
            """
        )
        db.execute(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS idx_training_samples_user_local
            ON training_samples(user_id, sample_local_id)
            WHERE sample_local_id IS NOT NULL
            """
        )
