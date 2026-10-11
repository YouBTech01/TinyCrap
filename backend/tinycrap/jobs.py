"""Temporary job state and the download queue. No database.

Only ``max_concurrent_jobs`` downloads run at the same time, server-wide. Everybody else waits in a
first-come-first-served line and starts the moment a place frees up. The limit holds even when the
host runs several worker processes: each running job holds a small "slot" file in the temp folder.

Jobs live in memory and are mirrored to ``<temp_dir>/<job_id>/state.json`` so a status request that
lands on a different worker process (possible under Passenger) can still answer. Finished jobs and
their files are deleted after ``cleanup_minutes``.
"""
from __future__ import annotations

import json
import logging
import os
import re
import secrets
import shutil
import subprocess
import threading
import time
from collections import deque
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Callable, Deque, Dict, List, Optional

from .config import Config
from .errors import ApiError
from .registry import Registry

log = logging.getLogger("tinycrap.jobs")

STAGES = ["preparing", "analyzing", "downloading", "processing", "finalizing", "ready"]
FINAL_STAGES = ("ready", "error")
JOB_ID_RE = re.compile(r"^[A-Za-z0-9_-]{8,40}$")
STATE_FILE = "state.json"
CANCEL_FILE = "cancel.flag"

STAGE_MESSAGES = {
    "preparing": "Preparing…",
    "analyzing": "Reading media details…",
    "downloading": "Downloading…",
    "processing": "Processing…",
    "finalizing": "Finalizing…",
    "ready": "Ready",
}


@dataclass
class Job:
    id: str
    kind: str                      # "video" | "audio"
    url: str
    platform: str
    options: Dict[str, Any]
    dir: Path
    stage: str = "preparing"
    percent: float = 0.0
    message: str = "Preparing…"
    speed: Optional[float] = None
    eta: Optional[float] = None
    downloaded: Optional[int] = None
    total: Optional[int] = None
    error: Optional[Dict[str, str]] = None
    result: Optional[Dict[str, Any]] = None
    filepath: Optional[str] = None
    queue_position: int = 0        # 1 = next in line; 0 = not waiting
    created: float = field(default_factory=time.time)
    updated: float = field(default_factory=time.time)
    finished_at: Optional[float] = None
    owner: Optional[str] = field(default=None, repr=False, compare=False)
    runner: Optional[Callable[["Job"], None]] = field(default=None, repr=False, compare=False)
    cancel: threading.Event = field(default_factory=threading.Event, repr=False, compare=False)
    proc: Optional[subprocess.Popen] = field(default=None, repr=False, compare=False)
    _flushed_at: float = field(default=0.0, repr=False, compare=False)

    @property
    def finished(self) -> bool:
        return self.stage in FINAL_STAGES

    def to_state(self) -> Dict[str, Any]:
        return {
            "id": self.id, "kind": self.kind, "url": self.url, "platform": self.platform,
            "options": self.options, "stage": self.stage, "percent": self.percent,
            "message": self.message, "speed": self.speed, "eta": self.eta,
            "downloaded": self.downloaded, "total": self.total, "error": self.error,
            "result": self.result, "filepath": self.filepath, "created": self.created,
            "updated": self.updated, "finished_at": self.finished_at,
            "queue_position": self.queue_position,
        }

    def public(self) -> Dict[str, Any]:
        """What GET /api/progress/<id> returns."""
        stage_index = STAGES.index(self.stage) if self.stage in STAGES else -1
        data: Dict[str, Any] = {
            "job_id": self.id,
            "kind": self.kind,
            "stage": self.stage,
            "stage_index": stage_index,
            "stages": STAGES,
            "percent": round(self.percent, 1),
            "queue_position": self.queue_position,
            "message": self.message,
            "speed": self.speed,
            "eta": self.eta,
            "downloaded": self.downloaded,
            "total": self.total,
            "error": self.error,
            "result": None,
        }
        if self.stage == "ready" and self.result:
            data["result"] = {**self.result, "download_url": f"/api/download/{self.id}"}
        return data


class SlotPool:
    """``size`` places shared by every process using this folder. A place is a file that is created
    exclusively, kept fresh while the job runs, and treated as abandoned if nobody touches it."""

    def __init__(self, directory: Path, size: int, stale_after: float = 45.0):
        self.dir = directory
        self.size = size
        self.stale_after = stale_after
        directory.mkdir(parents=True, exist_ok=True)

    def _path(self, index: int) -> Path:
        return self.dir / f"slot-{index}.lock"

    def acquire(self) -> Optional[int]:
        for i in range(self.size):
            path = self._path(i)
            for _ in range(2):
                try:
                    fd = os.open(path, os.O_CREAT | os.O_EXCL | os.O_WRONLY)
                except FileExistsError:
                    try:
                        if time.time() - path.stat().st_mtime > self.stale_after:
                            path.unlink()      # left behind by a crashed process
                            continue
                    except OSError:
                        pass
                    break
                except OSError:
                    break
                with os.fdopen(fd, "w") as fh:
                    fh.write(str(os.getpid()))
                return i
        return None

    def touch(self, index: int) -> None:
        try:
            os.utime(self._path(index))
        except OSError:
            pass

    def release(self, index: int) -> None:
        try:
            self._path(index).unlink()
        except OSError:
            pass

    def in_use(self) -> int:
        now, used = time.time(), 0
        for i in range(self.size):
            try:
                if now - self._path(i).stat().st_mtime <= self.stale_after:
                    used += 1
            except OSError:
                pass
        return used


class JobStore:
    def __init__(self, cfg: Config, registry: Optional[Registry] = None):
        self.cfg = cfg
        self.registry = registry
        self.cfg.temp_dir.mkdir(parents=True, exist_ok=True)
        try:
            os.chmod(self.cfg.temp_dir, 0o700)
        except OSError:
            pass
        self._jobs: Dict[str, Job] = {}
        self._lock = threading.RLock()
        self._cond = threading.Condition(self._lock)
        self._queue: Deque[Job] = deque()
        self._running: Dict[str, int] = {}
        self._slots = SlotPool(cfg.temp_dir / ".slots", cfg.max_concurrent_jobs)
        self._cleaner: Optional[threading.Thread] = None
        self._stop = threading.Event()
        self._dispatcher = threading.Thread(target=self._dispatch, name="tc-dispatch", daemon=True)
        self._dispatcher.start()

    # -- creation ---------------------------------------------------------------
    def submit(self, kind: str, url: str, platform: str, options: Dict[str, Any],
               runner: Callable[[Job], None], owner: Optional[str] = None) -> Job:
        with self._lock:
            live = [j for j in self._jobs.values() if not j.finished]
            if len(live) >= self.cfg.max_concurrent_jobs + self.cfg.max_queued_jobs:
                raise ApiError("busy", "The server is very busy right now. Try again in a few minutes.", 429)
            if owner and sum(1 for j in live if j.owner == owner) >= self.cfg.max_jobs_per_client:
                raise ApiError("too_many", "You already have several downloads in progress. "
                               "Wait for one to finish, then add more.", 429)
            job_id = secrets.token_urlsafe(12)
            job_dir = self.cfg.temp_dir / job_id
            job_dir.mkdir(mode=0o700)
            job = Job(id=job_id, kind=kind, url=url, platform=platform,
                      options=options, dir=job_dir, owner=owner, runner=runner)
            job.message = "Waiting in queue…"
            self._jobs[job_id] = job
            self._queue.append(job)
            self._reindex()
            self._cond.notify_all()
        if self.registry:
            self.registry.started(platform, kind)
        self._flush(job, force=True)
        return job

    # -- the line -------------------------------------------------------------------
    def _reindex(self) -> None:
        """Numbers the waiting jobs 1, 2, 3 … (call with the lock held)."""
        for i, job in enumerate(self._queue, start=1):
            if job.queue_position != i:
                job.queue_position = i
                self._flush(job, force=True)

    def _dispatch(self) -> None:
        """Hands free places to the jobs at the front of the line, one at a time, oldest first."""
        last_beat = 0.0
        while not self._stop.is_set():
            started: List[Job] = []
            with self._cond:
                self._cond.wait(timeout=0.5)
                while self._queue and not self._stop.is_set():
                    job = self._queue[0]
                    if job.cancel.is_set():
                        self._queue.popleft()
                        job.queue_position = 0
                        self.fail(job, "cancelled", "Download cancelled.")
                        continue
                    slot = self._slots.acquire()
                    if slot is None:
                        break                          # all places are taken: keep waiting
                    self._queue.popleft()
                    job.queue_position = 0
                    self._running[job.id] = slot
                    started.append(job)
                self._reindex()
                now = time.monotonic()
                if now - last_beat > 5:
                    last_beat = now
                    for slot in self._running.values():
                        self._slots.touch(slot)
            for job in started:
                threading.Thread(target=self._work, args=(job,), name="tc-job", daemon=True).start()

    def _work(self, job: Job) -> None:
        try:
            job.queue_position = 0
            self.update(job, message="Starting…")
            self._flush(job, force=True)
            if job.runner is not None:
                self._run_safely(job.runner, job)
        finally:
            with self._cond:
                slot = self._running.pop(job.id, None)
                if slot is not None:
                    self._slots.release(slot)
                self._cond.notify_all()

    def _run_safely(self, runner: Callable[[Job], None], job: Job) -> None:
        try:
            runner(job)
        except Exception:  # last-resort guard so a job never stays "running" forever
            log.exception("job %s crashed", job.id)
            self.fail(job, "internal", "Something went wrong while processing this download.")

    def queue_stats(self) -> Dict[str, int]:
        return {"running": len(self._running), "waiting": len(self._queue),
                "places": self.cfg.max_concurrent_jobs, "places_in_use": self._slots.in_use()}

    def snapshot(self, recent: int = 12) -> Dict[str, List[Dict[str, Any]]]:
        """Running, waiting and recently finished jobs, read from disk so every worker process shows up."""
        now = time.time()
        stale = self.cfg.job_timeout + 120
        running: List[Dict[str, Any]] = []
        waiting: List[Dict[str, Any]] = []
        done: List[Dict[str, Any]] = []
        try:
            entries = list(self.cfg.temp_dir.iterdir())
        except OSError:
            entries = []
        for entry in entries:
            if not entry.is_dir() or not JOB_ID_RE.match(entry.name):
                continue
            try:
                st = json.loads((entry / STATE_FILE).read_text(encoding="utf-8"))
            except (OSError, ValueError):
                continue
            row = {"id": st.get("id"), "kind": st.get("kind"), "platform": st.get("platform"),
                   "url": str(st.get("url", ""))[:90], "stage": st.get("stage"),
                   "percent": round(st.get("percent") or 0, 1), "message": st.get("message", ""),
                   "queue_position": st.get("queue_position", 0), "created": st.get("created"),
                   "finished_at": st.get("finished_at"),
                   "size": (st.get("result") or {}).get("size"),
                   "error": (st.get("error") or {}).get("message")}
            if st.get("stage") in FINAL_STAGES:
                done.append(row)
            elif now - (st.get("updated") or 0) > stale:
                continue
            elif row["queue_position"]:
                waiting.append(row)
            else:
                running.append(row)
        running.sort(key=lambda r: r["created"] or 0)
        waiting.sort(key=lambda r: r["queue_position"])
        done.sort(key=lambda r: r["finished_at"] or 0, reverse=True)
        return {"running": running, "waiting": waiting, "recent": done[:recent]}

    # -- lookup -------------------------------------------------------------------
    def get(self, job_id: str) -> Optional[Job]:
        if not isinstance(job_id, str) or not JOB_ID_RE.match(job_id):
            return None
        with self._lock:
            job = self._jobs.get(job_id)
        if job is not None:
            return job
        return self._load_from_disk(job_id)

    def _load_from_disk(self, job_id: str) -> Optional[Job]:
        job_dir = self.cfg.temp_dir / job_id
        try:
            data = json.loads((job_dir / STATE_FILE).read_text(encoding="utf-8"))
        except (OSError, ValueError):
            return None
        try:
            return Job(
                id=data["id"], kind=data["kind"], url=data["url"], platform=data["platform"],
                options=data.get("options") or {}, dir=job_dir, stage=data["stage"],
                percent=data.get("percent", 0.0), message=data.get("message", ""),
                speed=data.get("speed"), eta=data.get("eta"), downloaded=data.get("downloaded"),
                total=data.get("total"), error=data.get("error"), result=data.get("result"),
                filepath=data.get("filepath"), created=data.get("created", time.time()),
                updated=data.get("updated", time.time()), finished_at=data.get("finished_at"),
                queue_position=data.get("queue_position", 0),
            )
        except (KeyError, TypeError):
            return None

    # -- updates ------------------------------------------------------------------
    def update(self, job: Job, **fields: Any) -> None:
        stage_before = job.stage
        for k, v in fields.items():
            setattr(job, k, v)
        if "stage" in fields and "message" not in fields:
            job.message = STAGE_MESSAGES.get(job.stage, job.message)
        job.updated = time.time()
        self._flush(job, force=job.stage != stage_before)

    def fail(self, job: Job, code: str, message: str) -> None:
        if job.stage == "error":
            return
        job.queue_position = 0
        job.error = {"code": code, "message": message}
        job.stage = "error"
        job.message = message
        job.speed = job.eta = None
        job.finished_at = job.updated = time.time()
        # Do not keep partial files around.
        for p in job.dir.glob("*"):
            if p.name not in (STATE_FILE, CANCEL_FILE):
                _remove(p)
        self._flush(job, force=True)
        if self.registry:
            self.registry.failed(cancelled=code == "cancelled")

    def ready(self, job: Job, filepath: Path, filename: str, size: int, mime: str,
              duration: Optional[float]) -> None:
        job.filepath = str(filepath)
        job.result = {"filename": filename, "size": size, "mime": mime,
                      "duration": round(duration, 1) if duration else None}
        job.stage = "ready"
        job.percent = 100.0
        job.message = "Ready"
        job.speed = job.eta = None
        job.finished_at = job.updated = time.time()
        self._flush(job, force=True)
        if self.registry:
            self.registry.finished(size)

    def request_cancel(self, job: Job) -> None:
        job.cancel.set()
        with self._cond:
            if job in self._queue:                    # still waiting: just leave the line
                self._queue.remove(job)
                self._reindex()
                self.fail(job, "cancelled", "Download cancelled.")
                return
        try:
            (job.dir / CANCEL_FILE).write_text("1", encoding="utf-8")  # reaches other workers
        except OSError:
            pass
        proc = job.proc
        if proc is not None and proc.poll() is None:
            try:
                proc.kill()
            except OSError:
                pass

    def cancel_requested(self, job: Job) -> bool:
        return job.cancel.is_set() or (job.dir / CANCEL_FILE).exists()

    def _flush(self, job: Job, force: bool = False) -> None:
        now = time.monotonic()
        if not force and now - job._flushed_at < 0.5:
            return
        job._flushed_at = now
        path = job.dir / STATE_FILE
        tmp = job.dir / (STATE_FILE + ".tmp")
        try:
            tmp.write_text(json.dumps(job.to_state()), encoding="utf-8")
            os.replace(tmp, path)
        except OSError:
            pass  # directory may have been swept; state in memory is still valid

    # -- cleanup ------------------------------------------------------------------
    def sweep(self) -> int:
        """Delete expired job directories. Returns how many were removed."""
        ttl = self.cfg.cleanup_minutes * 60
        stuck_after = self.cfg.job_timeout + ttl
        now = time.time()
        removed = 0
        try:
            entries = list(self.cfg.temp_dir.iterdir())
        except OSError:
            return 0
        for entry in entries:
            if not entry.is_dir() or not JOB_ID_RE.match(entry.name):
                continue
            expired = False
            try:
                state = json.loads((entry / STATE_FILE).read_text(encoding="utf-8"))
                finished_at = state.get("finished_at")
                updated = state.get("updated", 0)
                if state.get("queue_position"):          # waiting in line: not stuck, just patient
                    expired = now - state.get("created", now) > 6 * 3600
                else:
                    expired = bool(finished_at and now - finished_at > ttl) or now - updated > stuck_after
            except (OSError, ValueError):
                try:
                    expired = now - entry.stat().st_mtime > ttl
                except OSError:
                    continue
            if expired:
                shutil.rmtree(entry, ignore_errors=True)
                with self._lock:
                    self._jobs.pop(entry.name, None)
                removed += 1
        if removed:
            log.info("cleanup removed %d expired job(s)", removed)
        return removed

    def start_cleaner(self) -> None:
        if self._cleaner is not None:
            return
        interval = max(15, min(60, self.cfg.cleanup_minutes * 30))

        def loop() -> None:
            self.sweep()  # also clears leftovers from a previous run
            while not self._stop.wait(interval):
                try:
                    self.sweep()
                except Exception:
                    log.exception("cleanup failed")

        self._cleaner = threading.Thread(target=loop, name="tc-cleaner", daemon=True)
        self._cleaner.start()

    def shutdown(self) -> None:
        self._stop.set()
        with self._cond:
            self._cond.notify_all()
            for slot in list(self._running.values()):
                self._slots.release(slot)
            self._running.clear()
        if self.registry:
            self.registry.flush()


def _remove(path: Path) -> None:
    try:
        if path.is_dir():
            shutil.rmtree(path, ignore_errors=True)
        else:
            path.unlink()
    except OSError:
        pass
