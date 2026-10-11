"""Tiny Crap backend: a small Flask API around yt-dlp + FFmpeg."""
from __future__ import annotations

import logging
import time
from typing import Optional

from flask import Flask, jsonify
from werkzeug.exceptions import HTTPException

from .config import APP_NAME, APP_VERSION, Config
from .diagnostics import check_environment
from .errors import ApiError
from .extractor import Analyzer
from .jobs import JobStore
from .ratelimit import RateLimiter
from .registry import Registry
from .routes import Services, api
from .admin import admin, resolve_password

log = logging.getLogger("tinycrap")

__all__ = ["create_app", "Config", "APP_NAME", "APP_VERSION"]


def create_app(config: Optional[Config] = None) -> Flask:
    cfg = config or Config.from_env()
    logging.basicConfig(
        level=getattr(logging, cfg.log_level, logging.INFO),
        format="%(asctime)s %(levelname)-7s %(name)s: %(message)s",
    )

    app = Flask(__name__)
    app.config["MAX_CONTENT_LENGTH"] = 16 * 1024      # request bodies are tiny JSON
    app.json.sort_keys = False

    if cfg.trust_proxy:
        from werkzeug.middleware.proxy_fix import ProxyFix
        app.wsgi_app = ProxyFix(app.wsgi_app, x_for=1, x_proto=1, x_host=1)

    cfg.temp_dir.mkdir(parents=True, exist_ok=True)
    tools = check_environment(cfg)
    registry = Registry(cfg)
    store = JobStore(cfg, registry)
    app.extensions["tinycrap"] = Services(
        cfg=cfg, tools=tools, store=store, analyzer=Analyzer(cfg, tools),
        limiter=RateLimiter(cfg.rate_limit_per_min), registry=registry,
        admin_password=resolve_password(cfg), started_at=time.time(),
    )
    app.register_blueprint(api)
    app.register_blueprint(admin)
    store.start_cleaner()

    @app.get("/")
    def index():
        return jsonify({
            "app": APP_NAME, "version": APP_VERSION, "status": "running",
            "endpoints": ["/api/health", "/api/analyze", "/api/download/video",
                          "/api/download/audio", "/api/progress/<job_id>",
                          "/api/download/<job_id>", "/api/cancel/<job_id>", "/api/update", "/admin"],
        })

    @app.errorhandler(ApiError)
    def _api_error(err: ApiError):
        resp = jsonify({"ok": False, "error": err.to_dict()})
        resp.status_code = err.status
        return resp

    @app.errorhandler(HTTPException)
    def _http_error(err: HTTPException):
        message = {
            404: "Not found.", 405: "Method not allowed.",
            413: "Request too large.", 415: "Send JSON.",
        }.get(err.code or 500, err.description or "Request failed.")
        resp = jsonify({"ok": False, "error": {"code": "http_error", "message": message}})
        resp.status_code = err.code or 500
        return resp

    @app.errorhandler(Exception)
    def _unhandled(err: Exception):
        log.exception("unhandled error")
        resp = jsonify({"ok": False, "error": {
            "code": "internal", "message": "Something went wrong on the server."}})
        resp.status_code = 500
        return resp

    return app
