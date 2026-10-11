"""FFmpeg / ffprobe helpers. FFmpeg is an external executable, not a pip package."""
from __future__ import annotations

import json
import os
import shutil
import subprocess
from pathlib import Path
from typing import Optional, Tuple

_EXE = ".exe" if os.name == "nt" else ""


def _resolve(configured: Optional[str], name: str, sibling_dir: Optional[Path] = None) -> Optional[str]:
    if configured:
        p = Path(configured).expanduser()
        if p.is_dir():
            p = p / (name + _EXE)
        return str(p) if p.is_file() and os.access(p, os.X_OK) else None
    if sibling_dir is not None:
        p = sibling_dir / (name + _EXE)
        if p.is_file() and os.access(p, os.X_OK):
            return str(p)
    return shutil.which(name)


def locate(ffmpeg_cfg: Optional[str], ffprobe_cfg: Optional[str]) -> Tuple[Optional[str], Optional[str]]:
    """Return absolute paths ``(ffmpeg, ffprobe)``; either may be None if missing."""
    ffmpeg = _resolve(ffmpeg_cfg, "ffmpeg")
    sibling = Path(ffmpeg).parent if ffmpeg else None
    ffprobe = _resolve(ffprobe_cfg, "ffprobe", sibling_dir=sibling)
    return ffmpeg, ffprobe


def tool_version(path: str) -> Optional[str]:
    try:
        cp = subprocess.run(
            [path, "-version"], capture_output=True, text=True, timeout=10,
            stdin=subprocess.DEVNULL,
        )
    except (OSError, subprocess.SubprocessError):
        return None
    first = (cp.stdout or "").splitlines()[:1]
    return first[0].strip() if first else None


def probe(ffprobe: str, file_path: Path, timeout: int = 20) -> Optional[dict]:
    """Inspect a finished file. Returns None when ffprobe cannot read it."""
    cmd = [
        ffprobe, "-v", "error", "-print_format", "json",
        "-show_entries", "format=duration,size,format_name:stream=codec_type,codec_name",
        "--", str(file_path),
    ]
    try:
        cp = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout,
                            stdin=subprocess.DEVNULL)
        if cp.returncode != 0:
            return None
        data = json.loads(cp.stdout or "{}")
    except (OSError, subprocess.SubprocessError, ValueError):
        return None

    streams = data.get("streams") or []
    fmt = data.get("format") or {}
    try:
        duration = float(fmt.get("duration")) if fmt.get("duration") not in (None, "N/A") else None
    except (TypeError, ValueError):
        duration = None
    return {
        "duration": duration,
        "has_video": any(s.get("codec_type") == "video" for s in streams),
        "has_audio": any(s.get("codec_type") == "audio" for s in streams),
        "format": fmt.get("format_name"),
    }
