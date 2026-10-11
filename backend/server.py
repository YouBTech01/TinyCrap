#!/usr/bin/env python3
"""Tiny Crap backend: single entry point.

    python server.py                      # http://127.0.0.1:8000
    python server.py --host 0.0.0.0       # reachable from a phone on the same Wi-Fi

``app`` / ``application`` are also exposed for WSGI hosts (see passenger_wsgi.py).
"""
from __future__ import annotations

import argparse
import os
import socket

from tinycrap import APP_NAME, APP_VERSION, create_app

app = create_app()
application = app  # WSGI servers look for this name


def _lan_ip() -> str:
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
            s.connect(("10.255.255.255", 1))
            return s.getsockname()[0]
    except OSError:
        return "your-pc-ip"


def main() -> None:
    parser = argparse.ArgumentParser(description=f"{APP_NAME} backend")
    parser.add_argument("--host", default=os.environ.get("HOST", "127.0.0.1"))
    parser.add_argument("--port", type=int, default=int(os.environ.get("PORT", "8000")))
    parser.add_argument("--debug", action="store_true", help="Flask dev server with debug (not for production)")
    args = parser.parse_args()

    print(f"\n{APP_NAME} backend v{APP_VERSION}")
    print(f"  Local:             http://127.0.0.1:{args.port}")
    print(f"  Android emulator:  http://10.0.2.2:{args.port}")
    if args.host in ("0.0.0.0", "::"):
        print(f"  Phone on Wi-Fi:    http://{_lan_ip()}:{args.port}")
    else:
        print("  (use --host 0.0.0.0 to reach it from a real phone)")
    print(f"  Health check:      /api/health")
    print(f"  Admin panel:       http://127.0.0.1:{args.port}/admin   (user: admin)")
    print(f"  Admin password:    {app.extensions['tinycrap'].admin_password}\n")

    if args.debug:
        app.run(host=args.host, port=args.port, debug=True, use_reloader=False)
        return
    try:
        from waitress import serve
    except ImportError:
        print("waitress not installed; falling back to Flask's development server.")
        app.run(host=args.host, port=args.port, threaded=True)
        return
    serve(app, host=args.host, port=args.port, threads=24, channel_timeout=120,
          connection_limit=500, backlog=256)


if __name__ == "__main__":
    main()
