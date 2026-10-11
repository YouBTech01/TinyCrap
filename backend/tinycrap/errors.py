"""API error type and mapping of yt-dlp / ffmpeg output to friendly messages."""
from __future__ import annotations

import re
from typing import Tuple


class ApiError(Exception):
    """An error that is safe to show to the client."""

    def __init__(self, code: str, message: str, status: int = 400):
        super().__init__(message)
        self.code = code
        self.message = message
        self.status = status

    def to_dict(self) -> dict:
        return {"code": self.code, "message": self.message}


# (needles, code, message, http status). First match wins, so order matters.
_PATTERNS = [
    (("not a bot", "confirm you’re not a bot", "confirm you're not a bot"),
     "platform_blocked",
     "The platform is asking this server to verify it is not a bot. Try again later.", 503),
    (("private video", "this video is private", "private account", "login required",
      "requires authentication", "rate-limit reached or login required", "sign in",
      "log in", "login", "cookies", "members-only", "join this channel"),
     "login_required",
     "This content is private or needs a login. Tiny Crap only works with public posts.", 422),
    (("drm",),
     "drm",
     "This media is DRM-protected and cannot be downloaded.", 422),
    (("ffmpeg not found", "ffprobe not found", "ffmpeg is not installed",
      "ffprobe and ffmpeg", "ffmpeg and ffprobe", "avconv"),
     "ffmpeg_missing",
     "FFmpeg is missing on the server, so this file cannot be processed.", 503),
    (("unsupported url",),
     "unsupported",
     "This link is not supported. Paste a public Instagram, YouTube or Facebook video link.", 422),
    (("confirm your age", "age-restricted", "age restricted", "inappropriate for some users"),
     "age_restricted",
     "This video is age-restricted and needs a signed-in account, so it cannot be downloaded.", 422),
    (("not available in your country", "blocked it in your country", "geo restrict",
      "geo-restrict", "geo blocked"),
     "geo_blocked",
     "This media is not available from the server's region.", 422),
    (("live event will begin", "is live", "live stream", "premieres in"),
     "live",
     "Live streams and upcoming premieres cannot be downloaded.", 422),
    (("video unavailable", "has been removed", "no longer available", "does not exist",
      "this video is not available", "content isn't available", "content is not available",
      "page isn't available", "not found", "http error 404"),
     "unavailable",
     "This media is unavailable. It may have been removed or made private.", 404),
    (("http error 429", "too many requests", "rate limit", "rate-limit"),
     "rate_limited",
     "The platform is rate-limiting this server. Wait a few minutes and try again.", 503),
    (("larger than max-filesize", "max-filesize", "file is larger"),
     "too_large",
     "This file is larger than the size limit set on the server.", 413),
    (("does not pass filter",),
     "too_long",
     "This media is longer than the limit set on the server.", 413),
    (("requested format is not available", "no video formats", "no formats"),
     "format_unavailable",
     "That quality or format is not available for this media. Pick another option.", 422),
    (("postprocessing", "conversion failed", "error while"),
     "processing_failed",
     "The file could not be processed. Try a different quality or format.", 500),
    (("timed out", "timeout"),
     "timeout",
     "The platform took too long to respond. Try again.", 504),
    (("unable to download", "urlopen error", "name or service not known",
      "temporary failure in name resolution", "connection", "network is unreachable"),
     "network",
     "Could not reach the platform from the server. Try again in a moment.", 502),
]

_ERROR_LINE = re.compile(r"^\s*ERROR:\s*(.*)$", re.IGNORECASE)


def classify_error(text: str) -> Tuple[str, str, int]:
    """Map raw tool output to (code, friendly message, http status)."""
    lines = [m.group(1) for m in (_ERROR_LINE.match(l) for l in text.splitlines()) if m]
    haystack = ("\n".join(lines) if lines else text).lower()
    for needles, code, message, status in _PATTERNS:
        if any(n in haystack for n in needles):
            return code, message, status
    return (
        "extract_failed",
        "Could not read this media. The platform may have changed or the link is not a video. "
        "If it keeps failing, the server's yt-dlp may need an update.",
        502,
    )
