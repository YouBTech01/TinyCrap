# Tiny Crap — Backend (Python + Flask)

API server used by the Tiny Crap Android app. It uses **yt-dlp** to read public
Instagram / YouTube / Facebook links and **FFmpeg** to merge and convert media.
No database, no accounts: job state is temporary and files are auto-deleted.

## Run locally

```bash
python -m venv .venv
source .venv/bin/activate            # Windows: .venv\Scripts\activate
pip install -r requirements.txt
python server.py                     # http://127.0.0.1:8000
```

* Android **emulator** reaches it at `http://10.0.2.2:8000`.
* A **real phone** on the same Wi-Fi: run `python server.py --host 0.0.0.0`
  and use the LAN address it prints (e.g. `http://192.168.1.20:8000`).
* Check `http://127.0.0.1:8000/api/health` — it tells you if yt-dlp or FFmpeg is missing.

### Requirements

| Needed | Why |
|---|---|
| Python 3.9+ | runtime |
| FFmpeg + ffprobe on `PATH` (or `TINYCRAP_FFMPEG_PATH`) | merge video+audio, convert audio, verify files. It is an external program, **not** a pip package |
| Node.js, Deno or Bun | recent yt-dlp needs a JavaScript runtime for full YouTube support |

Startup prints a diagnostic if anything is missing. Keep yt-dlp fresh — platforms
change often and extractors break: `pip install -U yt-dlp`.

## Queue, admin panel and updates (v2)

* **Only 2 downloads run at once, server-wide** (`TINYCRAP_MAX_CONCURRENT_JOBS`). Everyone else waits in a
  first-come-first-served line (up to `TINYCRAP_MAX_QUEUED_JOBS`, default 200) and starts automatically when a
  place frees up. `GET /api/progress/<id>` returns `queue_position` (0 = running, 1 = next). The limit also holds
  when the host runs several worker processes. One client (IP) can hold at most `TINYCRAP_MAX_JOBS_PER_CLIENT` (8).
  Links are read (`/api/analyze`) three at a time, and the same link pasted by many people is read only once.
* **Admin panel:** open `http://<server>:8000/admin` — user `admin`, password from `TINYCRAP_ADMIN_PASSWORD`
  (or the one generated into `data/admin_password.txt`; it is also printed when the server starts). It shows health,
  lifetime download counts, running/waiting downloads, lets you create API keys (with API documentation) and publish an
  app update. It loads Material Design Lite from a CDN, so the browser needs internet.
* **API keys:** create keys in the panel; turn on *Require an API key* when customers should use them. Each key can have
  its own requests-per-minute. `/api/health` and `/api/update` never need a key.
* **Forced app update:** publish *title + link + version code*. Any app whose version code is lower shows a full-screen
  "Update" wall with no way to skip it; it disappears once the new APK (higher version code) is installed.
  Bump `versionCode` in `android/app/build.gradle.kts` for every build you hand out.
* Data kept across restarts (counters, keys, updates) lives in `data/state.json` (`TINYCRAP_DATA_DIR`). Back it up if you care.

## API

All responses are JSON: `{"ok": true, ...}` or `{"ok": false, "error": {"code", "message"}}`.

| Method | Path | Body | Result |
|---|---|---|---|
| GET | `/api/update?code=N` | — | `update`: `null` or `{title, link, version_code, required}` |
| POST | `/api/analyze` | `{"url"}` | `media`: title, thumbnail, duration, platform, `video.qualities[]`, `audio.formats[]` |
| POST | `/api/download/video` | `{"url","quality":"best"\|"1080"…,"container":"mp4"\|"webm"}` | `202` + `job` |
| POST | `/api/download/audio` | `{"url","format":"best"\|"mp3"\|"m4a"\|"opus"\|"wav"\|"flac","bitrate":192}` | `202` + `job` |
| GET | `/api/progress/<job_id>` | | `job`: `stage`, `percent`, `speed`, `eta`, `error`, `result` |
| GET | `/api/download/<job_id>` | | the file (supports HTTP Range) |
| POST | `/api/cancel/<job_id>` | | stops a running job |
| GET | `/api/health` | | tool status, no secrets |

Stages: `preparing → analyzing → downloading → processing → finalizing → ready` (or `error`).
`percent` is real download progress (merging/conversion shows as "processing").

Error codes you may see: `invalid_url`, `unsupported`, `login_required`, `unavailable`,
`age_restricted`, `geo_blocked`, `live`, `too_long`, `too_large`, `format_unavailable`,
`rate_limited`, `platform_blocked`, `busy`, `timeout`, `ffmpeg_missing`, `unauthorized`.

## Configuration

Environment variables (or a `.env` file, see `.env.example`):

| Variable | Default | Meaning |
|---|---|---|
| `TINYCRAP_TEMP_DIR` | system temp / `tinycrap` | where temporary media lives |
| `TINYCRAP_CLEANUP_MINUTES` | 30 | finished jobs + files deleted after this |
| `TINYCRAP_MAX_CONCURRENT_JOBS` / `_QUEUED_JOBS` | 2 / 4 | concurrency; beyond that → HTTP 429 |
| `TINYCRAP_MAX_FILESIZE_MB` | 500 | hard size cap |
| `TINYCRAP_MAX_DURATION_SEC` | 10800 | refuse longer media |
| `TINYCRAP_API_KEY` | unset | if set, clients must send `X-API-Key` |
| `TINYCRAP_TRUST_PROXY` | 0 | set to 1 behind a reverse proxy |
| `TINYCRAP_FFMPEG_PATH` / `_FFPROBE_PATH` / `_JS_RUNTIME` | auto | tool locations |

## Security notes

* Only `http(s)` links on Instagram/YouTube/Facebook hosts are accepted (no IPs, no
  `file://`, no credentials, no custom ports) — this blocks SSRF and local-file access.
* yt-dlp runs as a child process with an argument list (never a shell), `--ignore-config`,
  `--` before the URL, a scrubbed environment, a timeout and a size cap.
* The client never sends raw yt-dlp format strings; options are validated against fixed
  lists and the selector is built server-side.
* Files are served only from inside their own job directory; job IDs are random.
* No DRM/login/paywall bypass: private or protected media fails with a clear message.

## Deploy on cPanel (Application Manager / Passenger)

Works only if your host offers **Python apps with Passenger**. Shared hosts often lack
FFmpeg and limit CPU/RAM/process time, and some forbid downloader services — check first.

1. Upload this folder (e.g. `~/tinycrap-backend`).
2. cPanel → **Application Manager** (or *Setup Python App*) → *Register/Create*:
   Application root `tinycrap-backend`, Python 3.9+, **startup file `passenger_wsgi.py`**,
   **entry point `application`**.
3. In the app's virtualenv: `pip install -r requirements.txt`.
4. FFmpeg: if the host has none, upload a static build to `~/bin/` and set
   `TINYCRAP_FFMPEG_PATH=/home/USER/bin`. Likewise a Deno/Node binary → `TINYCRAP_JS_RUNTIME`.
5. Set environment variables (at least `TINYCRAP_TEMP_DIR=/home/USER/tinycrap_tmp`,
   and `TINYCRAP_API_KEY`, `TINYCRAP_TRUST_PROXY=1` for a public server). Restart the app.
6. Open `https://your-domain/api/health` — `"status": "ok"` means everything is found.
7. Put that HTTPS URL in the Android app's server settings.

Job state is mirrored to disk, so progress checks work even if Passenger runs several
worker processes. Concurrency limits apply per worker process.

## Tests

```bash
pip install -r requirements-dev.txt
python -m pytest -q
```
The tests use a fake yt-dlp (no network) but real FFmpeg for merging/conversion.

## Responsible use

For public content you are allowed to save. Respect the platforms' terms and copyright.
