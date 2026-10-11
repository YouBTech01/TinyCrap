"""Tiny in-memory sliding-window rate limiter (per client IP). No external store."""
from __future__ import annotations

import threading
import time
from collections import deque
from typing import Deque, Dict


class RateLimiter:
    def __init__(self, per_minute: int):
        self.per_minute = per_minute
        self._hits: Dict[str, Deque[float]] = {}
        self._lock = threading.Lock()
        self._last_gc = time.monotonic()

    def blocked(self, key: str, limit: int = 0) -> bool:
        """True when [key] already used up its allowance in the last minute (does not count a hit)."""
        now = time.monotonic()
        with self._lock:
            q = self._hits.get(key)
            if not q:
                return False
            while q and now - q[0] > 60:
                q.popleft()
            return len(q) >= (limit or self.per_minute)

    def allow(self, key: str, limit: int = 0) -> bool:
        now = time.monotonic()
        with self._lock:
            q = self._hits.setdefault(key, deque())
            while q and now - q[0] > 60:
                q.popleft()
            if len(q) >= (limit or self.per_minute):
                return False
            q.append(now)
            if now - self._last_gc > 300:  # drop idle keys so memory stays flat
                self._last_gc = now
                for k in [k for k, v in self._hits.items() if not v or now - v[-1] > 60]:
                    self._hits.pop(k, None)
            return True
