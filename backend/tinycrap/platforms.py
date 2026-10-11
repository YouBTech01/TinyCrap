"""Platform detection. Extraction itself stays in extractor.py (yt-dlp)."""
from __future__ import annotations

from typing import Optional

# Hosts we accept per platform. Anything else is rejected before yt-dlp runs,
# which keeps the extractor away from arbitrary hosts (SSRF) and local files.
PLATFORM_DOMAINS = {
    "youtube": ("youtube.com", "youtu.be", "youtube-nocookie.com"),
    "instagram": ("instagram.com", "instagr.am"),
    "facebook": ("facebook.com", "fb.watch", "fb.com"),
}

PLATFORM_LABELS = {"youtube": "YouTube", "instagram": "Instagram", "facebook": "Facebook"}


def platform_for_host(host: str) -> Optional[str]:
    host = host.lower().rstrip(".")
    for platform, domains in PLATFORM_DOMAINS.items():
        for domain in domains:
            if host == domain or host.endswith("." + domain):
                return platform
    return None


def platform_from_extractor(extractor_key: str) -> Optional[str]:
    """Map a yt-dlp extractor key (e.g. 'YoutubeTab', 'Instagram') to our platform id."""
    key = (extractor_key or "").lower()
    for platform in PLATFORM_DOMAINS:
        if key.startswith(platform):
            return platform
    return None
