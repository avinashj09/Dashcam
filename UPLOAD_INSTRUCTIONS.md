# LiveDashcam Android upload instructions

Upload the folder structure in this ZIP into the root of `avinashj09/Dashcam` on the `main` branch. Keep the existing `README.md`, `index.html`, `package.json`, and `server.js` unchanged.

Included:
- `android/` native Android prototype (LIVE, STORAGE diagnostics, SETUP, RTSP player)
- `.github/workflows/android-apk.yml` GitHub Actions debug APK build

After uploading:
1. Open repository → Actions.
2. Choose **Build LiveDashcam Android APK**.
3. Tap **Run workflow** (if available) or wait for the push-triggered run.
4. Open a successful run and download artifact **LiveDashcam-debug-apk**.

Important: this is a prototype. The Oncord RTSP URL is a candidate and may fail; SD-card browsing/download, continuous recording, Google Drive background sync, OCR/plate search, GPS, and remote live relay are not implemented. Do not treat it as a production evidence recorder.
