from __future__ import annotations

import logging

import requests


LOGGER = logging.getLogger(__name__)


class TestDataCleaner:
    """Uses public business APIs to find and cancel orders created by a UI test."""

    def __init__(self, base_url: str, username: str, password: str, timeout: float = 8) -> None:
        self.base_url = base_url.rstrip("/")
        self.username = username
        self.password = password
        self.timeout = timeout

    def order_numbers(self) -> set[str]:
        with self._authenticated_session() as session:
            response = session.get(
                f"{self.base_url}/order/list",
                params={"page": 1, "size": 100},
                timeout=self.timeout,
            )
            body = self._success(response, "list orders")
            return {
                record["orderNo"]
                for record in body["data"]["records"]
                if record.get("orderNo")
            }

    def cancel_new_orders(self, previous: set[str]) -> list[str]:
        cancelled: list[str] = []
        with self._authenticated_session() as session:
            response = session.get(
                f"{self.base_url}/order/list",
                params={"page": 1, "size": 100},
                timeout=self.timeout,
            )
            records = self._success(response, "list orders for cleanup")["data"]["records"]
            for record in records:
                order_no = record.get("orderNo")
                if not order_no or order_no in previous or record.get("status") != 0:
                    continue
                result = session.post(
                    f"{self.base_url}/order/cancel/{order_no}", timeout=self.timeout
                )
                self._success(result, f"cancel order {order_no}")
                cancelled.append(order_no)
                LOGGER.info("cancelled UI test order: %s", order_no)
        return cancelled

    def _authenticated_session(self) -> requests.Session:
        session = requests.Session()
        response = session.post(
            f"{self.base_url}/user/login",
            json={"username": self.username, "password": self.password},
            timeout=self.timeout,
        )
        body = self._success(response, "login for test-data cleanup")
        session.headers.update({"Authorization": f"Bearer {body['data']['token']}"})
        return session

    @staticmethod
    def _success(response: requests.Response, action: str) -> dict:
        response.raise_for_status()
        body = response.json()
        if body.get("code") != 200:
            raise RuntimeError(f"API {action} failed: {body}")
        return body
