# SurakshaSathi — Implementation Plan

## Overview

**SurakshaSathi** is a production-grade Kotlin Android app for SBI's hackathon that detects fake
YONO apps, phishing SMS/WhatsApp/Telegram messages, and educates users in vernacular languages.
Built with Clean Architecture + MVVM, Jetpack Compose, Hilt DI, Room, Retrofit, TFLite, and more.

**Scope:** Android CLIENT only. Backend microservices, trained ML models, and Play Store review
are out-of-scope for this repo (documented honestly in README).

---

## Configuration

| Setting | Value |
|---|---|
| Language | Kotlin 2.0 |
| AGP | 8.7 |
| minSdk | 26 (Android 8.0) |
| targetSdk | 35 |
| UI | Jetpack Compose + Material 3 |
| DI | Hilt |
| SMS Strategy | Both (build-flag switched) |

**External keys needed (to be provided by user before respective phase):**
- Google Maps API Key → Phase 5 (Flow 4a)
- `google-services.json` → Phase 5 (FCM)
- Backend Base URL → Phase 2 (Flow 1b onwards)
- VirusTotal API Key → Phase 3 (Tier 2)

---

## Phase 0 — Production Scaffold ✅ CURRENT

### Goal
A buildable, launchable Android project with all core architecture wiring in place, placeholder
screens for every feature, CI green. **No business logic yet — just the skeleton.**

### Files to Create

#### Project Root
- `build.gradle.kts` (root)
- `settings.gradle.kts`
- `gradle/libs.versions.toml` (version catalog — ALL deps centralized here)
- `.github/workflows/ci.yml`
- `local.properties.example`
- `README.md` (living document, updated each phase)
- `proguard-rules.pro`

#### App Module
- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml` (all permissions declared, least-privilege)
- `app/src/main/kotlin/com/sbi/surakshasathi/app/SurakshaSathiApplication.kt`
- `app/src/main/kotlin/com/sbi/surakshasathi/app/MainActivity.kt`

#### Core Layers
- `core/common/` — `Result<T>`, `AppError`, `DispatcherProvider`
- `core/designsystem/` — Theme, Colors, Typography, Shapes
- `core/database/` — `AppDatabase` (SQLCipher), base DAOs
- `core/network/` — `NetworkModule`, OkHttp, Retrofit, `CertificatePinner` wiring
- `core/datastore/` — `UserPreferencesDataStore` (encrypted)
- `core/di/` — Hilt modules for DB, network, dispatchers

#### Feature Placeholders (7 features)
Each gets a skeleton `presentation/` screen + route in NavHost:
1. `feature/messagescan/`
2. `feature/ragwarning/`
3. `feature/apkscan/`
4. `feature/adaptivefriction/`
5. `feature/frauddashboard/`
6. `feature/ncrpreport/`
7. `feature/awareness/`

#### Navigation
- `app/navigation/NavGraph.kt` — root NavHost, bottom nav (4 tabs)
- `feature/*/presentation/*Screen.kt` — placeholder Compose screens

#### Config
- `detekt.yml`
- `ktlint` config
- `proguard-rules.pro`

### Verification
- `./gradlew assembleDebug` — builds without errors
- `./gradlew test` — unit tests pass (empty stubs)
- `./gradlew lint detekt` — no errors
- App launches on emulator, shows home hub with 4 bottom-nav tabs, placeholder screens accessible

---

## Phase 1 — Flow 1: Message Scanning

SMS + WhatsApp/Telegram ingestion, on-device hybrid classification (TFLite + rules), alerts screen.

## Phase 2 — Flow 1b: RAG Warning

Real Retrofit RAG client, warning notifications, vernacular strings (EN/HI/MR/TA), consent gating.

## Phase 3 — Flow 2: APK/URL Scanning

3-tier APK scan (Tier1 on-device, Tier2 cloud, Tier3 MaMaDroid contract), Play Integrity, blocking UI.

## Phase 4 — Flow 3: Adaptive Friction

Behavioral biometrics, risk engine, liveness challenge (CameraX + ML Kit), demo transfer screen.

## Phase 5 — Flow 4: Fraud Dashboard + NCRP Reporting

Google Maps heatmap, FCM segment alerts, I4C forensic reporting with WorkManager guaranteed delivery.

## Phase 6 — Flow 5: Awareness & Nudge

QR/link official-app validator, gamified lessons + badges, vernacular video nudges (Media3).

## Phase 7 — Hardening & Release

Cert pinning, LeakCanary pass, Baseline Profile, signed AAB config, crash/metrics, full README.

---

## Architecture Rules

```
presentation → domain ← data
```
- Domain has ZERO Android imports (enforced by lint/test)
- Presentation never touches data directly — only through use cases
- Real data source is default; `Fake*` only for offline fallback (runtime flag) and tests
- Sealed `Result<T>` everywhere; no raw exceptions surfacing to UI

## Open Questions for User

> [!IMPORTANT]
> **Google Maps API Key**: Will be needed before Phase 5. Please provide when ready.

> [!IMPORTANT]  
> **google-services.json**: Needed for FCM in Phase 5. Please share when ready.

> [!NOTE]
> **Backend Base URL**: A placeholder `https://api.surakshasathi.sbi.co.in` will be used until
> you provide the real URL. Configurable via `local.properties`.
