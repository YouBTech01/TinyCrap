"""Small persistent store for the things that must survive a restart: lifetime counters,
generated API keys and the app-update notice. One JSON file, no database.

It re-reads the file when another process changed it, so two Passenger workers stay in step.
"""
from __future__ import annotations

import hashlib
import json
import os
import re
import secrets
import threading
import time
from typing import Any, Dict, List, Optional

from .config import Config
from .errors import ApiError

_URL = re.compile(r"^https?://[^\s]+$", re.I)


def _hash(key: str) -> str:
    return hashlib.sha256(key.encode("utf-8")).hexdigest()


def _blank() -> Dict[str, Any]:
    return {
        "stats": {"started": 0, "completed": 0, "failed": 0, "cancelled": 0, "bytes": 0,
                  "platforms": {}, "kinds": {}, "since": time.time()},
        "keys": [], "updates": [], "settings": {"require_key": False},
    }


class Registry:
    def __init__(self, cfg: Config):
        cfg.data_dir.mkdir(parents=True, exist_ok=True)
        self.path = cfg.data_dir / "state.json"
        self._lock = threading.RLock()
        self._mtime = 0.0
        self._saved_at = 0.0
        self._dirty = False
        self._data = _blank()
        with self._lock:
            self._sync(force=True)

    # -- file handling --------------------------------------------------------------
    def _sync(self, force: bool = False) -> None:
        try:
            mtime = self.path.stat().st_mtime
        except OSError:
            if force:
                self._save()
            return
        if not force and mtime == self._mtime:
            return
        try:
            data = json.loads(self.path.read_text(encoding="utf-8"))
        except (OSError, ValueError):
            return
        merged = _blank()
        for k, v in data.items():
            if k in merged and isinstance(v, type(merged[k])):
                merged[k] = v
        merged["stats"] = {**_blank()["stats"], **merged["stats"]}
        merged["settings"] = {**_blank()["settings"], **merged["settings"]}
        self._data, self._mtime = merged, mtime

    def _save(self) -> None:
        tmp = self.path.with_suffix(".tmp")
        try:
            tmp.write_text(json.dumps(self._data), encoding="utf-8")
            os.replace(tmp, self.path)
            self._mtime = self.path.stat().st_mtime
            self._saved_at, self._dirty = time.monotonic(), False
        except OSError:
            pass

    def flush(self) -> None:
        with self._lock:
            if self._dirty:
                self._save()

    # -- lifetime counters ------------------------------------------------------------
    def started(self, platform: str, kind: str) -> None:
        with self._lock:
            self._sync()
            st = self._data["stats"]
            st["started"] += 1
            st["platforms"][platform] = st["platforms"].get(platform, 0) + 1
            st["kinds"][kind] = st["kinds"].get(kind, 0) + 1
            self._save()

    def finished(self, size: int) -> None:
        with self._lock:
            self._sync()
            self._data["stats"]["completed"] += 1
            self._data["stats"]["bytes"] += max(0, int(size))
            self._save()

    def failed(self, cancelled: bool) -> None:
        with self._lock:
            self._sync()
            self._data["stats"]["cancelled" if cancelled else "failed"] += 1
            self._save()

    def stats(self) -> Dict[str, Any]:
        with self._lock:
            self._sync()
            return json.loads(json.dumps(self._data["stats"]))

    # -- settings -----------------------------------------------------------------------
    def setting(self, name: str) -> Any:
        with self._lock:
            self._sync()
            return self._data["settings"].get(name)

    def set_setting(self, name: str, value: Any) -> None:
        with self._lock:
            self._sync()
            self._data["settings"][name] = value
            self._save()

    # -- API keys -----------------------------------------------------------------------
    def create_key(self, name: str, per_minute: Optional[int]) -> Dict[str, Any]:
        name = (name or "").strip()[:60]
        if not name:
            raise ApiError("invalid_request", "Give the key a name.")
        raw = "tc_" + secrets.token_urlsafe(24)
        entry = {"id": secrets.token_hex(4), "name": name, "hash": _hash(raw), "prefix": raw[:9],
                 "per_minute": per_minute, "active": True, "created": time.time(),
                 "last_used": None, "requests": 0}
        with self._lock:
            self._sync()
            self._data["keys"].append(entry)
            self._save()
        return {**self._public_key(entry), "key": raw}

    @staticmethod
    def _public_key(k: Dict[str, Any]) -> Dict[str, Any]:
        return {x: k[x] for x in ("id", "name", "prefix", "per_minute", "active", "created", "last_used", "requests")}

    def keys(self) -> List[Dict[str, Any]]:
        with self._lock:
            self._sync()
            return [self._public_key(k) for k in self._data["keys"]]

    def find_key(self, raw: str) -> Optional[Dict[str, Any]]:
        if not raw:
            return None
        digest = _hash(raw)
        with self._lock:
            self._sync()
            for k in self._data["keys"]:
                if k["active"] and secrets.compare_digest(k["hash"], digest):
                    return self._public_key(k)
        return None

    def touch_key(self, key_id: str) -> None:
        with self._lock:
            for k in self._data["keys"]:
                if k["id"] == key_id:
                    k["requests"] += 1
                    k["last_used"] = time.time()
                    self._dirty = True
            if self._dirty and time.monotonic() - self._saved_at > 20:
                self._save()

    def toggle_key(self, key_id: str) -> bool:
        with self._lock:
            self._sync()
            for k in self._data["keys"]:
                if k["id"] == key_id:
                    k["active"] = not k["active"]
                    self._save()
                    return True
        return False

    def delete_key(self, key_id: str) -> bool:
        with self._lock:
            self._sync()
            before = len(self._data["keys"])
            self._data["keys"] = [k for k in self._data["keys"] if k["id"] != key_id]
            self._save()
            return len(self._data["keys"]) != before

    # -- app updates -----------------------------------------------------------------------
    def add_update(self, title: str, link: str, version_code: Any, version_name: str = "") -> Dict[str, Any]:
        title, link = (title or "").strip(), (link or "").strip()
        if not title or len(title) > 100:
            raise ApiError("invalid_request", "Write a short title (up to 100 characters).")
        if len(link) > 500 or not _URL.match(link):
            raise ApiError("invalid_request", "The link must start with http:// or https://")
        try:
            code = int(version_code)
        except (TypeError, ValueError):
            raise ApiError("invalid_request", "Version code must be a whole number, like 3.")
        if code < 1:
            raise ApiError("invalid_request", "Version code must be 1 or higher.")
        entry = {"id": secrets.token_hex(4), "title": title, "link": link, "version_code": code,
                 "version_name": (version_name or "").strip()[:20], "created": time.time()}
        with self._lock:
            self._sync()
            self._data["updates"].append(entry)
            self._data["updates"] = sorted(self._data["updates"], key=lambda u: u["version_code"])[-30:]
            self._save()
        return entry

    def updates(self) -> List[Dict[str, Any]]:
        with self._lock:
            self._sync()
            return list(reversed(self._data["updates"]))

    def delete_update(self, update_id: str) -> bool:
        with self._lock:
            self._sync()
            before = len(self._data["updates"])
            self._data["updates"] = [u for u in self._data["updates"] if u["id"] != update_id]
            self._save()
            return len(self._data["updates"]) != before

    def update_for(self, current_code: int) -> Optional[Dict[str, Any]]:
        """The newest published update, if it is newer than the app that is asking."""
        with self._lock:
            self._sync()
            ups = self._data["updates"]
            if not ups:
                return None
            newest = max(ups, key=lambda u: u["version_code"])
        if newest["version_code"] <= current_code:
            return None
        return {"title": newest["title"], "link": newest["link"], "version_code": newest["version_code"],
                "version_name": newest["version_name"], "required": True}
