"""Builds yt-dlp command lines and a scrubbed environment for child processes.

We always run yt-dlp as a child process with an argument *list* (never a shell
string), so a URL can never be interpreted as shell syntax or as a yt-dlp
option (we also pass ``--`` before the URL). A child process can be killed on
timeout/cancel, which an in-process call cannot.
"""
from __future__ import annotations

import os
import sys
from typing import Dict, List

from .config import Config
from .diagnostics import Tools

_ENV_KEEP = (
    "PATH", "HOME", "LANG", "LC_ALL", "TMPDIR", "TEMP", "TMP", "SYSTEMROOT", "USERPROFILE",
    "APPDATA", "LOCALAPPDATA", "SSL_CERT_FILE", "SSL_CERT_DIR", "REQUESTS_CA_BUNDLE",
    "HTTP_PROXY", "HTTPS_PROXY", "NO_PROXY", "http_proxy", "https_proxy", "no_proxy",
    "DENO_DIR", "NODE_EXTRA_CA_CERTS",
)


def safe_env() -> Dict[str, str]:
    """Environment for child processes. Drops secrets such as TINYCRAP_API_KEY."""
    env = {k: os.environ[k] for k in _ENV_KEEP if k in os.environ}
    env["PYTHONIOENCODING"] = "utf-8"
    env["PYTHONUNBUFFERED"] = "1"
    return env


def base_command(cfg: Config, tools: Tools) -> List[str]:
    """Arguments shared by analysis and downloads."""
    if cfg.ytdlp_cmd:
        cmd = list(cfg.ytdlp_cmd)
    else:
        cmd = [sys.executable, "-m", "yt_dlp"]

    cmd += [
        "--ignore-config",            # never read user/system config files
        "--no-plugin-dirs",           # never load third-party plugins
        "--color", "never",
        "--no-playlist",
        "--playlist-items", "1",      # carousels/albums: first item only
        "--socket-timeout", str(cfg.socket_timeout),
        "--retries", "3",
        "--cache-dir", str(cfg.temp_dir / ".yt-dlp-cache"),
    ]
    if tools.ffmpeg:
        # Point yt-dlp at the exact binary we verified at startup.
        cmd += ["--ffmpeg-location", tools.ffmpeg]
    if tools.js_runtime:
        cmd += ["--js-runtimes", tools.js_runtime]
    return cmd
