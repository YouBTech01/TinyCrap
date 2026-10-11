import base64
import dataclasses
import sys
import threading
import time

import pytest

from conftest import FAKE
from tinycrap import Config
from tinycrap.jobs import JobStore, SlotPool
from tinycrap.registry import Registry

YT = "https://www.youtube.com/watch?v=abc123"
H = {"X-Requested-With": "tinycrap"}


def start(client, n=1, kind="video", **kw):
    ids = []
    for i in range(n):
        r = client.post(f"/api/download/{kind}", json={"url": YT, "quality": "720", "container": "mp4"}, **kw)
        assert r.status_code == 202, r.get_json()
        ids.append(r.get_json()["job"]["job_id"])
    return ids


def job(client, jid):
    return client.get(f"/api/progress/{jid}").get_json()["job"]


def until(fn, timeout=10):
    end = time.time() + timeout
    while time.time() < end:
        if fn():
            return True
        time.sleep(0.05)
    return False


# -- the line ----------------------------------------------------------------------------

def test_only_two_run_the_rest_wait_in_order(make_client):
    client, app = make_client(mode="slow", max_concurrent_jobs=2, max_jobs_per_client=10)
    ids = start(client, 5)
    assert until(lambda: job(client, ids[1])["stage"] == "downloading" or job(client, ids[1])["stage"] == "analyzing")
    states = [job(client, i) for i in ids]
    assert [s["queue_position"] for s in states] == [0, 0, 1, 2, 3]
    assert states[2]["message"].startswith("Waiting")
    assert app.extensions["tinycrap"].store.queue_stats()["running"] == 2


def test_next_job_starts_when_a_place_frees_up(make_client):
    client, _ = make_client(mode="slow", max_concurrent_jobs=1, max_jobs_per_client=10)
    first, second, third = start(client, 3)
    assert job(client, third)["queue_position"] == 2
    client.post(f"/api/cancel/{first}")
    assert until(lambda: job(client, first)["stage"] == "error")
    assert until(lambda: job(client, second)["queue_position"] == 0 and job(client, second)["stage"] != "error")
    assert until(lambda: job(client, third)["queue_position"] == 1)


def test_cancel_while_waiting_leaves_the_line(make_client):
    client, _ = make_client(mode="slow", max_concurrent_jobs=1, max_jobs_per_client=10)
    first, second, third = start(client, 3)
    client.post(f"/api/cancel/{second}")
    j = job(client, second)
    assert j["stage"] == "error" and j["error"]["code"] == "cancelled" and j["queue_position"] == 0
    assert job(client, third)["queue_position"] == 1


def test_queue_finishes_everything(make_client):
    client, _ = make_client(max_concurrent_jobs=2, max_jobs_per_client=10)
    ids = start(client, 6, kind="audio")
    assert until(lambda: all(job(client, i)["stage"] == "ready" for i in ids), timeout=40)


def test_one_client_cannot_hog_the_line(make_client):
    client, _ = make_client(mode="slow", max_concurrent_jobs=1, max_jobs_per_client=2)
    start(client, 2)
    r = client.post("/api/download/video", json={"url": YT, "quality": "720", "container": "mp4"})
    assert r.status_code == 429 and r.get_json()["error"]["code"] == "too_many"


def test_places_are_shared_between_processes(cfg):
    """Two stores on the same folder (like two Passenger workers) still run only one job at a time."""
    one = dataclasses.replace(cfg, max_concurrent_jobs=1, max_queued_jobs=5)
    a, b = JobStore(one), JobStore(one)
    release, running, peak = threading.Event(), [0], [0]

    def runner(j):
        running[0] += 1
        peak[0] = max(peak[0], running[0])
        release.wait(5)
        running[0] -= 1
        a.fail(j, "x", "done") if j.id in a._jobs else b.fail(j, "x", "done")

    ja = a.submit("video", YT, "youtube", {}, runner)
    jb = b.submit("video", YT, "youtube", {}, runner)
    time.sleep(1.2)
    assert running[0] == 1 and peak[0] == 1
    release.set()
    assert until(lambda: ja.finished and jb.finished)
    assert peak[0] == 1
    a.shutdown(); b.shutdown()


def test_stale_place_is_reclaimed(tmp_path):
    pool = SlotPool(tmp_path / "s", 1, stale_after=0.2)
    assert pool.acquire() == 0 and pool.acquire() is None
    time.sleep(0.4)
    assert pool.acquire() == 0


def test_same_link_is_read_once(make_client, tmp_path):
    counter = tmp_path / "calls.txt"
    client, app = make_client(ytdlp_cmd=(sys.executable, FAKE, "--fake-mode", "ok"))
    analyzer = app.extensions["tinycrap"].analyzer
    real, calls = analyzer._run, [0]

    def counting(url):
        calls[0] += 1
        time.sleep(0.3)
        return real(url)

    analyzer._run = counting
    threads = [threading.Thread(target=lambda: client.post("/api/analyze", json={"url": YT})) for _ in range(5)]
    [t.start() for t in threads]; [t.join() for t in threads]
    assert calls[0] == 1


# -- admin -------------------------------------------------------------------------------

def auth(app):
    pw = app.extensions["tinycrap"].admin_password
    return {"Authorization": "Basic " + base64.b64encode(f"admin:{pw}".encode()).decode()}


def test_admin_needs_password(make_client):
    client, app = make_client()
    assert client.get("/admin/").status_code == 401
    assert client.get("/admin/api/overview", headers={"Authorization": "Basic " + base64.b64encode(b"admin:nope").decode()}).status_code == 401
    ok = client.get("/admin/", headers=auth(app))
    assert ok.status_code == 200 and b"Tiny Crap" in ok.data


def test_admin_blocks_writes_without_header(make_client):
    client, app = make_client()
    r = client.post("/admin/api/settings", json={"require_key": True}, headers=auth(app))
    assert r.status_code == 403


def test_wrong_passwords_get_locked_out(make_client):
    client, app = make_client()
    bad = {"Authorization": "Basic " + base64.b64encode(b"admin:x").decode()}
    codes = [client.get("/admin/api/overview", headers=bad).status_code for _ in range(12)]
    assert codes[0] == 401 and codes[-1] == 429
    assert client.get("/admin/api/overview", headers=auth(app)).status_code == 429   # even the right one, for a minute


def test_env_password_is_used(make_client):
    client, app = make_client(admin_password="hunter22")
    r = client.get("/admin/api/overview", headers={"Authorization": "Basic " + base64.b64encode(b"admin:hunter22").decode()})
    assert r.status_code == 200


def test_overview_has_everything(make_client):
    client, app = make_client(mode="slow", max_concurrent_jobs=1)
    start(client, 2)
    until(lambda: app.extensions["tinycrap"].store.queue_stats()["running"] == 1)
    d = client.get("/admin/api/overview", headers=auth(app)).get_json()
    assert d["app"]["status"] in ("ok", "degraded") and d["limits"]["concurrent"] == 1
    assert d["stats"]["started"] == 2
    assert len(d["jobs"]["running"]) == 1 and len(d["jobs"]["waiting"]) == 1
    assert d["jobs"]["waiting"][0]["queue_position"] == 1


def test_lifetime_counters_survive_restart(make_client, cfg):
    client, app = make_client()
    (jid,) = start(client, 1, kind="audio")
    assert until(lambda: job(client, jid)["stage"] == "ready")
    s = Registry(app.extensions["tinycrap"].cfg).stats()
    assert s["completed"] == 1 and s["started"] == 1 and s["bytes"] > 0 and s["platforms"] == {"youtube": 1}


def test_api_keys_flow(make_client):
    client, app = make_client()
    H2 = {**auth(app), **H}
    made = client.post("/admin/api/keys", json={"name": "Shop A", "per_minute": 2}, headers=H2).get_json()["key"]
    key = made["key"]
    assert key.startswith("tc_") and "hash" not in made
    # keys are not required until the switch is on
    assert client.post("/api/analyze", json={"url": YT}).status_code == 200
    client.post("/admin/api/settings", json={"require_key": True}, headers=H2)
    assert client.post("/api/analyze", json={"url": YT}).status_code == 401
    assert client.post("/api/analyze", json={"url": YT}, headers={"X-API-Key": "tc_wrong"}).status_code == 401
    assert client.get("/api/health").status_code == 200                       # public
    codes = [client.post("/api/analyze", json={"url": YT}, headers={"X-API-Key": key}).status_code for _ in range(3)]
    assert codes == [200, 200, 429]                                           # this customer's own limit
    listed = client.get("/admin/api/overview", headers=auth(app)).get_json()["keys"][0]
    assert listed["requests"] >= 3 and "hash" not in listed and "key" not in listed
    client.post(f"/admin/api/keys/{made['id']}/toggle", headers=H2)
    assert client.post("/api/analyze", json={"url": YT}, headers={"X-API-Key": key}).status_code == 401
    client.delete(f"/admin/api/keys/{made['id']}", headers=H2)
    assert client.get("/admin/api/overview", headers=auth(app)).get_json()["keys"] == []


def test_updates_flow(make_client):
    client, app = make_client()
    H2 = {**auth(app), **H}
    assert client.get("/api/update?code=1").get_json()["update"] is None
    bad = client.post("/admin/api/updates", json={"title": "x", "link": "javascript:alert(1)", "version_code": 3}, headers=H2)
    assert bad.status_code == 400
    client.post("/admin/api/updates", json={"title": "Faster app", "link": "https://example.com/tc3.apk", "version_code": 3, "version_name": "2.1"}, headers=H2)
    up = client.get("/api/update?code=2").get_json()["update"]
    assert up["required"] is True and up["title"] == "Faster app" and up["version_code"] == 3
    assert client.get("/api/update?code=3").get_json()["update"] is None      # installed → gone
    assert client.get("/api/update?code=9").get_json()["update"] is None
    assert client.get("/api/update?code=abc").get_json()["update"]["version_code"] == 3
    # still reachable when keys are required
    client.post("/admin/api/settings", json={"require_key": True}, headers=H2)
    assert client.get("/api/update?code=1").status_code == 200
    uid = client.get("/admin/api/overview", headers=auth(app)).get_json()["updates"][0]["id"]
    client.delete(f"/admin/api/updates/{uid}", headers=H2)
    assert client.get("/api/update?code=1").get_json()["update"] is None
