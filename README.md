# Recap Maker - Android App

Video editor & subtitle generator for Android. All processing happens on-device.

## Setup

1. Clone this repo
2. Open in Android Studio
3. API URL is pre-configured to `https://zzzzz-mu.vercel.app`
4. Build & run

## GitHub Actions (Auto Build)

Push to `main` branch → GitHub Actions automatically builds debug APK.

### Release APK Setup (optional)
Add these secrets in GitHub repo Settings → Secrets:
- `KEYSTORE_BASE64` — your keystore file encoded: `base64 -w 0 your.keystore`
- `KEYSTORE_PASSWORD` — keystore password
- `KEY_ALIAS` — key alias name
- `KEY_PASSWORD` — key password

## Architecture

- **Jetpack Compose** — UI
- **Hilt** — Dependency Injection
- **Retrofit + OkHttp** — API calls with JWT auto-attach
- **Room** — Local video history
- **DataStore** — Token persistence
- **Navigation Compose** — Screen routing

## Screens

- Login / Register / Forgot Password
- Dashboard (coins, daily checkin, tools)
- Video Editor (effects, TTS, processing)
- Subtitle Generator (auto-transcribe & burn)
- Settings (account, password, history)
