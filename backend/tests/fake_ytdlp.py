"""Stand-in for ``python -m yt_dlp`` used by the tests (no network needed).

Understands the subset of options Tiny Crap passes. ``--fake-mode`` picks a
behaviour: ok (default) | private | slow | huge.
"""
import json
import os
import subprocess
import sys
import time

args = sys.argv[1:]
url = args[-1]


def opt(name, default=None):
    return args[args.index(name) + 1] if name in args else default


mode = opt("--fake-mode", "ok")


INFO = {
    "id": "abc123", "title": "Test: Video / Title?", "extractor_key": "Youtube",
    "uploader": "Tester", "duration": 4, "thumbnail": "https://img.example/t.jpg",
    "formats": [
        {"format_id": "137", "ext": "mp4", "vcodec": "avc1.640028", "acodec": "none",
         "height": 1080, "width": 1920, "fps": 30, "tbr": 4000, "filesize": 2_000_000, "url": "u"},
        {"format_id": "136", "ext": "mp4", "vcodec": "avc1.4d401f", "acodec": "none",
         "height": 720, "width": 1280, "fps": 30, "tbr": 2000, "filesize": 1_000_000, "url": "u"},
        {"format_id": "248", "ext": "webm", "vcodec": "vp09.00.40.08", "acodec": "none",
         "height": 1080, "width": 1920, "fps": 30, "tbr": 2500, "filesize": 1_500_000, "url": "u"},
        {"format_id": "140", "ext": "m4a", "vcodec": "none", "acodec": "mp4a.40.2",
         "abr": 129, "tbr": 129, "filesize": 64_000, "url": "u"},
        {"format_id": "251", "ext": "webm", "vcodec": "none", "acodec": "opus",
         "abr": 135, "tbr": 135, "filesize": 70_000, "url": "u"},
    ],
}

if "--dump-single-json" in args:
    if mode == "private":
        sys.stderr.write("ERROR: [instagram] abc: Login required. Private account\n")
        sys.exit(1)
    if mode == "generic":
        INFO = dict(INFO, extractor_key="Generic")
    print(json.dumps(INFO))
    sys.exit(0)

# ---- download mode ---------------------------------------------------------------
if mode == "private":
    sys.stderr.write("ERROR: [instagram] abc: Login required. Private account\n")
    sys.exit(1)

print("[youtube] Extracting URL: " + url, flush=True)
two = "-x" not in args
print("[info] abc123: Downloading %d format(s): 137+140" % (2 if two else 1), flush=True)


def progress(status, done, total):
    print("TCP|%s|%s|%s|NA|1048576.0|%s" % (status, done, total, 3), flush=True)


for i in range(1, 5):
    progress("downloading", i * 250000, 1000000)
    time.sleep(0.05 if mode != "slow" else 2)
    if mode == "huge":
        progress("downloading", 900_000_000, 1000000)
progress("finished", 1000000, 1000000)
if two:
    progress("downloading", 32000, 64000)
    progress("finished", 64000, 64000)

with open("media.info.json", "w") as fh:
    json.dump({"title": INFO["title"]}, fh)

if "-x" in args:
    fmt = opt("--audio-format", "best")
    ext = {"best": "m4a"}.get(fmt, fmt)
    print('[ExtractAudio] Destination: media.%s' % ext, flush=True)
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "lavfi", "-i", "sine=d=1",
                    "media." + ext], check=True)
else:
    ext = opt("--merge-output-format", "mp4")
    print('[Merger] Merging formats into "media.%s"' % ext, flush=True)
    codec = ["-c:v", "libvpx"] if ext == "webm" else ["-c:v", "mpeg4"]
    audio = ["-c:a", "libvorbis"] if ext == "webm" else ["-c:a", "aac"]
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "lavfi", "-i",
                    "testsrc=d=1:s=160x120:r=10", "-f", "lavfi", "-i", "sine=d=1",
                    *codec, *audio, "-shortest", "media." + ext], check=True)
sys.exit(0)
