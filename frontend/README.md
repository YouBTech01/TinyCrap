# Tiny Crap — Android app (Kotlin + Jetpack Compose)

Native Material 3 client for the Tiny Crap backend. One screen, no navigation:

paste or share a link → platform bar auto-highlights → thumbnail + title appear →
**Video** / **Audio** tabs show only what the source really offers → download with a live
progress line → file is saved to your phone (Movies/Tiny Crap or Music/Tiny Crap).

## Build and run

1. Install **Android Studio** (current stable) with the Android SDK (API 35) and JDK 17.
2. **File → Open** and choose this `android/` folder. Let Gradle sync.
3. Start the backend (see `../backend/README.md`), then press **Run** on an emulator or phone.
4. In the app tap the **gear** icon and set the server address:

| Where the backend runs | Address |
|---|---|
| Computer, app on the **emulator** | `http://10.0.2.2:8000` (default) |
| Computer, app on a **real phone** (same Wi-Fi) | `http://<your PC's IP>:8000` (start backend with `--host 0.0.0.0`) |
| cPanel / any hosted server | `https://your-domain` |

Use **Test connection** in that dialog to check the address, API key and server tools.

Command line (needs the Android SDK; set `ANDROID_HOME` or `local.properties`):

```bash
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease      # signed with the debug key for personal use
```

* **Debug** builds allow plain `http://` (for local development).
* **Release** builds only allow `https://`. Put your hosted backend behind HTTPS.
* To publish on Play Store, replace the debug signing config in `app/build.gradle.kts`.

## Features

* Material 3, dynamic colour on Android 12+ (static green palette below), light/dark toggle in the header, flat colours only.
* Universal link field with paste button; the platform bar reacts instantly and the backend confirms the platform.
* **Share → Tiny Crap** from Instagram, YouTube or Facebook opens straight into analysis.
* Video tab: real qualities only (resolution, fps, codec, container, size estimate), MP4/WebM choice, merge notice.
* Audio tab: **Best Audio** in one tap (original stream, no re-encode), plus MP3 / M4A / Opus / WAV / FLAC with bitrates. The source quality is shown separately, and picking a bitrate above the source warns you.
* Progress: Preparing → Analyzing → Downloading → Processing → Finalizing → Ready, with percent, speed, ETA, cancel, and a clear error message.
* Accessibility: labelled icons, radio/segmented semantics, live-region status text, 48 dp touch targets, reduced-motion support (system "remove animations").
* Adaptive layout: single column on phones, two panes from 840 dp wide.
* Minimal dependencies: Compose, OkHttp (plain JSON via `org.json`), Coil for the thumbnail. No database, no accounts.

## Project layout

```
app/src/main/java/com/tinycrap/app/
  MainActivity.kt            entry point, handles shared links
  data/                      Models, JsonParsing, ApiClient (OkHttp), MediaSaver (MediaStore), AppSettings
  ui/MainViewModel.kt        the whole state machine
  ui/MainScreen.kt           the single screen
  ui/components/             PlatformBar, UrlInput, MediaCard, VideoTab, AudioTab, ProgressPanel, SettingsDialog
  ui/theme/Theme.kt          Material 3 theme
```

Requires Android 10+ (minSdk 29): files are saved with MediaStore, so no storage permission is requested.
The only permission is INTERNET. Progress is tracked while the app is open; the backend keeps a job
for about 30 minutes, so leaving the app mid-download means starting that download again.

## Notes

* The app never talks to Instagram/YouTube/Facebook itself. Only your backend does.
* Only download content you have the right to save. Private or protected media is refused by design.
