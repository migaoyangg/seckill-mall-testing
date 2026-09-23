"""MySQL 与 Redis 只读校验工具。"""

from __future__ import annotations

from typing import Any

import pymysql
from pymysql.cursors import DictCursor
import redis

from tests.api.framework.config import TestConfig


class MySqlProbe:
    """通过只读查询校验接口调用后的数据库状态。"""

    def __init__(self, config: TestConfig) -> None:
        self.connection = pymysql.connect(
            host=config.mysql_host,
            port=config.mysql_port,
            user=config.mysql_username,
            password=config.mysql_password,
            database=config.mysql_database,
            charset="utf8mb4",
            cursorclass=DictCursor,
            autocommit=True,
            connect_timeout=5,
        )

    def fetch_one(self, sql: str, params: tuple[Any, ...]) -> dict[str, Any] | None:
        with self.connection.cursor() as cursor:
            cursor.execute(sql, params)
            return cursor.fetchone()

    def order(self, order_no: str) -> dict[str, Any] | None:
        return self.fetch_one(
            """
            SELECT id, order_no, user_id, status, transaction_no, refund_time,
                   is_seckill, seckill_goods_id
            FROM orders
            WHERE order_no = %s AND deleted = 0
            """,
            (order_no,),
        )

    def product_stock(self, product_id: int) -> int:
        row = self.fetch_one(
            "SELECT stock FROM product WHERE id = %s AND deleted = 0",
            (product_id,),
        )
        assert row is not None, f"商品不存在: product_id={product_id}"
        return int(row["stock"])

    def product_by_name(self, name: str) -> dict[str, Any] | None:
        return self.fetch_one(
            """
            SELECT id, name, stock, status
            FROM product
            WHERE name = %s AND deleted = 0
            ORDER BY id DESC
            LIMIT 1
            """,
            (name,),
        )

    def seckill_by_product_id(self, product_id: int) -> dict[str, Any] | None:
        return self.fetch_one(
            """
            SELECT id, product_id, stock_count, status, start_time, end_time
            FROM seckill_goods
            WHERE product_id = %s AND deleted = 0
            """,
            (product_id,),
        )

    def close(self) -> None:
        self.connection.close()


class RedisProbe:
    """校验测试相关Redis Key，并清理由当前用例创建的精确 Key。"""

    def __init__(self, config: TestConfig) -> None:
        self.client = redis.Redis(
            host=config.redis_host,
            port=config.redis_port,
            db=config.redis_database,
            password=config.redis_password,
            decode_responses=True,
            socket_connect_timeout=5,
            socket_timeout=5,
        )
        self.client.ping()

    def exists(self, key: str) -> bool:
        return bool(self.client.exists(key))

    def get(self, key: str) -> str | None:
        return self.client.get(key)

    def delete_exact(self, *keys: str) -> None:
        """只删除调用方明确给出的测试 Key，禁止通配符扫描。"""
        if keys:
            self.client.delete(*keys)

    def close(self) -> None:
        self.client.close()
