"""Strict URL validation. Runs before any subprocess is started."""
from __future__ import annotations

import ipaddress
import re
from typing import Tuple
from urllib.parse import urlsplit, urlunsplit

from .errors import ApiError
from .platforms import platform_for_host

MAX_URL_LENGTH = 2048
_BAD_CHARS = re.compile(r"[\x00-\x20\x7f\"'<>\\^`{|}]")


def validate_url(raw: object) -> Tuple[str, str]:
    """Return ``(clean_url, platform)`` or raise ApiError.

    Rules: http(s) only, no credentials, no custom ports, no IP literals,
    ASCII host, and the host must belong to a supported platform.
    """
    if not isinstance(raw, str):
        raise ApiError("invalid_url", "Paste a link to an Instagram, YouTube or Facebook video.")
    url = raw.strip()
    if not url:
        raise ApiError("invalid_url", "Paste a link to an Instagram, YouTube or Facebook video.")
    if len(url) > MAX_URL_LENGTH:
        raise ApiError("invalid_url", "That link is too long.")
    if _BAD_CHARS.search(url):
        raise ApiError("invalid_url", "That link contains characters that are not allowed.")

    if "://" not in url:
        url = "https://" + url  # allow "youtube.com/watch?v=..." pasted without scheme

    try:
        parts = urlsplit(url)
        port = parts.port
    except ValueError:
        raise ApiError("invalid_url", "That does not look like a valid link.")

    if parts.scheme not in ("http", "https"):
        raise ApiError("invalid_url", "Only http(s) links are supported.")
    if parts.username or parts.password:
        raise ApiError("invalid_url", "Links with embedded credentials are not allowed.")
    if port not in (None, 80, 443):
        raise ApiError("invalid_url", "Custom ports are not allowed.")

    host = (parts.hostname or "").lower().rstrip(".")
    if not host or not host.isascii():
        raise ApiError("invalid_url", "That does not look like a valid link.")
    try:
        ipaddress.ip_address(host)
        raise ApiError("invalid_url", "IP addresses are not allowed. Use the platform's normal link.")
    except ValueError:
        pass  # not an IP literal, which is what we want

    platform = platform_for_host(host)
    if platform is None:
        raise ApiError(
            "unsupported",
            "Only Instagram, YouTube and Facebook links are supported.",
            422,
        )

    clean = urlunsplit(("https", host, parts.path or "/", parts.query, ""))
    return clean, platform
