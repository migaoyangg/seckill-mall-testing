#!/usr/bin/env python3
"""Create, finish, inspect and clean an isolated seckill load-test activity."""

from __future__ import annotations

import argparse
from datetime import datetime, timedelta
import json
import os
from pathlib import Path
import sys
from uuid import uuid4

import pymysql
import redis
import requests


def call(session: requests.Session, method: str, url: str, **kwargs):
    response = session.request(method, url, timeout=10, **kwargs)
    response.raise_for_status()
    body = response.json()
    if body.get("code") != 200:
        raise RuntimeError(f"API failed: {method} {url}, response={body}")
    return body.get("data")


def db_connection(args):
    return pymysql.connect(
        host=args.mysql_host,
        port=args.mysql_port,
        user=args.mysql_user,
        password=args.mysql_password,
        database=args.mysql_database,
        charset="utf8mb4",
        cursorclass=pymysql.cursors.DictCursor,
        autocommit=True,
    )


def prepare(args):
    session = requests.Session()
    login = call(session, "POST", f"{args.base_url}/user/login", json={
        "username": args.admin_username,
        "password": args.admin_password,
    })
    session.headers["Authorization"] = f"Bearer {login['token']}"

    name = f"JMeter秒杀基准-{uuid4().hex[:10]}"
    call(session, "POST", f"{args.base_url}/admin/product/add", json={
        "name": name,
        "description": "JMeter独立性能测试数据",
        "price": 100,
        "stock": args.stock,
        "categoryId": 2,
        "brand": "PERF-TEST",
        "mainImage": "",
        "detailImages": "[]",
    })

    with db_connection(args) as connection, connection.cursor() as cursor:
        cursor.execute(
            "SELECT id FROM product WHERE name=%s AND deleted=0 ORDER BY id DESC LIMIT 1",
            (name,),
        )
        product = cursor.fetchone()
    if product is None:
        raise RuntimeError("Created product was not found")

    now = datetime.now().replace(microsecond=0)
    call(session, "POST", f"{args.base_url}/admin/seckill/create", json={
        "productId": product["id"],
        "seckillPrice": 50,
        "stockCount": args.stock,
        "startTime": (now - timedelta(minutes=2)).isoformat(),
        "endTime": (now + timedelta(minutes=args.duration_minutes)).isoformat(),
        "limitPerUser": 1,
    })

    with db_connection(args) as connection, connection.cursor() as cursor:
        cursor.execute(
            "SELECT id FROM seckill_goods WHERE product_id=%s AND deleted=0",
            (product["id"],),
        )
        activity = cursor.fetchone()
    if activity is None:
        raise RuntimeError("Created seckill activity was not found")

    activity_id = int(activity["id"])
    call(session, "PUT", f"{args.base_url}/admin/seckill/status/{activity_id}/1")
    state = {
        "name": name,
        "product_id": int(product["id"]),
        "activity_id": activity_id,
        "initial_stock": args.stock,
        "created_at": now.isoformat(),
    }
    args.state.parent.mkdir(parents=True, exist_ok=True)
    args.state.write_text(json.dumps(state, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(state, ensure_ascii=False))


def inspect(args, finish=False):
    state = json.loads(args.state.read_text(encoding="utf-8"))
    session = requests.Session()
    if finish:
        login = call(session, "POST", f"{args.base_url}/user/login", json={
            "username": args.admin_username,
            "password": args.admin_password,
        })
        session.headers["Authorization"] = f"Bearer {login['token']}"
        call(
            session,
            "PUT",
            f"{args.base_url}/admin/seckill/status/{state['activity_id']}/2",
        )

    with db_connection(args) as connection, connection.cursor() as cursor:
        cursor.execute(
            """
            SELECT COUNT(*) AS order_count, COUNT(DISTINCT user_id) AS distinct_users
            FROM orders
            WHERE seckill_goods_id=%s AND is_seckill=1 AND deleted=0
            """,
            (state["activity_id"],),
        )
        orders = cursor.fetchone()
        cursor.execute(
            """
            SELECT COUNT(*) AS duplicate_users FROM (
                SELECT user_id FROM orders
                WHERE seckill_goods_id=%s AND is_seckill=1 AND deleted=0
                GROUP BY user_id HAVING COUNT(*) > 1
            ) duplicated
            """,
            (state["activity_id"],),
        )
        duplicates = cursor.fetchone()
        cursor.execute(
            "SELECT stock_count, status FROM seckill_goods WHERE id=%s",
            (state["activity_id"],),
        )
        activity = cursor.fetchone()

    redis_client = redis.Redis(
        host=args.redis_host,
        port=args.redis_port,
        password=args.redis_password,
        decode_responses=True,
    )
    redis_stock = redis_client.get(f"seckill:stock:{state['activity_id']}")
    queue_depth = redis_client.llen("seckill:order:queue")
    redis_client.close()

    remaining = int(activity["stock_count"])
    order_count = int(orders["order_count"])
    result = {
        **state,
        "order_count": order_count,
        "distinct_users": int(orders["distinct_users"]),
        "duplicate_users": int(duplicates["duplicate_users"]),
        "mysql_remaining_stock": remaining,
        "redis_remaining_stock": int(redis_stock) if redis_stock is not None else None,
        "queue_depth": int(queue_depth),
        "consistency_diff": state["initial_stock"] - order_count - remaining,
    }
    print(json.dumps(result, ensure_ascii=False))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=("prepare", "inspect", "finish"))
    parser.add_argument("--base-url", default="http://127.0.0.1:8080/api")
    parser.add_argument("--stock", type=int, default=100)
    parser.add_argument("--duration-minutes", type=int, default=30)
    parser.add_argument("--state", type=Path, default=Path("target/performance/activity.json"))
    parser.add_argument("--admin-username", default=os.getenv("API_TEST_ADMIN_USERNAME"))
    parser.add_argument("--admin-password", default=os.getenv("API_TEST_ADMIN_PASSWORD"))
    parser.add_argument("--mysql-host", default="127.0.0.1")
    parser.add_argument("--mysql-port", type=int, default=3306)
    parser.add_argument("--mysql-user", default="root")
    parser.add_argument("--mysql-password", default=os.getenv("API_TEST_MYSQL_PASSWORD"))
    parser.add_argument("--mysql-database", default="seckill_mall")
    parser.add_argument("--redis-host", default="127.0.0.1")
    parser.add_argument("--redis-port", type=int, default=6379)
    parser.add_argument("--redis-password", default=os.getenv("API_TEST_REDIS_PASSWORD"))
    args = parser.parse_args()

    required = {
        "--admin-username/API_TEST_ADMIN_USERNAME": args.admin_username,
        "--admin-password/API_TEST_ADMIN_PASSWORD": args.admin_password,
        "--mysql-password/API_TEST_MYSQL_PASSWORD": args.mysql_password,
    }
    missing = [name for name, value in required.items() if not value]
    if missing:
        parser.error("missing credentials: " + ", ".join(missing))

    if args.action == "prepare":
        prepare(args)
    else:
        inspect(args, finish=args.action == "finish")


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
