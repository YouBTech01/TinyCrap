"""Startup diagnostics: Python, yt-dlp and FFmpeg/ffprobe availability."""
from __future__ import annotations

import logging
import shutil
import sys
from dataclasses import dataclass, field
from datetime import date
from importlib import metadata
from typing import List, Optional

from . import ffmpeg_utils
from .config import Config

log = logging.getLogger("tinycrap.diagnostics")

MIN_PYTHON = (3, 9)
YTDLP_STALE_DAYS = 90


@dataclass
class Tools:
    python_version: str = ""
    python_ok: bool = True
    ytdlp_version: Optional[str] = None
    ytdlp_age_days: Optional[int] = None
    ffmpeg: Optional[str] = None
    ffmpeg_version: Optional[str] = None
    ffprobe: Optional[str] = None
    js_runtime: Optional[str] = None
    problems: List[str] = field(default_factory=list)
    warnings: List[str] = field(default_factory=list)
    ytdlp_overridden: bool = False

    @property
    def can_extract(self) -> bool:
        return self.python_ok and (self.ytdlp_version is not None or self.ytdlp_overridden)

    @property
    def can_process(self) -> bool:
        return self.ffmpeg is not None

    @property
    def healthy(self) -> bool:
        return self.can_extract and self.can_process and self.ffprobe is not None

    def public(self) -> dict:
        """Safe summary for /api/health (no filesystem paths)."""
        return {
            "status": "ok" if self.healthy else ("degraded" if self.can_extract else "down"),
            "python": self.python_version,
            "yt_dlp": self.ytdlp_version,
            "ffmpeg": self.ffmpeg is not None,
            "ffprobe": self.ffprobe is not None,
            "js_runtime": self.js_runtime,
            "problems": list(self.problems),
            "warnings": list(self.warnings),
        }


def _ytdlp_age_days(version: str) -> Optional[int]:
    try:
        y, m, d = (int(p) for p in version.split(".")[:3])
        return (date.today() - date(y, m, d)).days
    except (ValueError, TypeError):
        return None


def check_environment(cfg: Config) -> Tools:
    t = Tools(python_version=sys.version.split()[0])

    if sys.version_info < MIN_PYTHON:
        t.python_ok = False
        t.problems.append(
            f"Python {MIN_PYTHON[0]}.{MIN_PYTHON[1]}+ is required (found {t.python_version})."
        )

    # yt-dlp ------------------------------------------------------------------
    if cfg.ytdlp_cmd:
        t.ytdlp_overridden = True
    else:
        try:
            t.ytdlp_version = metadata.version("yt-dlp")
        except metadata.PackageNotFoundError:
            t.problems.append("yt-dlp is not installed. Run: pip install -r requirements.txt")
        if t.ytdlp_version:
            t.ytdlp_age_days = _ytdlp_age_days(t.ytdlp_version)
            if t.ytdlp_age_days is not None and t.ytdlp_age_days > YTDLP_STALE_DAYS:
                t.warnings.append(
                    f"yt-dlp {t.ytdlp_version} is {t.ytdlp_age_days} days old. Platforms change often; "
                    "update with: pip install -U yt-dlp"
                )

    # FFmpeg / ffprobe ----------------------------------------------------------
    t.ffmpeg, t.ffprobe = ffmpeg_utils.locate(cfg.ffmpeg_path, cfg.ffprobe_path)
    if t.ffmpeg:
        t.ffmpeg_version = ffmpeg_utils.tool_version(t.ffmpeg)
    else:
        hint = f" (TINYCRAP_FFMPEG_PATH={cfg.ffmpeg_path!r} is not valid)" if cfg.ffmpeg_path else ""
        t.problems.append(
            "ffmpeg not found" + hint + ". Merging video+audio and audio conversion will fail. "
            "Install FFmpeg or set TINYCRAP_FFMPEG_PATH."
        )
    if not t.ffprobe:
        t.warnings.append("ffprobe not found. Finished files will not be verified.")

    # JavaScript runtime (recent yt-dlp needs one for full YouTube support) ----------
    if cfg.js_runtime:
        t.js_runtime = cfg.js_runtime
    else:
        for candidate in ("deno", "node", "bun"):
            if shutil.which(candidate):
                t.js_runtime = candidate
                break
    if not t.js_runtime:
        t.warnings.append(
            "No JavaScript runtime (deno/node/bun) found. YouTube may expose fewer formats. "
            "Install Deno or Node.js, or set TINYCRAP_JS_RUNTIME."
        )

    _log_report(t)
    return t


def _log_report(t: Tools) -> None:
    log.info("Python %s", t.python_version)
    log.info("yt-dlp: %s", t.ytdlp_version or ("override" if t.ytdlp_overridden else "MISSING"))
    log.info("ffmpeg: %s", t.ffmpeg_version or "MISSING")
    log.info("ffprobe: %s", t.ffprobe or "MISSING")
    log.info("js runtime: %s", t.js_runtime or "none")
    for p in t.problems:
        log.error("DIAGNOSTIC: %s", p)
    for w in t.warnings:
        log.warning("DIAGNOSTIC: %s", w)
