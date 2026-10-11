# Tiny Crap

A tiny, fast social-media video and audio downloader for **Instagram, YouTube and Facebook**.
Two separate projects:

| Folder | What | Stack |
|---|---|---|
| [`backend/`](backend/README.md) | API server: analysis, download, merge/convert, temporary files | Python, Flask, yt-dlp, FFmpeg |
| [`android/`](android/README.md) | The app: single-screen Material 3 UI | Kotlin, Jetpack Compose |

## Quick start

```bash
# 1) backend (needs Python 3.9+, FFmpeg on PATH, and Node/Deno/Bun for YouTube)
cd backend
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python server.py                      # http://127.0.0.1:8000  (check /api/health)

# 2) app: open the android/ folder in Android Studio and press Run
#    then tap the gear icon → server address (emulator: http://10.0.2.2:8000)
```

Flow: paste (or share) a link → platform detected → thumbnail and details → Video / Audio tabs with the
qualities that really exist → one tap → live progress → file saved on the phone → server auto-deletes its copy.

No database, no login, no history. Public content only; no DRM, login or paywall bypass.
Deploying the backend on cPanel is covered in `backend/README.md`.
