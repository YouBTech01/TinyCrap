import pytest

from tinycrap import Config
from tinycrap.errors import ApiError
from tinycrap.formats import normalize

CFG = Config()


def yt_info():
    return {
        "id": "x", "title": "Song", "extractor_key": "Youtube", "duration": 100,
        "uploader": "Chan", "thumbnail": "https://i/t.jpg",
        "formats": [
            {"format_id": "sb0", "ext": "mhtml", "vcodec": "none", "acodec": "none",
             "format_note": "storyboard", "url": "u"},
            {"format_id": "401", "ext": "mp4", "vcodec": "av01.0.12M.08", "acodec": "none",
             "height": 2160, "width": 3840, "fps": 30, "tbr": 9000, "filesize_approx": 90_000_000, "url": "u"},
            {"format_id": "313", "ext": "webm", "vcodec": "vp9", "acodec": "none",
             "height": 2160, "width": 3840, "fps": 30, "tbr": 12000, "filesize": 120_000_000, "url": "u"},
            {"format_id": "137", "ext": "mp4", "vcodec": "avc1.640028", "acodec": "none",
             "height": 1080, "width": 1920, "fps": 60, "tbr": 4000, "filesize": 50_000_000, "url": "u"},
            {"format_id": "18", "ext": "mp4", "vcodec": "avc1.42001E", "acodec": "mp4a.40.2",
             "height": 360, "width": 640, "fps": 30, "tbr": 500, "filesize": 6_000_000, "url": "u"},
            {"format_id": "140", "ext": "m4a", "vcodec": "none", "acodec": "mp4a.40.2",
             "abr": 130, "tbr": 130, "filesize": 1_600_000, "url": "u"},
            {"format_id": "251", "ext": "webm", "vcodec": "none", "acodec": "opus",
             "abr": 140, "tbr": 140, "filesize": 1_700_000, "url": "u"},
        ],
    }


def test_youtube_unified_model():
    m = normalize(yt_info(), "youtube", "https://youtu.be/x", CFG)
    assert m["platform"] == "youtube" and m["duration_text"] == "1:40"

    qs = m["video"]["qualities"]
    assert [q["height"] for q in qs] == [2160, 1080, 360]          # only what exists
    assert qs[0]["is_best"] and not qs[1]["is_best"]
    assert m["video"]["containers"] == ["mp4", "webm"]
    assert m["video"]["default_container"] == "mp4"

    q1080 = qs[1]
    assert [v["container"] for v in q1080["variants"]] == ["mp4"]  # no 1080 webm in source
    v = q1080["variants"][0]
    assert v["needs_merge"] and v["fps"] == 60 and v["codec"] == "H.264"
    assert v["size"] == 50_000_000 + 1_600_000                     # video + best m4a

    q360 = qs[2]["variants"][0]
    assert not q360["needs_merge"] and q360["size"] == 6_000_000

    webm2160 = [v for v in qs[0]["variants"] if v["container"] == "webm"][0]
    assert webm2160["size"] == 120_000_000 + 1_700_000 and webm2160["codec"] == "VP9"


def test_audio_source_vs_converted():
    a = normalize(yt_info(), "youtube", "u", CFG)["audio"]
    assert a["available"]
    assert a["source"]["codec"] == "Opus" and a["source"]["bitrate"] == 140
    best = a["formats"][0]
    assert best["format"] == "best" and not best["converted"]
    mp3 = next(f for f in a["formats"] if f["format"] == "mp3")
    assert mp3["converted"] and mp3["default_bitrate"] == 192
    flags = {b["value"]: b["exceeds_source"] for b in mp3["bitrates"]}
    assert flags == {128: False, 192: True, 256: True, 320: True}  # 140 kbps source
    wav = next(f for f in a["formats"] if f["format"] == "wav")
    assert wav["bitrates"] == [] and not wav["lossy"]


def test_facebook_combined_formats_without_height_fall_back_to_best():
    info = {
        "id": "1", "title": "FB", "extractor_key": "Facebook", "duration": 30,
        "formats": [
            {"format_id": "sd", "ext": "mp4", "vcodec": "avc1", "acodec": "mp4a", "url": "u", "tbr": 300},
            {"format_id": "hd", "ext": "mp4", "vcodec": "avc1", "acodec": "mp4a", "url": "u", "tbr": 900},
        ],
    }
    m = normalize(info, "facebook", "u", CFG)
    assert m["video"]["qualities"][0]["key"] == "best"
    assert not m["video"]["qualities"][0]["variants"][0]["needs_merge"]
    assert m["audio"]["available"]


def test_instagram_carousel_uses_first_entry():
    entry = {"id": "e1", "title": "Post", "extractor_key": "Instagram", "duration": 10,
             "formats": [{"format_id": "v", "ext": "mp4", "vcodec": "avc1", "acodec": "mp4a",
                          "height": 720, "width": 720, "url": "u"}]}
    m = normalize({"_type": "playlist", "entries": [entry], "extractor_key": "InstagramPost"},
                  "instagram", "u", CFG)
    assert m["video"]["qualities"][0]["height"] == 720


def test_limits_flagged():
    cfg = Config(max_filesize_mb=10)
    m = normalize(yt_info(), "youtube", "u", cfg)
    assert m["video"]["qualities"][0]["variants"][0]["exceeds_limit"]
    assert not m["video"]["qualities"][2]["variants"][0]["exceeds_limit"]


@pytest.mark.parametrize("mutate,code", [
    (lambda i: i.update(extractor_key="Generic"), "unsupported"),
    (lambda i: i.update(is_live=True), "live"),
    (lambda i: i.update(duration=999999), "too_long"),
    (lambda i: i.update(formats=[]), "unavailable"),
    (lambda i: i.update(formats=[dict(f, has_drm=True) for f in i["formats"]]), "unavailable"),
])
def test_errors(mutate, code):
    info = yt_info()
    mutate(info)
    with pytest.raises(ApiError) as exc:
        normalize(info, "youtube", "u", CFG)
    assert exc.value.code == code
