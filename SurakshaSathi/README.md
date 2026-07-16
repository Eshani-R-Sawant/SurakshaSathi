# SurakshaSathi

**SurakshaSathi** ("Security Companion") is a production-grade Android client for a bank
anti-phishing hackathon problem statement: fraudsters circulate fake YONO Bank banking apps via SMS,
WhatsApp, and Telegram, then phish credentials, OTPs, MPINs, and card details. SurakshaSathi
detects malicious messages and fake/malicious APKs on-device, warns the user in their own
language, hardens sensitive actions with adaptive friction, and reports fraud to the national
cybercrime system.

Built with **Clean Architecture + MVVM**, Kotlin 2.0, Jetpack Compose, Hilt, Room, Retrofit, and
TensorFlow Lite.

---

## 1. Honest scope boundary

This repository is the **production Android client only**. Three things live outside it and must
be provided separately before this becomes a complete, deployable system:

1. **Backend microservices** — the RAG agent, threat-intel API, MaMaDroid deep-analysis service,
   fraud-aggregate/segment-alert service, and the I4C/NCRP bridge. This client is built against
   real Retrofit contracts for all of them (documented in §5 below) and degrades gracefully to
   bundled/cached offline data when they're unreachable — but it does not implement them.
2. **Trained ML models** — `assets/spam_classifier.tflite` ships as a placeholder model so the app
   builds and classifies (with a keyword-heuristic fallback if the model fails to load). Dropping
   in real trained weights requires **zero code change** — same filename, same I/O contract
   (tokenized `int32[1,128]` in, `float32[1,1]` phishing probability out).
3. **Play Store / regulatory approval** for restricted permissions — see §4.

## 2. Architecture

Clean Architecture, feature-based package layout, one Gradle module:

```
com.sbi.surakshasathi/
├── app/                      Application, MainActivity, root NavHost, splash/onboarding
├── core/
│   ├── common/               Result<T>, AppError, DispatcherProvider
│   ├── designsystem/         Compose theme, shared components
│   ├── database/             AppDatabase (SQLCipher-encrypted, single Room DB)
│   ├── network/              Retrofit/OkHttp, cert pinning, auth interceptor
│   ├── datastore/            Encrypted preferences + DPDP consent ledger
│   ├── notification/         Shared notification channel registry
│   └── di/                   Core Hilt modules
└── feature/
    ├── messagescan/          Flow 1  — SMS/WhatsApp/Telegram ingestion + classification
    ├── ragwarning/           Flow 1b — RAG-backed warning + guideline
    ├── apkscan/               Flow 2  — 3-tier APK/URL scanning + Play Integrity
    ├── adaptivefriction/      Flow 3  — behavioral biometrics + liveness
    ├── frauddashboard/        Flow 4a — heatmap + segment alerts
    ├── ncrpreport/            Flow 4b — I4C/NCRP forensic reporting
    └── awareness/             Flow 5  — QR verifier, lessons, badges, nudges
```

Layer rule enforced throughout: **presentation → domain ← data**. Domain packages have zero
Android/framework imports (the one narrow exception is `kotlinx.serialization` DTOs, which live in
`data`, never `domain` — see `RagWarning` vs `RagAnalyzeResponseDto` for the pattern). Presentation
never touches `data` directly, only through use cases or the domain repository interface.

Every external service is behind a domain repository interface with a real Retrofit implementation
as the default binding. Where a `Fake*`/offline-fallback data source exists (`FakeRagDataSource`,
`FakeMaMaDroidSource`, bundled sample fraud clusters, bundled lessons), it is invoked **inside**
the real repository as a resilience fallback on network failure or the `USE_OFFLINE_FALLBACK` flag
— never as a separate default binding.

## 3. Tech stack

Kotlin 2.0 · Jetpack Compose + Material 3 · Hilt · Room + SQLCipher · Retrofit + OkHttp +
kotlinx.serialization · WorkManager · TensorFlow Lite · CameraX + ML Kit (Face Detection, Barcode
Scanning) · Play Integrity API · Google Maps Compose · Firebase Cloud Messaging · Media3 ·
BiometricPrompt · JUnit5 + MockK.

## 4. SMS permission strategy — read before building for Play

`READ_SMS`/`RECEIVE_SMS` are Play-Store-restricted permissions. The app ships as **two Gradle
product flavors** so the restricted permissions never leak into the publishable build:

| Flavor | Permissions declared | Distribution |
|---|---|---|
| `notificationOnly` (default) | None restricted — ingests SMS via the same `NotificationListenerService` used for WhatsApp/Telegram | **Play-publishable as-is**, no declaration form needed |
| `defaultHandler` | `READ_SMS` + `RECEIVE_SMS`, via `src/defaultHandler/AndroidManifest.xml` overlay + `RoleManager.ROLE_SMS` | Requires either Play's Permissions Declaration Form (human review) **or** enterprise/sideload distribution |

Build tasks are flavor-qualified: `assembleNotificationOnlyDebug` / `assembleDefaultHandlerDebug`,
etc. The flavor also drives `BuildConfig.SMS_STRATEGY`, so the runtime strategy and the manifest's
permission declarations can never drift out of sync with each other.

**Choose `defaultHandler` only if** the Bank intends to submit the Permissions Declaration Form, or
plans enterprise (MDM/sideload) distribution outside the Play Store.

## 5. Backend API contracts

All endpoints are relative to `BACKEND_BASE_URL` (set in `local.properties`). Every one of these
already has a working Retrofit client in this repo — the backend team can implement to this
contract directly.

### RAG agent (Flow 1b) — **the piece you're building separately**

```
POST /rag/analyze
Request:  MessageEnvelope { message: String, metadata: { sender, source, receivedAt, device: { model, osVersion, locale }, extractedUrls: [String] } }
Response: { verdict: "PHISHING"|"SCAM"|"SAFE", warning: String, guideline: String,
            language: String, persona: "FARMER"|"STUDENT"|"SENIOR"|"GENERAL", confidence: Float }

GET /rag/segment-alerts?region=&persona=
Response: [ { region, persona, language, title, body } ]

POST /rag/feed   (fire-and-forget training feed from NCRP reports, always quarantine=true)
Request: { apkSha256, messageBody, region, quarantine: true }
```
Client contract: `RagApi.kt` / `RagAnalyzeResponseDto.kt`. On any failure, the client falls back to
localized canned copy (`FakeRagDataSource`, EN/HI/MR/TA) — the verdict/warning/guideline shape must
match exactly or the client's `.getOrDefault()` parsing will silently treat it as `SCAM`/`GENERAL`.

### Threat intelligence + device integrity (Flow 2)

```
POST /threat/apk        { sha256, packageName, signingCertSha256: [String] } → { verdict, engineHits }
POST /threat/apk/deep   (MaMaDroid deep analysis, same request)              → { verdict, mamaDroidScore }
GET  /threat/url?u=                                                          → { verdict, isKnownPhishingDomain }
GET  /threat/bank-allowlist                                                  → [ { packageName, signingCertSha256 } ]
POST /integrity/verify  { integrityToken }                                   → { status }  (decodes the Play Integrity token server-side)
```
Client contract: `ThreatIntelApi.kt`, `IntegrityApi.kt`. `verdict` is always one of
`GOODWARE`/`MALWARE`/`UNKNOWN`.

### Fraud dashboard (Flow 4a)

```
GET  /fraud/aggregate                                    → [ { lat, lng, weight, persona, region, campaignTag } ]
POST /alerts/segment  { region, persona, language, message } → dispatched by the backend via FCM topic "<persona>_<region>_<language>"
```
Client contract: `FraudApi.kt`.

### I4C / NCRP reporting (Flow 4b)

```
POST /i4c/ncrp/report
Request:  { apkSha256, installSource, deviceIntegrity: { status, rooted }, offendingMessage: { sender, body, urls }, location: { region, lat, lng }, reportedAtMillis, reporterConsent }
Response: { caseId: String, status: String }
```
Client contract: `NcrpApi.kt`. The client retries this with WorkManager exponential backoff on
failure and shows a provisional local case ID (`PROV-XXXXXXXX`) in the meantime — never loses a
report.

### Awareness content (Flow 5)

```
GET /awareness/lessons?lang=   → [ { id, title, description, language, quiz: [{question, options, correctOptionIndex}], badgeIdOnCompletion } ]
```
Client contract: `AwarenessApi.kt`. Falls back to three bundled English lessons
(`BundledLessons.kt`) if unreachable.

## 6. Permission list & justification

| Permission | Feature | Justification |
|---|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | all | Backend API calls; offline-first design checks connectivity first |
| `POST_NOTIFICATIONS` | 1b, 2, 5 | Phishing/malware/nudge alerts (Android 13+ gate, requested with rationale) |
| `BIND_NOTIFICATION_LISTENER_SERVICE` (service) | 1 | Reads SMS/WhatsApp/Telegram **notification text only** — the only sanctioned way to observe WhatsApp/Telegram content |
| `READ_SMS`/`RECEIVE_SMS` | 1 | **`defaultHandler` flavor only** — see §4 |
| `CAMERA` | 3, 5 | Liveness challenge, QR scanning — no frame ever persisted or transmitted |
| `ACCESS_FINE/COARSE_LOCATION` | 4a | Voluntary, for heatmap region tagging; optional, consent-gated |
| `USE_BIOMETRIC` | 3 | PIN_CHALLENGE friction level via system BiometricPrompt |
| `REQUEST_INSTALL_PACKAGES`-adjacent scoped `<queries>` | 2 | WhatsApp, Telegram, official YONO package, Play Store, Downloads provider — **not** `QUERY_ALL_PACKAGES` |
| `RECEIVE_BOOT_COMPLETED` | 1, 2 | Re-register receivers after restart |

## 7. Security & privacy hardening (§8C)

- **At rest:** Room encrypted via SQLCipher, key derived from Android Keystore (`DatabaseKeyManager`).
- **In transit:** TLS only, cleartext traffic disabled. Certificate pinning is **wired but inactive**
  (`NetworkSecurityConfig.isConfigured == false`) until real backend cert pins replace the
  placeholders — see the TODOs in that file and the `openssl` command to generate them. Applying a
  fake pin would break every network call the moment a real backend is configured, which is worse
  than temporarily unpinned.
- **Consent:** DPDP-style consent ledger in `UserPreferencesDataStore` (what was consented to, and
  when), written from the real permission-grant flow in `PermissionsScreen`, not a stub.
- **Data minimization:** message bodies purged after 7 days, full rows after 30 (`MessageCleanupWorker`);
  threat-hash cache LRU-capped at 10k entries; NCRP reports retained 90 days (evidentiary).
- **No PII in logs:** Timber is planted in debug builds only; release builds never log.
- **Play Integrity:** the on-device verdict is never trusted alone — the token is forwarded to
  `/integrity/verify` for server-side decoding, per Google's own guidance.

## 8. Performance & storage (§8A/§8B)

- TFLite model loaded via `MappedByteBuffer` (no full-RAM copy); INT8 quantization expected from
  the real model.
- Every Room table has bounded retention enforced by a periodic `WorkManager` cleanup job — no
  table grows unbounded.
- APK Tier-1 scan (hash + cache + branding + rules) runs fully on-device with no network call;
  Tier 2/3 only fire on a genuine cache miss.
- All classification/hashing/inference runs on `Dispatchers.Default`/`IO`, never the main thread.

## 9. Testing

34 unit tests (JUnit5 + MockK), all passing, covering the security-critical pure-logic classes:
`HybridDecisionEngine`, `RuleBasedClassifier`, `TraiDltValidator`, `LocalBehaviorRuleEngine`
(BANK-R01–R07), `ImpersonationChecker`, `WeightedRiskScoringEngine`, `EvaluateFrictionUseCase`.

**Two real bugs were found and fixed by these tests** during this build, not left as pre-existing
issues:
1. `TraiDltValidatorImpl` used `startsWith()` prefix matching against bare registered headers like
   `"BANK"`/`"BANKYONO"` — meaning **any** spoofed sender starting with those strings (e.g.
   `"BANKYONO1"`, `"BANKFRAUD"`) validated as a legitimate registered sender, silently defeating the
   DLT check. Fixed to exact-match only.
2. `RuleBasedClassifier`'s `SENDER_NOT_REGISTERED_DLT` rule fired for **any** non-digit sender name
   over 4 characters, including WhatsApp/Telegram contact display names — TRAI DLT registration has
   no meaning outside SMS. Every saved WhatsApp contact was picking up a false-positive risk
   contribution. Fixed by gating the rule to `MessageSource.SMS` only.

**Honest gap:** this is not ≥80% coverage across domain+data as the spec's quality gate targets —
it's a deliberately-scoped set covering the highest-risk logic (fraud/impersonation detection,
friction scoring) given the session's time budget. Repository/ViewModel/Compose UI tests are not
included. ktlint/detekt are now wired to the `app` module (they were configured at the root but
never actually applied to it, so they silently checked nothing) and the codebase has been
auto-formatted to `ktlint_official`; a full `detekt` pass has not been run to zero findings.

## 10. Known limitations (stated honestly, not glossed over)

- **WhatsApp/Telegram**: notification-text only, as required — no message history, no media, no
  read receipts. Group-summary notifications are filtered out.
- **Liveness (Flow 3)** is blink + head-turn/smile detection via ML Kit, **not** true facial-depth
  mapping — most phones lack a depth sensor. Labeled as such everywhere it's surfaced to the user.
- **MaMaDroid (Flow 2 Tier 3)** is a server-side contract only; this client never runs call-graph
  extraction itself (needs ~16 GB RAM, documented in `FakeMaMaDroidSource`). If on-device
  behavioral learning is wanted later, use MalDozer (raw DEX API sequences → CNN, mobile-sized),
  not MaMaDroid.
- **Downloads pre-install APK scan** uses `DownloadManager.COLUMN_LOCAL_FILENAME`, which is
  deprecated and can return null on some OEM/Android-version combinations under scoped storage.
  The primary, always-reliable detection path is the **post-install** scan
  (`ACTION_PACKAGE_ADDED`), which has no such limitation.
- **FCM topic subscription** (segment alerts / nudges) requires knowing the user's persona/region,
  which onboarding doesn't currently collect — the receiving side (`SurakshaSathiFcmService`) is
  fully wired and will correctly render/store anything pushed to a topic; only the *subscribe*
  step (asking the user their persona/region and calling `FirebaseMessaging.subscribeToTopic`) is
  not yet built.
- **Each bank's real signing-certificate fingerprint** is a placeholder (e.g.
  `REPLACE_WITH_REAL_SBI_YONO_SIGNING_CERT_SHA256` in `BankAllowList.kt`) — the
  impersonation-detection *mechanism* is real and tested, but needs each bank's actual cert hash
  before it can distinguish their real app from a fake one.
- **Certificate pinning** is wired but inactive (placeholder pins) — see §7.
- **Google Maps heatmap** requires `MAPS_API_KEY`; without one, a Compose Canvas fallback renders
  an approximate heatmap over India's lat/lng bounding box so the dashboard never fails to render.

## 11. Compliance surface

Flags for the Bank's legal/security review, not a legal opinion:
- **DPDP Act 2023** — consent ledger with version + timestamp exists (`UserPreferencesDataStore`);
  data minimization is enforced via bounded retention; no data leaves the device without consent.
- **RBI Master Directions on digital banking** — adaptive friction (Flow 3) and device-integrity
  checks (Flow 2) are the client-side controls; server-side transaction risk scoring is out of
  this client's scope.
- **I4C / NCRP reporting mandate** — Flow 4b implements the forensic-report contract with
  guaranteed delivery.
- **Play Store restricted permissions** — see §4.

## 12. Build

```bash
# Play-publishable variant (default, no restricted permissions)
./gradlew assembleNotificationOnlyDebug

# Restricted-permission variant (requires Play declaration or enterprise distribution)
./gradlew assembleDefaultHandlerDebug

# Tests
./gradlew testNotificationOnlyDebugUnitTest

# Static analysis
./gradlew ktlintCheck detekt
```

Toolchain (SDK, JDK, Gradle cache) is configured to run entirely off a non-system drive — see
`gradle.properties` and `build-on-d.ps1`. Copy `local.properties`'s config block and fill in real
values before wiring a real backend:

```properties
BACKEND_BASE_URL=https://your-backend/
MAPS_API_KEY=
VIRUSTOTAL_API_KEY=
PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER=
USE_OFFLINE_FALLBACK=false
```

`google-services.json` currently contains stub Firebase client entries for all four build variants
— replace with a real Firebase project's file before wiring FCM/Crashlytics for real.
