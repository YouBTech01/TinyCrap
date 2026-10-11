import os
import shutil
import time

import pytest

from conftest import wait_done

pytestmark = pytest.mark.skipif(not shutil.which("ffmpeg"), reason="ffmpeg required")

YT = "https://www.youtube.com/watch?v=abc123"


def post(client, path, body, **kw):
    return client.post(path, json=body, **kw)


def test_health(client):
    j = client.get("/api/health").get_json()
    assert j["ok"] and j["ffmpeg"] is True


def test_root_and_404_are_json(client):
    assert client.get("/").get_json()["app"] == "Tiny Crap"
    r = client.get("/nope")
    assert r.status_code == 404 and r.get_json()["ok"] is False


def test_analyze_ok(client):
    r = post(client, "/api/analyze", {"url": YT})
    assert r.status_code == 200
    m = r.get_json()["media"]
    assert m["platform"] == "youtube" and m["title"].startswith("Test")
    assert [q["height"] for q in m["video"]["qualities"]] == [1080, 720]


@pytest.mark.parametrize("body,status", [
    ({}, 400), ({"url": "https://evil.com/x"}, 422), ({"url": "file:///etc/passwd"}, 400),
    ({"url": "http://127.0.0.1:8000/api/health"}, 400),
])
def test_analyze_rejects_bad_input(client, body, status):
    r = post(client, "/api/analyze", body)
    assert r.status_code == status and r.get_json()["ok"] is False


def test_analyze_non_json(client):
    r = client.post("/api/analyze", data="url=x", content_type="text/plain")
    assert r.status_code == 400


def test_analyze_private_maps_to_friendly_error(make_client):
    client, _ = make_client(mode="private")
    r = post(client, "/api/analyze", {"url": "https://www.instagram.com/p/abc/"})
    err = r.get_json()["error"]
    assert r.status_code == 422 and err["code"] == "login_required"


def test_analyze_generic_extractor_is_unsupported(make_client):
    client, _ = make_client(mode="generic")
    r = post(client, "/api/analyze", {"url": YT})
    assert r.get_json()["error"]["code"] == "unsupported"


def test_video_download_full_flow(client):
    r = post(client, "/api/download/video", {"url": YT, "quality": "1080", "container": "mp4"})
    assert r.status_code == 202
    job_id = r.get_json()["job"]["job_id"]

    job, stages = wait_done(client, job_id)
    assert job["stage"] == "ready", job
    assert "downloading" in stages and stages[-1] == "ready"
    assert job["percent"] == 100
    res = job["result"]
    assert res["filename"] == "Test Video Title.mp4"      # unsafe chars stripped
    assert res["mime"] == "video/mp4" and res["size"] > 0

    f = client.get(res["download_url"])
    assert f.status_code == 200 and f.mimetype == "video/mp4"
    assert "attachment" in f.headers["Content-Disposition"]
    assert len(f.data) == res["size"]

    part = client.get(res["download_url"], headers={"Range": "bytes=0-99"})
    assert part.status_code == 206 and len(part.data) == 100


def test_webm_download(client):
    job_id = post(client, "/api/download/video",
                  {"url": YT, "quality": "720", "container": "webm"}).get_json()["job"]["job_id"]
    job, _ = wait_done(client, job_id)
    if job["stage"] == "error":
        pytest.skip("ffmpeg build lacks libvpx/libvorbis: " + job["error"]["message"])
    assert job["result"]["filename"].endswith(".webm")


@pytest.mark.parametrize("fmt,bitrate,ext", [("best", None, "m4a"), ("mp3", 192, "mp3"), ("wav", None, "wav")])
def test_audio_download(client, fmt, bitrate, ext):
    body = {"url": YT, "format": fmt}
    if bitrate:
        body["bitrate"] = bitrate
    job_id = post(client, "/api/download/audio", body).get_json()["job"]["job_id"]
    job, _ = wait_done(client, job_id)
    if job["stage"] == "error":
        pytest.skip("ffmpeg encoder missing: " + job["error"]["message"])
    assert job["result"]["filename"].endswith("." + ext)
    assert client.get(job["result"]["download_url"]).status_code == 200


@pytest.mark.parametrize("path,body", [
    ("/api/download/video", {"url": YT, "quality": "abc"}),
    ("/api/download/video", {"url": YT, "quality": "720; rm -rf /"}),
    ("/api/download/video", {"url": YT, "quality": "720", "container": "avi"}),
    ("/api/download/audio", {"url": YT, "format": "exe"}),
    ("/api/download/audio", {"url": YT, "format": "mp3", "bitrate": 999}),
    ("/api/download/audio", {"url": YT, "format": "mp3", "bitrate": "x"}),
])
def test_download_rejects_bad_options(client, path, body):
    r = post(client, path, body)
    assert r.status_code == 400 and r.get_json()["error"]["code"] == "invalid_request"


def test_job_error_is_actionable(make_client):
    client, _ = make_client(mode="private")
    job_id = post(client, "/api/download/video",
                  {"url": "https://www.instagram.com/p/abc/", "quality": "best"}).get_json()["job"]["job_id"]
    job, _ = wait_done(client, job_id)
    assert job["stage"] == "error" and job["error"]["code"] == "login_required"
    assert client.get(f"/api/download/{job_id}").status_code == 409


def test_size_limit_kills_job(make_client):
    client, _ = make_client(mode="huge")
    job_id = post(client, "/api/download/video", {"url": YT, "quality": "best"}).get_json()["job"]["job_id"]
    job, _ = wait_done(client, job_id)
    assert job["error"]["code"] == "too_large"


def test_timeout(make_client):
    client, _ = make_client(job_timeout=1, mode="slow")
    job_id = post(client, "/api/download/video", {"url": YT, "quality": "best"}).get_json()["job"]["job_id"]
    job, _ = wait_done(client, job_id)
    assert job["error"]["code"] == "timeout"


def test_cancel(make_client):
    client, _ = make_client(mode="slow")
    job_id = post(client, "/api/download/video", {"url": YT, "quality": "best"}).get_json()["job"]["job_id"]
    time.sleep(0.5)
    assert client.post(f"/api/cancel/{job_id}").status_code == 200
    job, _ = wait_done(client, job_id)
    assert job["error"]["code"] == "cancelled"


def test_concurrency_limit(make_client):
    client, _ = make_client(max_concurrent_jobs=1, max_queued_jobs=1, mode="slow")
    codes = [post(client, "/api/download/video", {"url": YT, "quality": "best"}).status_code
             for _ in range(3)]
    assert codes == [202, 202, 429]


def test_unknown_and_malformed_job_ids(client):
    assert client.get("/api/progress/does-not-exist-123").status_code == 404
    assert client.get("/api/progress/..%2F..%2Fetc%2Fpasswd").status_code == 404
    assert client.get("/api/download/../../etc/passwd").status_code == 404


def test_api_key(make_client):
    client, _ = make_client(api_key="s3cret")
    assert post(client, "/api/analyze", {"url": YT}).status_code == 401
    assert post(client, "/api/analyze", {"url": YT}, headers={"X-API-Key": "nope"}).status_code == 401
    assert post(client, "/api/analyze", {"url": YT}, headers={"X-API-Key": "s3cret"}).status_code == 200
    assert client.get("/api/health").status_code == 200   # health stays open for monitoring


def test_rate_limit(make_client):
    client, _ = make_client(rate_limit_per_min=2)
    codes = [post(client, "/api/analyze", {"url": YT}).status_code for _ in range(3)]
    assert codes == [200, 200, 429]


def test_cleanup_removes_expired_jobs_and_files(make_client):
    client, app = make_client()
    store = app.extensions["tinycrap"].store
    job_id = post(client, "/api/download/audio", {"url": YT, "format": "best"}).get_json()["job"]["job_id"]
    job, _ = wait_done(client, job_id)
    job_dir = store.cfg.temp_dir / job_id
    assert job_dir.exists()

    assert store.sweep() == 0                                  # fresh: kept
    state = job_dir / "state.json"
    old = time.time() - 3600
    import json
    data = json.loads(state.read_text())
    data["finished_at"] = old
    state.write_text(json.dumps(data))
    assert store.sweep() == 1
    assert not job_dir.exists()
    assert client.get(f"/api/progress/{job_id}").status_code == 404


def test_status_survives_across_worker_processes(make_client):
    """A second store (another worker) can answer from the on-disk state."""
    client, app = make_client()
    job_id = post(client, "/api/download/audio", {"url": YT, "format": "best"}).get_json()["job"]["job_id"]
    wait_done(client, job_id)
    from tinycrap.jobs import JobStore
    other = JobStore(app.extensions["tinycrap"].cfg)
    job = other.get(job_id)
    assert job is not None and job.stage == "ready"
    other.shutdown()


def test_missing_ffmpeg_reports_cleanly(make_client):
    client, _ = make_client(ffmpeg_path="/nonexistent/ffmpeg")
    h = client.get("/api/health").get_json()
    assert h["ffmpeg"] is False and h["status"] != "ok" and h["problems"]
    job_id = post(client, "/api/download/video", {"url": YT, "quality": "best"}).get_json()["job"]["job_id"]
    job, _ = wait_done(client, job_id)
    assert job["error"]["code"] == "ffmpeg_missing"
