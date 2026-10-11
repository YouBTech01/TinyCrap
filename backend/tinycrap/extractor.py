"""yt-dlp based media analysis (POST /api/analyze).

Extractor support is dynamic: platforms change and yt-dlp extractors break. Any
failure is converted into a clear, user-facing message instead of a crash.
"""
from __future__ import annotations

import json
import logging
import subprocess
import threading
import time
from collections import OrderedDict
from typing import Any, Dict, Optional, Tuple

from . import formats
from .config import Config
from .diagnostics import Tools
from .errors import ApiError, classify_error
from .ytdlp import base_command, safe_env

log = logging.getLogger("tinycrap.extractor")

CACHE_TTL = 300      # seconds an analysis result is reused
CACHE_MAX = 64
MAX_JSON_BYTES = 25 * 1024 * 1024


class Analyzer:
    def __init__(self, cfg: Config, tools: Tools):
        self.cfg = cfg
        self.tools = tools
        self._cache: "OrderedDict[str, Tuple[float, Dict[str, Any]]]" = OrderedDict()
        self._lock = threading.Lock()
        # Analysis spawns a process; keep the number of simultaneous ones small.
        self._slots = threading.BoundedSemaphore(max(1, cfg.max_concurrent_analyze))
        self._inflight: Dict[str, threading.Lock] = {}

    def analyze(self, url: str, platform: str) -> Dict[str, Any]:
        if not self.tools.can_extract:
            raise ApiError("server_misconfigured",
                           "The server is missing yt-dlp, so it cannot read media right now.", 503)

        cached = self._cache_get(url)
        if cached is not None:
            return cached

        # Several people pasting the same link at once: one lookup, everyone else reuses its answer.
        with self._lock:
            gate = self._inflight.setdefault(url, threading.Lock())
        try:
            with gate:
                cached = self._cache_get(url)
                if cached is not None:
                    return cached
                if not self._slots.acquire(timeout=30):     # wait in line instead of failing fast
                    raise ApiError("busy", "The server is busy right now. Try again in a minute.", 429)
                try:
                    info = self._run(url)
                finally:
                    self._slots.release()
                result = formats.normalize(info, platform, url, self.cfg)
                self._cache_put(url, result)
                return result
        finally:
            with self._lock:
                self._inflight.pop(url, None)

    def _run(self, url: str) -> Dict[str, Any]:
        cmd = base_command(self.cfg, self.tools) + ["--dump-single-json", "--no-warnings", "--", url]
        try:
            cp = subprocess.run(
                cmd, capture_output=True, text=True, encoding="utf-8", errors="replace",
                timeout=self.cfg.analyze_timeout, stdin=subprocess.DEVNULL,
                cwd=str(self.cfg.temp_dir), env=safe_env(),
            )
        except subprocess.TimeoutExpired:
            raise ApiError("timeout", "The platform took too long to respond. Try again.", 504)
        except OSError as exc:
            log.error("could not start yt-dlp: %s", exc)
            raise ApiError("server_misconfigured", "The server could not start yt-dlp.", 503)

        if cp.returncode != 0 or not cp.stdout.strip():
            code, message, status = classify_error(cp.stderr or cp.stdout or "")
            log.info("analysis failed (%s): %s", code, (cp.stderr or "").strip()[-300:])
            raise ApiError(code, message, status)
        if len(cp.stdout) > MAX_JSON_BYTES:
            raise ApiError("extract_failed", "The platform returned too much data for this link.", 502)
        try:
            return json.loads(cp.stdout)
        except ValueError:
            raise ApiError("extract_failed", "Could not read the platform's response.", 502)

    # -- tiny TTL cache ---------------------------------------------------------
    def _cache_get(self, url: str) -> Optional[Dict[str, Any]]:
        with self._lock:
            hit = self._cache.get(url)
            if hit and time.time() - hit[0] < CACHE_TTL:
                self._cache.move_to_end(url)
                return hit[1]
            self._cache.pop(url, None)
        return None

    def _cache_put(self, url: str, value: Dict[str, Any]) -> None:
        with self._lock:
            self._cache[url] = (time.time(), value)
            self._cache.move_to_end(url)
            while len(self._cache) > CACHE_MAX:
                self._cache.popitem(last=False)
