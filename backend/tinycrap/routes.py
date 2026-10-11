"""HTTP API.

POST /api/analyze            {url}
POST /api/download/video     {url, quality, container}
POST /api/download/audio     {url, format, bitrate}
GET  /api/progress/<job_id>
GET  /api/download/<job_id>
POST /api/cancel/<job_id>
GET  /api/health
GET  /api/update?code=<installed versionCode>
"""
from __future__ import annotations

import hmac
import os
from dataclasses import dataclass
from pathlib import Path

from flask import Blueprint, current_app, jsonify, request, send_file

from . import downloader
from .config import APP_NAME, APP_VERSION, Config
from .diagnostics import Tools
from .errors import ApiError
from .extractor import Analyzer
from .jobs import JobStore
from .ratelimit import RateLimiter
from .registry import Registry
from .validation import validate_url

api = Blueprint("api", __name__, url_prefix="/api")


@dataclass
class Services:
    cfg: Config
    tools: Tools
    store: JobStore
    analyzer: Analyzer
    limiter: RateLimiter
    registry: Registry
    admin_password: str = ""
    started_at: float = 0.0


def svc() -> Services:
    return current_app.extensions["tinycrap"]


def _client_ip() -> str:
    return request.remote_addr or "unknown"


def _json_body() -> dict:
    data = request.get_json(silent=True)
    if not isinstance(data, dict):
        raise ApiError("invalid_request", "Send a JSON body.")
    return data


PUBLIC_ENDPOINTS = {"api.health", "api.update"}     # work without a key, so apps can always see an update


@api.before_request
def _guard():
    s = svc()
    if request.endpoint in PUBLIC_ENDPOINTS:
        return None
    limit_key, limit = _client_ip(), 0
    if s.cfg.api_key or s.registry.setting("require_key"):
        sent = request.headers.get("X-API-Key", "")
        shared = bool(s.cfg.api_key) and hmac.compare_digest(sent.encode(), s.cfg.api_key.encode())
        if not shared:
            key = s.registry.find_key(sent)
            if key is None:
                raise ApiError("unauthorized", "Missing or wrong API key.", 401)
            s.registry.touch_key(key["id"])
            limit_key, limit = "key:" + key["id"], key["per_minute"] or 0   # a customer's own allowance
    if request.method == "POST" and not s.limiter.allow(limit_key, limit):
        raise ApiError("rate_limited", "Too many requests. Wait a minute and try again.", 429)
    return None


@api.after_request
def _headers(resp):
    resp.headers["X-Content-Type-Options"] = "nosniff"
    if resp.mimetype == "application/json":
        resp.headers["Cache-Control"] = "no-store"
    return resp


@api.get("/health")
def health():
    s = svc()
    return jsonify({"ok": True, "app": APP_NAME, "version": APP_VERSION, **s.tools.public(),
                    "queue": s.store.queue_stats()})


@api.get("/update")
def update():
    """Tells an app whether it must update. The app sends the version code it is running."""
    try:
        code = int(request.args.get("code", "0"))
    except ValueError:
        code = 0
    return jsonify({"ok": True, "update": svc().registry.update_for(code)})


@api.post("/analyze")
def analyze():
    s = svc()
    url, platform = validate_url(_json_body().get("url"))
    result = s.analyzer.analyze(url, platform)
    return jsonify({"ok": True, "media": result})


def _start(kind: str, validator):
    s = svc()
    body = _json_body()
    url, platform = validate_url(body.get("url"))
    options = validator(body)
    job = s.store.submit(kind, url, platform, options, downloader.make_runner(s.store, s.cfg, s.tools),
                         owner=_client_ip())
    resp = jsonify({"ok": True, "job": job.public()})
    resp.status_code = 202
    return resp


@api.post("/download/video")
def download_video():
    return _start("video", downloader.validate_video_request)


@api.post("/download/audio")
def download_audio():
    return _start("audio", downloader.validate_audio_request)


@api.get("/progress/<job_id>")
def progress(job_id: str):
    job = svc().store.get(job_id)
    if job is None:
        raise ApiError("not_found", "This download expired or does not exist.", 404)
    return jsonify({"ok": True, "job": job.public()})


@api.post("/cancel/<job_id>")
def cancel(job_id: str):
    s = svc()
    job = s.store.get(job_id)
    if job is None:
        raise ApiError("not_found", "This download expired or does not exist.", 404)
    if not job.finished:
        s.store.request_cancel(job)
    return jsonify({"ok": True, "job": job.public()})


@api.get("/download/<job_id>")
def fetch_file(job_id: str):
    s = svc()
    job = s.store.get(job_id)
    if job is None:
        raise ApiError("not_found", "This download expired or does not exist.", 404)
    if job.stage != "ready" or not job.filepath or not job.result:
        raise ApiError("not_ready", "This download is not ready yet.", 409)

    # Only ever serve a file that sits inside this job's own directory.
    path = Path(job.filepath).resolve()
    job_dir = job.dir.resolve()
    try:
        inside = os.path.commonpath([str(path), str(job_dir)]) == str(job_dir)
    except ValueError:
        inside = False
    if not inside or not path.is_file():
        raise ApiError("not_found", "This download expired or does not exist.", 404)

    return send_file(
        path,
        mimetype=job.result.get("mime") or "application/octet-stream",
        as_attachment=True,
        download_name=job.result.get("filename") or path.name,
        conditional=True,       # HTTP Range support so Android can resume
        max_age=0,
    )
