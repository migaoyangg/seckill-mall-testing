#!/usr/bin/env python3
"""
Generate JMeter token CSV for seckill load testing.

The seckill JMeter plan reads a one-column CSV:

token
Bearer <jwt>

This script registers test users if needed, logs them in, and writes that file.
"""

from __future__ import annotations

import argparse
import csv
import json
import os
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any


SUCCESS_CODE = 200
USERNAME_EXISTS_CODE = 1007
PHONE_EXISTS_CODE = 1008


class ApiError(Exception):
    pass


def request_json(base_url: str, path: str, body: dict[str, Any], timeout: float) -> dict[str, Any]:
    data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(
        base_url.rstrip("/") + path,
        data=data,
        headers={
            "Accept": "application/json",
            "Content-Type": "application/json",
        },
        method="POST",
    )

    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read().decode("utf-8")
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode("utf-8", errors="replace")
    except urllib.error.URLError as exc:
        raise ApiError(f"Cannot connect to {base_url}: {exc.reason}") from exc

    try:
        parsed = json.loads(raw)
    except json.JSONDecodeError as exc:
        raise ApiError(f"Non-JSON response from {path}: {raw[:200]}") from exc

    if not isinstance(parsed, dict):
        raise ApiError(f"Unexpected response from {path}: {parsed}")
    return parsed


def register_user(base_url: str, username: str, password: str, phone: str, timeout: float) -> None:
    response = request_json(
        base_url,
        "/user/register",
        {
            "username": username,
            "password": password,
            "nickname": f"压测用户{username[-4:]}",
            "phone": phone,
        },
        timeout,
    )
    code = response.get("code")
    if code in (SUCCESS_CODE, USERNAME_EXISTS_CODE, PHONE_EXISTS_CODE):
        return
    raise ApiError(f"Register failed for {username}: {response}")


def login_user(base_url: str, username: str, password: str, timeout: float) -> str:
    response = request_json(
        base_url,
        "/user/login",
        {
            "username": username,
            "password": password,
        },
        timeout,
    )
    if response.get("code") != SUCCESS_CODE:
        raise ApiError(f"Login failed for {username}: {response}")

    data = response.get("data")
    if not isinstance(data, dict) or not data.get("token"):
        raise ApiError(f"Login response has no data.token for {username}: {response}")
    return str(data["token"])


def phone_for(index: int) -> str:
    return f"139{index:08d}"


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate JMeter tokens.csv for seckill load testing.")
    parser.add_argument("--base-url", default="http://localhost:8080/api", help="API base URL.")
    parser.add_argument("--count", type=int, default=100, help="Number of users/tokens to generate.")
    parser.add_argument("--start", type=int, default=1, help="Start index for generated users.")
    parser.add_argument("--username-prefix", default="jmeter", help="Generated username prefix.")
    parser.add_argument(
        "--password",
        default=os.getenv("JMETER_USER_PASSWORD"),
        help="Password for generated users; prefer JMETER_USER_PASSWORD.",
    )
    parser.add_argument("--output", default="scripts/jmeter/tokens.csv", help="Output token CSV path.")
    parser.add_argument("--users-output", default="scripts/jmeter/users.csv", help="Output user CSV path.")
    parser.add_argument("--timeout", type=float, default=8.0, help="HTTP timeout in seconds.")
    parser.add_argument("--sleep", type=float, default=0.0, help="Sleep seconds between users.")
    args = parser.parse_args()

    if args.count <= 0:
        raise ApiError("--count must be greater than 0")
    if args.start <= 0:
        raise ApiError("--start must be greater than 0")
    if not args.password:
        raise ApiError("--password or JMETER_USER_PASSWORD is required")

    token_rows: list[list[str]] = [["token"]]
    user_rows: list[list[str]] = [["username", "password", "phone"]]

    for offset in range(args.count):
        index = args.start + offset
        username = f"{args.username_prefix}{index:06d}"
        phone = phone_for(index)

        register_user(args.base_url, username, args.password, phone, args.timeout)
        token = login_user(args.base_url, username, args.password, args.timeout)

        token_rows.append([f"Bearer {token}"])
        user_rows.append([username, args.password, phone])

        if (offset + 1) % 50 == 0 or offset + 1 == args.count:
            print(f"generated {offset + 1}/{args.count} tokens")
        if args.sleep > 0:
            time.sleep(args.sleep)

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", newline="", encoding="utf-8") as fp:
        writer = csv.writer(fp)
        writer.writerows(token_rows)

    users_output = Path(args.users_output)
    users_output.parent.mkdir(parents=True, exist_ok=True)
    with users_output.open("w", newline="", encoding="utf-8") as fp:
        writer = csv.writer(fp)
        writer.writerows(user_rows)

    print(f"wrote {args.count} tokens to {output}")
    print(f"wrote {args.count} users to {users_output}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except ApiError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
