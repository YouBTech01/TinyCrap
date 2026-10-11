import dataclasses
import sys
import time
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

from tinycrap import Config, create_app  # noqa: E402

FAKE = str(Path(__file__).with_name("fake_ytdlp.py"))


@pytest.fixture()
def cfg(tmp_path):
    return Config(
        temp_dir=tmp_path / "dl",
        data_dir=tmp_path / "data",
        ytdlp_cmd=(sys.executable, FAKE),
        cleanup_minutes=1,
        rate_limit_per_min=1000,
        job_timeout=20,
        log_level="WARNING",
    )


@pytest.fixture()
def make_client(cfg):
    created = []

    def _make(mode="ok", **overrides):
        overrides.setdefault("ytdlp_cmd", (sys.executable, FAKE, "--fake-mode", mode))
        app = create_app(dataclasses.replace(cfg, **overrides))
        created.append(app)
        return app.test_client(), app

    yield _make
    for app in created:
        app.extensions["tinycrap"].store.shutdown()


@pytest.fixture()
def client(make_client):
    return make_client()[0]


def wait_done(client, job_id, timeout=20):
    deadline = time.time() + timeout
    seen = []
    while time.time() < deadline:
        job = client.get(f"/api/progress/{job_id}").get_json()["job"]
        if not seen or seen[-1] != job["stage"]:
            seen.append(job["stage"])
        if job["stage"] in ("ready", "error"):
            return job, seen
        time.sleep(0.05)
    raise AssertionError(f"job did not finish; stages seen: {seen}")
