"""Download / convert jobs. Runs yt-dlp (+FFmpeg) as a controlled child process.

Stages: preparing → analyzing → downloading → processing → finalizing → ready.
"""
from __future__ import annotations

import json
import logging
import re
import subprocess
import threading
import time
import unicodedata
from collections import deque
from pathlib import Path
from typing import Any, Dict, List, Optional

from . import ffmpeg_utils
from .config import Config
from .diagnostics import Tools
from .errors import ApiError, classify_error
from .formats import AUDIO_FORMATS, VIDEO_CONTAINERS
from .jobs import Job, JobStore
from .ytdlp import base_command, safe_env

log = logging.getLogger("tinycrap.downloader")

PROGRESS_PREFIX = "TCP"
PROGRESS_TEMPLATE = (
    "download:" + PROGRESS_PREFIX + "|%(progress.status)s|%(progress.downloaded_bytes)s|"
    "%(progress.total_bytes)s|%(progress.total_bytes_estimate)s|%(progress.speed)s|%(progress.eta)s"
)

MIME = {
    "mp4": "video/mp4", "webm": "video/webm", "mkv": "video/x-matroska", "mov": "video/quicktime",
    "m4a": "audio/mp4", "mp3": "audio/mpeg", "opus": "audio/ogg", "ogg": "audio/ogg",
    "wav": "audio/wav", "flac": "audio/flac", "aac": "audio/aac", "weba": "audio/webm",
}
FINAL_EXTS = tuple(MIME)
_INTERMEDIATE = re.compile(r"^media\.f[\w-]+\.")
_FORMAT_COUNT = re.compile(r"Downloading (\d+) format\(s\)")
_POSTPROC = re.compile(r"^\[(Merger|ExtractAudio|VideoRemuxer|VideoConvertor|FFmpeg\w*|Fixup\w*|Metadata|EmbedThumbnail)\]")


# -- request → yt-dlp arguments -------------------------------------------------

def validate_video_request(data: Dict[str, Any]) -> Dict[str, Any]:
    quality = str(data.get("quality", "best")).strip().lower()
    if quality != "best":
        if not quality.isdigit() or not (100 <= int(quality) <= 8640):
            raise ApiError("invalid_request", "Pick one of the listed qualities.")
    container = str(data.get("container", "mp4")).strip().lower()
    if container not in VIDEO_CONTAINERS:
        raise ApiError("invalid_request", "Pick MP4 or WebM.")
    return {"quality": quality, "container": container}


def validate_audio_request(data: Dict[str, Any]) -> Dict[str, Any]:
    fmt = str(data.get("format", "best")).strip().lower()
    if fmt != "best" and fmt not in AUDIO_FORMATS:
        raise ApiError("invalid_request", "Pick one of the listed audio formats.")
    bitrate: Optional[int] = None
    if fmt != "best" and AUDIO_FORMATS[fmt]["bitrates"]:
        raw = data.get("bitrate", AUDIO_FORMATS[fmt]["default"])
        try:
            bitrate = int(raw)
        except (TypeError, ValueError):
            raise ApiError("invalid_request", "Pick one of the listed bitrates.")
        if bitrate not in AUDIO_FORMATS[fmt]["bitrates"]:
            raise ApiError("invalid_request", "Pick one of the listed bitrates.")
    return {"format": fmt, "bitrate": bitrate}


def video_selector(quality: str, container: str) -> str:
    """Build a format selector from validated values only (never raw client text)."""
    if quality == "best":
        h_eq = h_le = ""
    else:
        h_eq, h_le = f"[height={quality}]", f"[height<={quality}]"
    if container == "mp4":
        return (
            f"bv*{h_eq}[ext=mp4]+ba[ext=m4a]/b{h_eq}[ext=mp4]/"
            f"bv*{h_eq}[ext=mp4]+ba/bv*{h_le}+ba/b{h_le}"
        )
    return f"bv*{h_eq}[ext=webm]+ba[ext=webm]/b{h_eq}[ext=webm]/bv*{h_le}[ext=webm]+ba[ext=webm]"


def build_args(job: Job, cfg: Config, tools: Tools) -> List[str]:
    args = base_command(cfg, tools) + [
        "--newline", "--progress", "--progress-template", PROGRESS_TEMPLATE,
        "--no-simulate", "--no-mtime", "--no-part",
        "--concurrent-fragments", "4",
        "--max-filesize", f"{cfg.max_filesize_mb}M",
        "--match-filters", f"!is_live & duration <=? {cfg.max_duration_sec}",
        "--write-info-json",
        "-o", "media.%(ext)s",
    ]
    if job.kind == "video":
        o = job.options
        args += ["-f", video_selector(o["quality"], o["container"]),
                 "--merge-output-format", o["container"]]
    else:
        o = job.options
        args += ["-f", "ba/b", "-x", "--audio-format", o["format"]]
        if o.get("bitrate"):
            args += ["--audio-quality", f"{o['bitrate']}K"]
    args += ["--", job.url]
    return args


# -- helpers ----------------------------------------------------------------------

def sanitize_filename(title: str, ext: str) -> str:
    text = unicodedata.normalize("NFKC", title or "")
    text = "".join(ch for ch in text if ch.isprintable() and ch not in '<>:"/\\|?*\x7f')
    text = re.sub(r"\s+", " ", text).strip(" .")
    text = text[:80].rstrip(" .") or "tiny-crap"
    return f"{text}.{ext}"


def _num(value: str) -> Optional[float]:
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def _find_output(job_dir: Path) -> Optional[Path]:
    candidates = [
        p for p in job_dir.glob("media.*")
        if p.is_file() and p.suffix.lower().lstrip(".") in FINAL_EXTS and not _INTERMEDIATE.match(p.name)
    ]
    return max(candidates, key=lambda p: p.stat().st_size) if candidates else None


def _read_title(job_dir: Path) -> str:
    path = job_dir / "media.info.json"
    try:
        return str(json.loads(path.read_text(encoding="utf-8")).get("title") or "")
    except (OSError, ValueError):
        return ""
    finally:
        try:
            path.unlink()
        except OSError:
            pass


# -- the job runner -----------------------------------------------------------------

def make_runner(store: JobStore, cfg: Config, tools: Tools):
    def runner(job: Job) -> None:
        _run(job, store, cfg, tools)
    return runner


def _run(job: Job, store: JobStore, cfg: Config, tools: Tools) -> None:
    if store.cancel_requested(job):
        return store.fail(job, "cancelled", "Download cancelled.")
    if not tools.can_extract:
        return store.fail(job, "server_misconfigured", "The server is missing yt-dlp.")
    if not tools.can_process:  # merging, remuxing and audio extraction all need FFmpeg
        return store.fail(job, "ffmpeg_missing",
                          "FFmpeg is missing on the server, so this file cannot be processed.")

    store.update(job, stage="analyzing", percent=2.0)
    args = build_args(job, cfg, tools)
    log.info("job %s start kind=%s platform=%s", job.id, job.kind, job.platform)

    try:
        proc = subprocess.Popen(
            args, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL,
            text=True, encoding="utf-8", errors="replace", bufsize=1,
            cwd=str(job.dir), env=safe_env(),
        )
    except OSError as exc:
        log.error("could not start yt-dlp: %s", exc)
        return store.fail(job, "server_misconfigured", "The server could not start yt-dlp.")
    job.proc = proc

    flags = {"timed_out": False, "too_large": False}

    def on_timeout() -> None:
        flags["timed_out"] = True
        _kill(proc)

    timer = threading.Timer(cfg.job_timeout, on_timeout)
    timer.daemon = True
    timer.start()

    tail: deque = deque(maxlen=40)
    streams = 1
    stream_index = 0
    last_cancel_check = 0.0

    try:
        assert proc.stdout is not None
        for raw in proc.stdout:
            line = raw.rstrip()
            if not line:
                continue

            now = time.monotonic()
            if now - last_cancel_check > 1.0:
                last_cancel_check = now
                if store.cancel_requested(job):
                    _kill(proc)
                    break

            if line.startswith(PROGRESS_PREFIX + "|"):
                stream_index = _handle_progress(job, store, cfg, line, streams, stream_index, proc, flags)
                continue

            tail.append(line)
            m = _FORMAT_COUNT.search(line)
            if m:
                streams = max(1, int(m.group(1)))
            elif _POSTPROC.match(line):
                _enter_processing(job, store, line)
    finally:
        try:
            proc.wait(timeout=30)
        except subprocess.TimeoutExpired:
            _kill(proc)
        timer.cancel()
        job.proc = None

    if store.cancel_requested(job):
        return store.fail(job, "cancelled", "Download cancelled.")
    if flags["timed_out"]:
        return store.fail(job, "timeout", "This download took too long and was stopped.")
    if flags["too_large"]:
        return store.fail(job, "too_large",
                          f"This file is larger than the {cfg.max_filesize_mb} MB limit set on the server.")

    output = _find_output(job.dir)
    if proc.returncode != 0 or output is None:
        text = "\n".join(tail)
        if output is None and "does not pass filter" in text:
            code, message = "too_long", "This media is live or longer than the limit set on the server."
        else:
            code, message, _ = classify_error(text)
        log.info("job %s failed (%s): %s", job.id, code, "\n".join(list(tail)[-5:]))
        return store.fail(job, code, message)

    _finalize(job, store, cfg, tools, output)


def _handle_progress(job: Job, store: JobStore, cfg: Config, line: str, streams: int,
                     stream_index: int, proc: subprocess.Popen, flags: Dict[str, bool]) -> int:
    parts = line.split("|")
    if len(parts) < 7:
        return stream_index
    status = parts[1]
    downloaded = _num(parts[2])
    total = _num(parts[3]) or _num(parts[4])
    speed = _num(parts[5])
    eta = _num(parts[6])

    if downloaded and downloaded > cfg.max_filesize_bytes:
        flags["too_large"] = True
        _kill(proc)
        return stream_index

    # Share of this stream: video usually dominates a video+audio pair.
    if streams == 2:
        weights = (0.9, 0.1)
    else:
        weights = tuple([1.0 / streams] * streams)
    idx = min(stream_index, len(weights) - 1)
    frac = 1.0 if status == "finished" else (min(downloaded / total, 1.0) if downloaded and total else None)
    percent = job.percent
    if frac is not None:
        done = sum(weights[:idx])
        percent = 5.0 + (done + weights[idx] * frac) * 85.0   # downloading spans 5% … 90%
        percent = max(percent, job.percent)                   # never move backwards

    if job.stage != "downloading":
        store.update(job, stage="downloading", percent=percent, speed=speed, eta=eta,
                     downloaded=int(downloaded) if downloaded else None,
                     total=int(total) if total else None)
    else:
        store.update(job, percent=percent, speed=speed, eta=eta,
                     downloaded=int(downloaded) if downloaded else None,
                     total=int(total) if total else None)
    return stream_index + 1 if status == "finished" else stream_index


def _enter_processing(job: Job, store: JobStore, line: str) -> None:
    if "[Merger]" in line:
        message = "Merging video and audio…"
    elif "[ExtractAudio]" in line:
        message = "Converting audio…"
    else:
        message = "Processing…"
    if job.stage in ("downloading", "analyzing", "processing"):
        store.update(job, stage="processing", message=message, percent=max(job.percent, 90.0),
                     speed=None, eta=None)


def _finalize(job: Job, store: JobStore, cfg: Config, tools: Tools, output: Path) -> None:
    store.update(job, stage="finalizing", percent=97.0)
    title = _read_title(job.dir)

    size = output.stat().st_size
    if size > cfg.max_filesize_bytes:
        return store.fail(job, "too_large",
                          f"This file is larger than the {cfg.max_filesize_mb} MB limit set on the server.")
    if size == 0:
        return store.fail(job, "processing_failed", "The downloaded file was empty. Try again.")

    duration: Optional[float] = None
    if tools.ffprobe:
        info = ffmpeg_utils.probe(tools.ffprobe, output)
        if info is None:
            return store.fail(job, "processing_failed",
                              "The downloaded file could not be read. Try a different quality or format.")
        duration = info.get("duration")
        if job.kind == "video" and not info.get("has_video"):
            return store.fail(job, "processing_failed", "The downloaded file has no video. Try another quality.")
        if job.kind == "audio" and not info.get("has_audio"):
            return store.fail(job, "processing_failed", "The downloaded file has no audio.")

    ext = output.suffix.lower().lstrip(".")
    filename = sanitize_filename(title, ext)
    store.ready(job, output, filename, size, MIME.get(ext, "application/octet-stream"), duration)
    log.info("job %s ready: %s (%d bytes)", job.id, ext, size)


def _kill(proc: subprocess.Popen) -> None:
    try:
        if proc.poll() is None:
            proc.kill()
    except OSError:
        pass
