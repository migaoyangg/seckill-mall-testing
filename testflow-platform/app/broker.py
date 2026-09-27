from __future__ import annotations

import os
import queue
from abc import ABC, abstractmethod
from typing import Optional


class TaskBroker(ABC):
    @abstractmethod
    def enqueue(self, run_id: int) -> None:
        raise NotImplementedError

    @abstractmethod
    def dequeue(self, timeout: int = 2) -> Optional[int]:
        raise NotImplementedError


class LocalTaskBroker(TaskBroker):
    def __init__(self) -> None:
        self.queue: queue.Queue[int] = queue.Queue()

    def enqueue(self, run_id: int) -> None:
        self.queue.put(run_id)

    def dequeue(self, timeout: int = 2) -> Optional[int]:
        try:
            return self.queue.get(timeout=timeout)
        except queue.Empty:
            return None


class RedisTaskBroker(TaskBroker):
    def __init__(self, url: str, queue_name: str = "testflow:runs") -> None:
        import redis

        self.client = redis.Redis.from_url(url, decode_responses=True)
        self.client.ping()
        self.queue_name = queue_name

    def enqueue(self, run_id: int) -> None:
        self.client.rpush(self.queue_name, str(run_id))

    def dequeue(self, timeout: int = 2) -> Optional[int]:
        item = self.client.blpop(self.queue_name, timeout=timeout)
        return int(item[1]) if item else None


_local_broker = LocalTaskBroker()


def build_broker() -> TaskBroker:
    backend = os.getenv("TESTFLOW_QUEUE_BACKEND", "local").lower()
    if backend == "redis":
        return RedisTaskBroker(
            os.getenv("TESTFLOW_REDIS_URL", "redis://127.0.0.1:6379/0"),
            os.getenv("TESTFLOW_REDIS_QUEUE", "testflow:runs"),
        )
    return _local_broker

