"""Runtime configuration for Tiny Crap.

Everything is driven by environment variables (prefix ``TINYCRAP_``) so the same
code runs locally, under waitress, and under cPanel / Passenger where env vars
are set from the Application Manager. A tiny ``.env`` loader is included for
local convenience (no extra dependency).
"""
from __future__ import annotations

import os
import tempfile
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional

APP_NAME = "Tiny Crap"
APP_VERSION = "2.0.0"
ROOT_DIR = Path(__file__).resolve().parent.parent


def load_dotenv(path: Path) -> None:
    """Minimal KEY=VALUE loader. Never overrides variables already in the env."""
    try:
        text = path.read_text(encoding="utf-8")
    except OSError:
        return
    for raw in text.splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, _, value = line.partition("=")
        key = key.strip()
        value = value.strip().strip('"').strip("'")
        if key and key not in os.environ:
            os.environ[key] = value


def _env(name: str, default: Optional[str] = None) -> Optional[str]:
    value = os.environ.get(f"TINYCRAP_{name}")
    if value is None or value.strip() == "":
        return default
    return value.strip()


def _int(name: str, default: int, minimum: int = 1) -> int:
    raw = _env(name)
    if raw is None:
        return default
    try:
        return max(minimum, int(raw))
    except ValueError:
        return default


def _bool(name: str, default: bool = False) -> bool:
    raw = _env(name)
    if raw is None:
        return default
    return raw.lower() in {"1", "true", "yes", "on"}


@dataclass(frozen=True)
class Config:
    # Storage -----------------------------------------------------------------
    temp_dir: Path = field(default_factory=lambda: Path(tempfile.gettempdir()) / "tinycrap")
    cleanup_minutes: int = 30          # finished jobs (and their files) live this long
    data_dir: Path = field(default_factory=lambda: ROOT_DIR / "data")   # lifetime stats, API keys, updates

    # Limits ------------------------------------------------------------------
    max_concurrent_jobs: int = 2       # downloads running at the same moment, server-wide (all users together)
    max_queued_jobs: int = 200         # everybody else waits in line; only beyond this do we answer "busy"
    max_jobs_per_client: int = 8       # running + waiting downloads one client (IP) may hold at once
    max_concurrent_analyze: int = 3    # simultaneous "read this link" lookups
    max_filesize_mb: int = 500         # hard cap per download
    max_duration_sec: int = 3 * 3600   # refuse media longer than this
    analyze_timeout: int = 45          # seconds for /api/analyze
    job_timeout: int = 900             # seconds for one download job
    socket_timeout: int = 20           # network read timeout inside yt-dlp
    rate_limit_per_min: int = 30       # per client IP, for POST endpoints

    # Access ------------------------------------------------------------------
    api_key: Optional[str] = None      # optional shared secret (X-API-Key header)
    admin_password: Optional[str] = None   # admin panel password; auto-generated into data/ when empty
    trust_proxy: bool = False          # honour X-Forwarded-* (set behind a reverse proxy)

    # Tooling -----------------------------------------------------------------
    ffmpeg_path: Optional[str] = None  # directory or binary; default: search PATH
    ffprobe_path: Optional[str] = None
    js_runtime: Optional[str] = None   # e.g. "deno", "node", "node:/usr/bin/node"
    ytdlp_cmd: Optional[tuple] = None  # override command (used by tests)

    log_level: str = "INFO"

    @property
    def max_filesize_bytes(self) -> int:
        return self.max_filesize_mb * 1024 * 1024

    @classmethod
    def from_env(cls) -> "Config":
        load_dotenv(ROOT_DIR / ".env")
        temp = _env("TEMP_DIR")
        return cls(
            temp_dir=Path(temp).expanduser() if temp else Path(tempfile.gettempdir()) / "tinycrap",
            cleanup_minutes=_int("CLEANUP_MINUTES", 30),
            data_dir=Path(_env("DATA_DIR") or ROOT_DIR / "data").expanduser(),
            max_concurrent_jobs=_int("MAX_CONCURRENT_JOBS", 2),
            max_queued_jobs=_int("MAX_QUEUED_JOBS", 200, minimum=0),
            max_jobs_per_client=_int("MAX_JOBS_PER_CLIENT", 8),
            max_concurrent_analyze=_int("MAX_CONCURRENT_ANALYZE", 3),
            max_filesize_mb=_int("MAX_FILESIZE_MB", 500),
            max_duration_sec=_int("MAX_DURATION_SEC", 3 * 3600),
            analyze_timeout=_int("ANALYZE_TIMEOUT", 45),
            job_timeout=_int("JOB_TIMEOUT", 900),
            socket_timeout=_int("SOCKET_TIMEOUT", 20),
            rate_limit_per_min=_int("RATE_LIMIT_PER_MIN", 30),
            api_key=_env("API_KEY"),
            admin_password=_env("ADMIN_PASSWORD"),
            trust_proxy=_bool("TRUST_PROXY", False),
            ffmpeg_path=_env("FFMPEG_PATH"),
            ffprobe_path=_env("FFPROBE_PATH"),
            js_runtime=_env("JS_RUNTIME"),
            log_level=(_env("LOG_LEVEL", "INFO") or "INFO").upper(),
        )
