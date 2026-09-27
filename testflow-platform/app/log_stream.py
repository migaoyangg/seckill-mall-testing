from __future__ import annotations

import queue
import threading


class RunLogStream:
    def __init__(self) -> None:
        self._subscribers: dict[int, set[queue.Queue[str]]] = {}
        self._lock = threading.Lock()

    def subscribe(self, run_id: int) -> queue.Queue[str]:
        channel: queue.Queue[str] = queue.Queue(maxsize=500)
        with self._lock:
            self._subscribers.setdefault(run_id, set()).add(channel)
        return channel

    def unsubscribe(self, run_id: int, channel: queue.Queue[str]) -> None:
        with self._lock:
            channels = self._subscribers.get(run_id)
            if channels:
                channels.discard(channel)
                if not channels:
                    self._subscribers.pop(run_id, None)

    def publish(self, run_id: int, line: str) -> None:
        with self._lock:
            channels = list(self._subscribers.get(run_id, set()))
        for channel in channels:
            try:
                channel.put_nowait(line)
            except queue.Full:
                try:
                    channel.get_nowait()
                    channel.put_nowait(line)
                except queue.Empty:
                    pass


run_log_stream = RunLogStream()

