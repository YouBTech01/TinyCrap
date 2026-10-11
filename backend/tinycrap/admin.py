"""Admin panel: one HTML page (Material Design Lite) plus a small JSON API under /admin/api.

Sign in with the browser's password box: user ``admin``, password from TINYCRAP_ADMIN_PASSWORD or
the one generated into ``data/admin_password.txt`` on first start (also printed in the console).
"""
from __future__ import annotations

import hmac
import platform as _platform
import secrets
import shutil
import sys
import time
from pathlib import Path

from flask import Blueprint, Response, jsonify, request

from .config import APP_NAME, APP_VERSION, Config
from .errors import ApiError
from .routes import svc

admin = Blueprint("admin", __name__, url_prefix="/admin")
PAGE = Path(__file__).with_name("static") / "admin.html"


def resolve_password(cfg: Config) -> str:
    if cfg.admin_password:
        return cfg.admin_password
    file = cfg.data_dir / "admin_password.txt"
    try:
        saved = file.read_text(encoding="utf-8").strip()
        if saved:
            return saved
    except OSError:
        pass
    password = secrets.token_urlsafe(9)
    try:
        file.write_text(password, encoding="utf-8")
    except OSError:
        pass
    return password


@admin.before_request
def _auth():
    s = svc()
    ip = request.remote_addr or "?"
    if s.limiter.blocked("admin:" + ip, 10):
        raise ApiError("rate_limited", "Too many wrong passwords. Wait a minute.", 429)
    auth = request.authorization
    ok = bool(auth) and auth.username == "admin" and hmac.compare_digest(
        (auth.password or "").encode(), s.admin_password.encode())
    if not ok:
        s.limiter.allow("admin:" + ip, 10)          # counts a failed try
        resp = Response("Sign in with user: admin", 401)
        resp.headers["WWW-Authenticate"] = 'Basic realm="Tiny Crap admin"'
        return resp
    # A page on another website must not be able to press these buttons for you.
    if request.method != "GET" and request.headers.get("X-Requested-With") != "tinycrap":
        raise ApiError("forbidden", "Missing header.", 403)
    return None


@admin.after_request
def _no_store(resp):
    resp.headers["Cache-Control"] = "no-store"
    resp.headers["X-Content-Type-Options"] = "nosniff"
    resp.headers["Referrer-Policy"] = "no-referrer"
    return resp


@admin.get("/")
def page():
    return Response(PAGE.read_text(encoding="utf-8"), mimetype="text/html")


@admin.get("/api/overview")
def overview():
    s = svc()
    tools = s.tools.public()
    try:
        free = shutil.disk_usage(s.cfg.temp_dir).free
    except OSError:
        free = 0
    return jsonify({
        "app": {"name": APP_NAME, "version": APP_VERSION, "uptime": int(time.time() - s.started_at),
                "python": _platform.python_version(), "os": _platform.system(),
                "frozen": bool(getattr(sys, "frozen", False)), "disk_free": free, **tools},
        "queue": s.store.queue_stats(),
        "limits": {"concurrent": s.cfg.max_concurrent_jobs, "queued": s.cfg.max_queued_jobs,
                   "per_client": s.cfg.max_jobs_per_client, "max_mb": s.cfg.max_filesize_mb,
                   "rate": s.cfg.rate_limit_per_min},
        "stats": s.registry.stats(),
        "jobs": s.store.snapshot(),
        "settings": {"require_key": bool(s.registry.setting("require_key")),
                     "env_key": bool(s.cfg.api_key)},
        "keys": s.registry.keys(),
        "updates": s.registry.updates(),
        "base": request.host_url.rstrip("/"),
    })


def _body() -> dict:
    data = request.get_json(silent=True)
    if not isinstance(data, dict):
        raise ApiError("invalid_request", "Send JSON.")
    return data


@admin.post("/api/keys")
def create_key():
    body = _body()
    rate = body.get("per_minute")
    try:
        rate = int(rate) if rate not in (None, "") else None
    except (TypeError, ValueError):
        raise ApiError("invalid_request", "Requests per minute must be a number.")
    if rate is not None and not 1 <= rate <= 10000:
        raise ApiError("invalid_request", "Requests per minute must be between 1 and 10000.")
    return jsonify({"ok": True, "key": svc().registry.create_key(body.get("name", ""), rate)})


@admin.post("/api/keys/<key_id>/toggle")
def toggle_key(key_id: str):
    return jsonify({"ok": svc().registry.toggle_key(key_id)})


@admin.delete("/api/keys/<key_id>")
def delete_key(key_id: str):
    return jsonify({"ok": svc().registry.delete_key(key_id)})


@admin.post("/api/settings")
def settings():
    body = _body()
    if "require_key" in body:
        svc().registry.set_setting("require_key", bool(body["require_key"]))
    return jsonify({"ok": True})


@admin.post("/api/updates")
def add_update():
    b = _body()
    entry = svc().registry.add_update(b.get("title"), b.get("link"), b.get("version_code"), b.get("version_name", ""))
    return jsonify({"ok": True, "update": entry})


@admin.delete("/api/updates/<update_id>")
def delete_update(update_id: str):
    return jsonify({"ok": svc().registry.delete_update(update_id)})


@admin.post("/api/jobs/<job_id>/cancel")
def cancel_job(job_id: str):
    s = svc()
    job = s.store.get(job_id)
    if job is not None and not job.finished:
        s.store.request_cancel(job)
    return jsonify({"ok": job is not None})
