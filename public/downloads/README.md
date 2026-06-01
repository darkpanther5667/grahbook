# APK Downloads

Place the latest Grahbook Pro APK here named `grahbook-latest.apk`.

This file is served at:
  https://wpapp-xz9l.onrender.com/downloads/grahbook-latest.apk

## Steps to release a new version

1. Build the release APK:
   ```
   .\gradlew.bat assembleRelease
   ```
2. Copy `app/build/outputs/apk/release/app-release.apk` to this folder as `grahbook-latest.apk`
3. In `server.js`, bump `versionCode` (e.g. 1 → 2) and `versionName`
4. Also bump `versionCode` in `app/build.gradle.kts`
5. Commit and push — Render auto-deploys, users get prompted to update
