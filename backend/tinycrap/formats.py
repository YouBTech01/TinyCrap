"""Normalises yt-dlp's ``info`` dict into one unified media model.

Instagram, YouTube and Facebook all produce the same structure, so the Android
app has a single Video/Audio UI. Only qualities that actually exist in the
source are reported; nothing is invented.
"""
from __future__ import annotations

from typing import Any, Dict, List, Optional

from .config import Config
from .errors import ApiError
from .platforms import PLATFORM_LABELS, platform_from_extractor

VIDEO_CONTAINERS = ("mp4", "webm")

# Audio output formats the pipeline really supports through yt-dlp + FFmpeg.
AUDIO_FORMATS = {
    "mp3": {"label": "MP3", "lossy": True, "bitrates": (128, 192, 256, 320), "default": 192},
    "m4a": {"label": "M4A (AAC)", "lossy": True, "bitrates": (128, 192, 256), "default": 192},
    "opus": {"label": "Opus", "lossy": True, "bitrates": (64, 96, 128, 160), "default": 128},
    "wav": {"label": "WAV", "lossy": False, "bitrates": (), "default": None},
    "flac": {"label": "FLAC", "lossy": False, "bitrates": (), "default": None},
}

_VCODEC_LABELS = (
    ("avc", "H.264"), ("h264", "H.264"), ("hev", "H.265"), ("hvc", "H.265"), ("h265", "H.265"),
    ("vp09", "VP9"), ("vp9", "VP9"), ("vp8", "VP8"), ("av01", "AV1"), ("av1", "AV1"),
)
_ACODEC_LABELS = (
    ("mp4a", "AAC"), ("aac", "AAC"), ("opus", "Opus"), ("vorbis", "Vorbis"),
    ("mp3", "MP3"), ("ec-3", "E-AC-3"), ("ac-3", "AC-3"), ("flac", "FLAC"),
)


def _label(codec: Optional[str], table) -> Optional[str]:
    c = (codec or "").lower()
    if not c or c == "none":
        return None
    for prefix, label in table:
        if c.startswith(prefix):
            return label
    return c.split(".")[0].upper()


def format_duration(seconds: Optional[float]) -> Optional[str]:
    if not seconds or seconds < 0:
        return None
    s = int(round(seconds))
    h, rem = divmod(s, 3600)
    m, sec = divmod(rem, 60)
    return f"{h}:{m:02d}:{sec:02d}" if h else f"{m}:{sec:02d}"


def _num(value: Any) -> Optional[float]:
    try:
        v = float(value)
        return v if v == v and v > 0 else None  # drops NaN and non-positive
    except (TypeError, ValueError):
        return None


def _size(f: Dict[str, Any], duration: Optional[float]) -> tuple:
    """Return (bytes|None, estimated?)."""
    exact = _num(f.get("filesize"))
    if exact:
        return int(exact), False
    approx = _num(f.get("filesize_approx"))
    if approx:
        return int(approx), True
    tbr = _num(f.get("tbr"))
    if tbr and duration:
        return int(tbr * 1000 / 8 * duration), True
    return None, False


def _usable(f: Dict[str, Any]) -> bool:
    if f.get("has_drm") or f.get("ext") == "mhtml":
        return False
    if (f.get("format_note") or "").lower() == "storyboard":
        return False
    if (f.get("protocol") or "") in ("mhtml", "rtsp", "rtmp"):
        return False
    if f.get("vcodec") == "none" and f.get("acodec") == "none":
        return False
    return bool(f.get("url") or f.get("fragments") or f.get("manifest_url") or f.get("format_id"))


def _is_video(f: Dict[str, Any]) -> bool:
    return f.get("vcodec") != "none" and bool(f.get("height") or f.get("width") or f.get("vcodec"))


def _is_audio_only(f: Dict[str, Any]) -> bool:
    return f.get("vcodec") == "none" and f.get("acodec") != "none"


def _has_audio(f: Dict[str, Any]) -> bool:
    # In yt-dlp, acodec == "none" means no audio; None means unknown (assume present).
    return f.get("acodec") != "none"


def _codec_rank(vcodec: Optional[str]) -> int:
    """Prefer widely playable codecs when several formats share a height."""
    label = _label(vcodec, _VCODEC_LABELS)
    return {"H.264": 3, "VP9": 2, "H.265": 1, "AV1": 1, "VP8": 0}.get(label or "", 0)


def _best_audio_for(audio_only: List[Dict[str, Any]], container: str) -> Optional[Dict[str, Any]]:
    wanted = ("m4a",) if container == "mp4" else ("webm",)
    pool = [a for a in audio_only if a.get("ext") in wanted] or []
    if not pool:
        return None
    return max(pool, key=lambda a: _num(a.get("abr")) or _num(a.get("tbr")) or 0)


def build_video(info: Dict[str, Any], cfg: Config, duration: Optional[float]) -> Dict[str, Any]:
    formats = [f for f in (info.get("formats") or []) if _usable(f)]
    video_formats = [f for f in formats if _is_video(f)]
    audio_only = [f for f in formats if _is_audio_only(f)]

    by_height: Dict[Optional[int], List[Dict[str, Any]]] = {}
    for f in video_formats:
        h = int(f["height"]) if f.get("height") else None
        by_height.setdefault(h, []).append(f)

    has_any_height = any(h for h in by_height)
    qualities: List[Dict[str, Any]] = []

    heights = sorted((h for h in by_height if h), reverse=True) if has_any_height else [None]
    for h in heights:
        pool = by_height.get(h) if h else video_formats
        variants = []
        for container in VIDEO_CONTAINERS:
            cands = [f for f in pool if f.get("ext") == container]
            if not cands:
                continue
            best = max(cands, key=lambda f: (_codec_rank(f.get("vcodec")), _num(f.get("tbr")) or 0))
            needs_merge = not _has_audio(best)
            size, est = _size(best, duration)
            if needs_merge:
                audio = _best_audio_for(audio_only, container)
                if audio is None and container == "webm":
                    continue  # webm needs a webm/opus audio stream to merge with
                a_size, _ = _size(audio, duration) if audio else (None, True)
                size = size + a_size if (size is not None and a_size is not None) else size
            variants.append({
                "container": container,
                "codec": _label(best.get("vcodec"), _VCODEC_LABELS),
                "fps": int(_num(best.get("fps"))) if _num(best.get("fps")) else None,
                "hdr": (best.get("dynamic_range") or "SDR") != "SDR",
                "size": size,
                "size_estimated": est or needs_merge,
                "needs_merge": needs_merge,
                "exceeds_limit": bool(size and size > cfg.max_filesize_bytes),
            })
        if not variants:
            continue
        top = pool[0] if pool else {}
        width = max((int(f["width"]) for f in pool if f.get("width")), default=None)
        note = (top.get("format_note") or "").strip() if not h else None
        qualities.append({
            "key": str(h) if h else "best",
            "height": h,
            "width": width,
            "label": f"{h}p" if h else (note or "Best available"),
            "fps": max((v["fps"] for v in variants if v["fps"]), default=None),
            "variants": variants,
            "is_best": False,
        })

    if qualities:
        qualities[0]["is_best"] = True

    containers = [c for c in VIDEO_CONTAINERS if any(
        v["container"] == c for q in qualities for v in q["variants"])]
    return {
        "available": bool(qualities),
        "default_container": "mp4" if "mp4" in containers else (containers[0] if containers else None),
        "containers": containers,
        "qualities": qualities,
    }


def build_audio(info: Dict[str, Any], duration: Optional[float]) -> Dict[str, Any]:
    formats = [f for f in (info.get("formats") or []) if _usable(f)]
    audio_only = [f for f in formats if _is_audio_only(f)]
    combined = [f for f in formats if _is_video(f) and _has_audio(f)]

    source_f = None
    if audio_only:
        source_f = max(audio_only, key=lambda a: _num(a.get("abr")) or _num(a.get("tbr")) or 0)
    elif combined:
        source_f = max(combined, key=lambda f: _num(f.get("abr")) or _num(f.get("tbr")) or 0)
    if source_f is None:
        return {"available": False, "source": None, "formats": []}

    source_abr = _num(source_f.get("abr")) or (_num(source_f.get("tbr")) if audio_only else None)
    size, est = _size(source_f, duration) if audio_only else (None, True)
    source_codec = _label(source_f.get("acodec"), _ACODEC_LABELS)
    native_ext = source_f.get("ext") if audio_only else "m4a"
    if native_ext in ("webm", "weba"):
        native_ext = "opus"

    source = {
        "codec": source_codec,
        "ext": native_ext,
        "bitrate": round(source_abr) if source_abr else None,
        "size": size,
        "size_estimated": est,
    }

    fmts: List[Dict[str, Any]] = [{
        "format": "best",
        "label": "Best audio (original)",
        "ext": native_ext,
        "converted": False,
        "lossy": True,
        "note": "Original stream from the platform, not re-encoded. No quality loss.",
        "default_bitrate": None,
        "bitrates": [],
    }]
    for key, spec in AUDIO_FORMATS.items():
        bitrates = []
        for br in spec["bitrates"]:
            bitrates.append({
                "value": br,
                "size": int(br * 1000 / 8 * duration) if duration else None,
                "size_estimated": True,
                "exceeds_source": bool(source_abr and br > source_abr * 1.05),
            })
        if spec["lossy"]:
            note = "Converted. Re-encoding cannot improve on the source quality."
        else:
            note = "Lossless container, but the source is lossy, so it will not sound better. Large file."
        wav_size = int(44100 * 2 * 2 * duration) if (key == "wav" and duration) else None
        fmts.append({
            "format": key,
            "label": spec["label"],
            "ext": key,
            "converted": True,
            "lossy": spec["lossy"],
            "note": note,
            "default_bitrate": spec["default"],
            "bitrates": bitrates,
            "size": wav_size,
            "size_estimated": True,
        })
    return {"available": True, "source": source, "formats": fmts}


def _pick_thumbnail(info: Dict[str, Any]) -> Optional[str]:
    candidates = [info.get("thumbnail")]
    thumbs = [t for t in (info.get("thumbnails") or []) if isinstance(t, dict)]
    thumbs.sort(key=lambda t: (t.get("preference") or 0, t.get("width") or 0))
    candidates += [t.get("url") for t in reversed(thumbs)]
    for url in candidates:
        if isinstance(url, str) and url.startswith("https://"):
            return url
    return None


def normalize(info: Dict[str, Any], platform_hint: str, source_url: str, cfg: Config) -> Dict[str, Any]:
    """Convert a yt-dlp info dict to the unified model, or raise ApiError."""
    if info.get("_type") in ("playlist", "multi_video") or "entries" in info:
        entries = [e for e in (info.get("entries") or []) if e]
        if not entries:
            raise ApiError("unavailable", "No downloadable media was found at this link.", 404)
        extractor_key = info.get("extractor_key") or ""
        info = dict(entries[0])
        info.setdefault("extractor_key", extractor_key)

    extractor = info.get("extractor_key") or info.get("extractor") or ""
    platform = platform_from_extractor(extractor) or None
    if platform is None:
        # yt-dlp fell back to a generic extractor: the link is not a recognised media page.
        raise ApiError("unsupported", "No video was found at this link, or the platform is not supported.", 422)
    if platform != platform_hint:
        platform = platform_hint

    if info.get("is_live") or info.get("live_status") in ("is_live", "is_upcoming"):
        raise ApiError("live", "Live streams and upcoming premieres cannot be downloaded.", 422)

    duration = _num(info.get("duration"))
    if duration and duration > cfg.max_duration_sec:
        raise ApiError(
            "too_long",
            f"This media is longer than the {cfg.max_duration_sec // 60}-minute limit set on the server.",
            413,
        )

    video = build_video(info, cfg, duration)
    audio = build_audio(info, duration)
    if not video["available"] and not audio["available"]:
        raise ApiError(
            "unavailable",
            "No downloadable formats were found. The media may be protected or private.",
            422,
        )

    return {
        "id": info.get("id"),
        "url": source_url,
        "platform": platform,
        "platform_label": PLATFORM_LABELS.get(platform, platform),
        "title": (info.get("title") or "Untitled")[:300],
        "uploader": info.get("uploader") or info.get("channel") or info.get("creator"),
        "thumbnail": _pick_thumbnail(info),
        "duration": int(duration) if duration else None,
        "duration_text": format_duration(duration),
        "video": video,
        "audio": audio,
        "limits": {
            "max_filesize_mb": cfg.max_filesize_mb,
            "max_duration_min": cfg.max_duration_sec // 60,
        },
    }
