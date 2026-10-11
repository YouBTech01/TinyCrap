import pytest

from tinycrap.errors import ApiError
from tinycrap.validation import validate_url


@pytest.mark.parametrize("url,platform", [
    ("https://www.youtube.com/watch?v=dQw4w9WgXcQ", "youtube"),
    ("https://youtu.be/dQw4w9WgXcQ", "youtube"),
    ("youtube.com/shorts/abc", "youtube"),
    ("https://m.youtube.com/watch?v=x", "youtube"),
    ("https://www.instagram.com/reel/Cabc123/", "instagram"),
    ("https://instagr.am/p/abc/", "instagram"),
    ("https://www.facebook.com/watch/?v=123", "facebook"),
    ("https://fb.watch/abcd/", "facebook"),
    ("HTTPS://WWW.FACEBOOK.COM/x/videos/1", "facebook"),
])
def test_valid(url, platform):
    clean, found = validate_url(url)
    assert found == platform
    assert clean.startswith("https://")


@pytest.mark.parametrize("url", [
    "", "   ", None, 123, ["https://youtube.com"],
    "file:///etc/passwd",
    "ftp://youtube.com/x",
    "javascript:alert(1)",
    "https://evil.com/watch?v=1",
    "https://youtube.com.evil.com/watch",
    "https://evilyoutube.com/watch",
    "https://user:pass@youtube.com/watch?v=1",
    "https://youtube.com:8080/watch",
    "http://127.0.0.1/x",
    "http://[::1]/x",
    "http://169.254.169.254/latest/meta-data",
    "https://localhost/x",
    "https://youtube.com/watch?v=1 --exec id",
    "https://youtube.com/watch?v=1\nhttps://evil.com",
    "https://youtube.com/" + "a" * 3000,
    "https://уoutube.com/watch",
])
def test_rejected(url):
    with pytest.raises(ApiError):
        validate_url(url)
