# Recap Maker - Android App (v2.1 — Full Feature)

AI Video Editor for Myanmar Creators — native Android (Kotlin + Jetpack Compose)

## ✅ Features Added (matching website)

### 🎬 Video Editor — Full UI
- **Video Upload** — file picker from gallery
- **URL Download** — paste YouTube, TikTok, Facebook links (server-side yt-dlp)
- **Bypass Effects** — Flip, Speed (1.05x), Pitch Shift, Noise/Grain toggles
- **Blur Box** — enable + select 1–4 blur regions (server-side processing)
- **Logo Overlay** — pick image, server positions it
- **Text Watermark** — full advanced settings:
  - Text input, font size slider (12–72), color selector
  - Position grid (7 positions: top/center/bottom × left/center/right)
  - Scroll animation toggle
  - Background box with opacity control
- **AI Voice (TTS)** — script textarea + "AI ပြန်ရေးမည်" analyze button
- **30+ Voice Selector** — tabbed Google (PREMIUM) / Microsoft (FREE):
  - 30 Google Gemini voices (Aoede, Puck, Kore, etc.)
  - 2 Microsoft Edge TTS voices (Thiha, Nilar)
  - Search/filter, 2-column grid, gender badges
  - Premium cost warning for Google voices
- **Cost Display** — dynamic pricing based on video duration + voice type
- **Script Analysis** — Gemini AI translates English→Burmese spoken text

### 💰 Coin Packages Popup
- Gold/Silver coin badge (tappable)
- Package list with name, gold amount, price, no-ads bonus
- Payment instructions from server config
- "Admin ကိုဆက်သွယ်ရန်" → opens Telegram link
- Daily Check-in dialog (🥈 Silver)

### 🔗 URL Download
- Available in both Editor and Subtitle screens
- Paste link → server downloads via yt-dlp
- Loading overlay during download

### 📝 Subtitle Generator
- Video upload + URL download
- Font size slider, color selector, position chips
- Background box toggle
- Bypass effects (flip, speed, noise, blur)

## Architecture
```
com.recapmaker.app/
├── data/
│   ├── api/        RecapApi (Retrofit), AuthInterceptor
│   ├── local/      TokenManager (DataStore), AppDatabase (Room)
│   ├── model/      Models.kt (all data classes + VoiceData)
│   └── repository/ AuthRepository, MainRepository
├── di/             AppModule (Hilt DI)
├── ui/
│   ├── auth/       Login, Register, ForgotPassword
│   ├── common/     Theme, Components (shared UI)
│   ├── dashboard/  DashboardScreen + ViewModel + CoinPackagesDialog
│   ├── editor/     EditorScreen + EditorViewModel (FULL)
│   ├── settings/   SettingsScreen
│   └── subtitle/   SubtitleScreen + SubtitleViewModel
└── util/           Utils.kt
```

## Tech Stack
- Kotlin, Jetpack Compose, Material3
- Hilt (DI), Retrofit + OkHttp, Room, DataStore
- ExoPlayer (Media3), Coil (images)
- Navigation Compose

## New Files
| File | Description |
|------|-------------|
| `EditorViewModel.kt` | Full state management for editor |
| `SubtitleViewModel.kt` | State management for subtitle screen |
| `Models.kt` | Added VoiceData (30+ voices), VideoProcessOptions, BlurArea, CoinPackage enhancements, URL models |
| `MainRepository.kt` | Added uploadVideo, downloadFromUrl, processVideo, getPackages |
| `RecapApi.kt` | Added upload, URL download, process, packages endpoints |
| `Components.kt` | Added EffectToggle, VoiceCard, PositionSelector, TabRow, LoadingOverlay, SectionCard |
| `Theme.kt` | Added Rose, Cyan, WarningYellow, SurfaceDark colors |

## Modified Files
| File | Changes |
|------|---------|
| `EditorScreen.kt` | Complete rewrite with full UI |
| `DashboardScreen.kt` | Added CoinPackagesDialog, CheckinDialog, tappable coins |
| `DashboardViewModel.kt` | Added packages loading, contactUsername |
| `SubtitleScreen.kt` | Added URL download, effects, color selector |
| `MainActivity.kt` | Updated to inject ViewModels for editor/subtitle |
