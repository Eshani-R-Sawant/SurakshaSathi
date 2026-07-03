# Claude Code Build Prompt — "SurakshaSathi" (SBI Anti-Phishing Android App)

> **How to use this file:** Paste the entire section below (everything under "PROMPT START")
> into Claude Code as your initial instruction. It is written as direct instructions to the
> coding agent. Build it incrementally in the phase order given at the end — do not try to
> generate everything in one shot.

---

## PROMPT START

You are building **SurakshaSathi**, a production-grade Android application in **Kotlin** for the
State Bank of India (SBI) hackathon. The problem it solves: fraudsters circulate **fake YONO
banking apps** through SMS, WhatsApp, and Telegram, then phish credentials, OTPs, MPINs, and card
details. SurakshaSathi detects malicious messages and fake/malicious APKs on-device, warns the
user in their own language, hardens sensitive actions with adaptive friction, and reports fraud to
the national cybercrime system.

Build with **Clean Architecture + MVVM** throughout, to **production quality** — not a prototype.
Every on-device capability must be fully, correctly implemented (no stubbed client logic). External
services (backend RAG, MaMaDroid server, threat-intel DB, I4C) are consumed through repository
interfaces with real Retrofit implementations against the documented contracts; provide a
`Fake*DataSource` ONLY as an offline-resilience fallback selected by a runtime flag, never as the
default and never as a shortcut that hides missing logic. Follow every instruction below precisely.

**Production quality bar — all mandatory, not optional:** meet the performance budgets in §8A, the
storage/footprint limits in §8B, the security & privacy hardening in §8C, the reliability rules in
§8D, and pass the quality gates in §8G. Code that compiles but violates these is NOT done.

**Honest scope boundary (state this in the README, do not fake around it):** this repository is the
production Android CLIENT. Three things live outside it and must be provided separately —
(1) the backend microservices (RAG, MaMaDroid deep-analysis, threat intelligence, I4C bridge),
(2) the trained ML models (`.tflite` classifier weights, MaMaDroid Markov model), and
(3) Play Store / regulatory approval for restricted permissions (see §1.7). The client is built to
plug into all three via clean contracts; it does not pretend to be them.

---

### 1. NON-NEGOTIABLE TECHNICAL CONSTRAINTS (read first — do not violate)

1. **WhatsApp / Telegram messages cannot be read from their databases.** No Android app can do
   this. The ONLY sanctioned approach is a `NotificationListenerService` that reads incoming
   notification text (sender title + message body). Implement WhatsApp/Telegram ingestion this way.
   Do not attempt content-provider or root access for these apps.
2. **SMS reading is a Play-Store-RESTRICTED permission — architect for it explicitly.** Google Play
   only allows `READ_SMS`/`RECEIVE_SMS` if the app is the **default SMS handler** or is granted a
   documented exception via the Permissions Declaration Form (human review, can take weeks). Design
   the SMS ingestion behind an `SmsIngestionStrategy` interface with TWO implementations selectable
   at build/config time:
   - `DefaultHandlerSmsStrategy` — used only when the app is registered as the default SMS handler
     (implement the RoleManager `ROLE_SMS` request flow properly).
   - `NotificationOnlySmsStrategy` — the compliant fallback that reads SMS via the same
     `NotificationListenerService` used for WhatsApp/Telegram, requiring NO restricted permission.
   Default distribution build = notification-only (publishable). Document both paths in the README so
   SBI can choose default-handler + declaration, or enterprise distribution, for full SMS access.
3. **Installed-app enumeration is also restricted.** Do NOT use `QUERY_ALL_PACKAGES`. Use scoped
   `<queries>` manifest declarations targeting only what the impersonation check needs. For installed
   packages read `PackageInfo`, signing certificates (`GET_SIGNING_CERTIFICATES`), and install source
   (`getInstallSourceInfo`). For downloaded APKs, observe the Downloads dir and hash the file before
   install. Never claim broad visibility you don't need — it fails Play review.
4. **The ML model runs fully on-device** as a **quantized** `.tflite` file (INT8, see §8B) in
   `assets/`. Ship a working placeholder model + tokenizer/vocab so the app builds and runs; wire it
   so dropping in real trained weights requires zero code change. The model is a real artifact
   supplied by the ML team — the client must not hardcode classification results.
5. **Everything network-facing is behind a Retrofit interface + repository.** The real
   `RetrofitRemoteDataSource` is the default. `Fake*DataSource` exists only as a runtime-flagged
   offline fallback and for tests — it is never the shipping default and never hides missing client
   logic.
6. Target **Kotlin 2.0+**, **minSdk 26**, **targetSdk 35 (or latest stable)**, **Jetpack Compose**
   UI, Material 3. Comply with Google Play's **16 KB memory-page-size** requirement for native libs.
7. **Restricted-permission & compliance reality (do not ignore):** the full feature set touches
   Play-restricted permissions (SMS), notification access, camera (liveness), and installed-app
   visibility, plus Indian regulation (RBI Master Directions on digital banking, MHA/I4C reporting,
   and the DPDP Act 2023 for personal data). Treat every one as a first-class requirement:
   least-privilege manifest, prominent in-app disclosure + consent before any sensitive access, a
   published privacy policy, and data minimization. See §8C and §8E.

---

### 2. TECH STACK (use exactly these)

- **Language:** Kotlin 2.0+, Coroutines + Flow
- **UI:** Jetpack Compose, Material 3, Navigation-Compose
- **Architecture:** Clean Architecture (data / domain / presentation) + MVVM + `UiState` pattern
- **DI:** Hilt
- **Local persistence:** Room + DataStore (Preferences)
- **Networking:** Retrofit + OkHttp (logging interceptor) + kotlinx.serialization
- **Background work:** WorkManager (scheduled scans, retryable NCRP uploads)
- **On-device ML:** TensorFlow Lite (`org.tensorflow:tensorflow-lite`, `tensorflow-lite-support`)
- **Liveness / face:** CameraX + ML Kit Face Detection (`com.google.mlkit:face-detection`)
- **Device integrity:** Google Play Integrity API
- **Maps / heatmap:** Google Maps Compose (`com.google.maps.android:maps-compose`) with a
  heatmap overlay; if no API key is configured, fall back to a static Compose-drawn heatmap so the
  demo never breaks.
- **Permissions UX:** Accompanist Permissions
- **Testing:** JUnit5, Turbine (Flow tests), MockK, Compose UI test

---

### 3. MODULE / PACKAGE STRUCTURE

Use a **feature-based Clean Architecture** layout. Base package `com.sbi.surakshasathi`.
Prefer a single Gradle module split into packages (fast for a hackathon); keep layers strictly
separated. Layer rules: **presentation → domain ← data**. Domain has NO Android/framework imports.
Presentation never touches data directly — only use cases. Data implements domain repository
interfaces.

```
com.sbi.surakshasathi/
├── app/                      # Application(), MainActivity, root NavHost, splash
├── core/
│   ├── common/               # Result<T>, DispatcherProvider, extensions, error types
│   ├── designsystem/         # Compose theme, colors, typography, shared components
│   ├── database/             # AppDatabase, DAOs, entities, type converters
│   ├── network/              # Retrofit/OkHttp providers, interceptors, DTOs base
│   ├── datastore/            # UserPreferences, feature flags, selected language
│   └── di/                   # Core Hilt modules (DB, network, dispatchers)
├── feature/
│   ├── messagescan/          # Flow 1: ingest + classify messages
│   │   ├── data/             #   repo impl, MessageDao wiring, TfLiteSpamClassifier,
│   │   │                     #   SmsBroadcastReceiver, MessageNotificationListenerService
│   │   ├── domain/           #   Message entity, MessageRepository, use cases
│   │   └── presentation/     #   inbox/alert screens, MessageScanViewModel, UiState
│   ├── ragwarning/           # Flow 1b: RAG API warning + guideline + notification
│   │   ├── data/ · domain/ · presentation/
│   ├── apkscan/              # Flow 2: link + APK scanning (Panda-inspired)
│   │   ├── data/ · domain/ · presentation/
│   ├── adaptivefriction/     # Flow 3: behavioral biometrics + risk + liveness
│   │   ├── data/ · domain/ · presentation/
│   ├── frauddashboard/       # Flow 4a: geographic heatmap + segment alerts
│   │   ├── data/ · domain/ · presentation/
│   ├── ncrpreport/           # Flow 4b: I4C automated forensic reporting
│   │   ├── data/ · domain/ · presentation/
│   └── awareness/            # Flow 5: education & nudge — QR validation, gamified lessons,
│       ├── data/             #   vernacular video alerts, proactive safety push
│       ├── domain/ · presentation/
└── di/                       # App-level Hilt aggregation
```

Team ownership (from the architecture diagram — use as code-owner comments only):
Swami → `messagescan` ingestion + JSON metadata builder; Anish & Eshani → ML/rule classifier +
"is malicious?" decision + metadata enrichment; Vipul → `ragwarning` + backend/AI integration.

---

### 4. FLOW 1 — Message ingestion & on-device classification

**Goal:** When an SMS / WhatsApp / Telegram message arrives, capture it, store it, classify it
on-device as spam/phishing or safe. If flagged, escalate to the RAG service (Flow 1b).

**Ingestion (data layer):**
- `SmsBroadcastReceiver` (registered for `SMS_RECEIVED_ACTION`) → parses PDUs → emits raw message.
- `MessageNotificationListenerService` (extends `NotificationListenerService`) → filters by package
  name (`com.whatsapp`, `org.telegram.messenger`) → extracts `title` (sender) and `text` (body).
- Both funnel into a single `IncomingMessageMapper` that produces the canonical JSON metadata
  envelope (mirrors the diagram):

```json
{
  "message": "<raw text>",
  "metadata": {
    "sender": "<number or contact/title>",
    "source": "SMS | WHATSAPP | TELEGRAM",
    "receivedAt": "<epoch millis>",
    "device": { "model": "...", "osVersion": "...", "locale": "..." },
    "extractedUrls": ["..."]
  }
}
```

**Domain layer:**
- Entity `Message(id, body, sender, source, receivedAtMillis, extractedUrls, classification, riskScore)`.
- `MessageRepository` interface: `observeMessages(): Flow<List<Message>>`,
  `classifyAndStore(raw): Message`, `getById(id)`.
- Use cases: `ClassifyMessageUseCase`, `ObserveFlaggedMessagesUseCase`, `ExtractUrlsUseCase`.

**Classification (data layer) — hybrid, mirroring "ML model + Rule based model" in the diagram:**
1. `TfLiteSpamClassifier`: loads `assets/spam_classifier.tflite` + `assets/vocab.txt`. Tokenize
   body → run interpreter → output spam probability (0.0–1.0). Wrap in an interface
   `OnDeviceClassifier` so the model is swappable. Include a tiny working placeholder `.tflite`
   (or generate one at build time) and a fallback keyword heuristic if model load fails, so the
   app never crashes.
2. `RuleBasedClassifier`: deterministic signals — presence of URLs, URL shorteners, `.apk` links,
   urgency keywords (KYC expiry, account blocked, reward, OTP), sender not matching TRAI DLT
   registered SBI headers. Expose a `TraiDltValidator` interface (mockable) that checks whether a
   sender ID is a registered 1600-series DLT sender.
3. `HybridDecisionEngine`: combines both into a final `classification` (SAFE / SUSPICIOUS /
   MALICIOUS) + `riskScore`. This is the "is mal?" branch in the diagram. If SUSPICIOUS/MALICIOUS,
   enrich metadata and hand off to Flow 1b.

**Presentation:** `MessageScanViewModel` exposes `StateFlow<MessageScanUiState>`
(`Loading / Content(messages) / Empty`). A protected "Alerts" screen lists flagged messages with
their risk badge.

---

### 4b. FLOW 1b — RAG warning + guideline notification

**Goal:** For flagged messages, call the **external, API-based RAG agent** (NOT on-device). It
returns a human-readable **warning** + **guideline**, localized to the user's dialect. Surface it
as a system notification and an in-app card.

**Data layer:**
- Retrofit `RagApi`: `POST /rag/analyze` with the enriched message envelope; response DTO:

```json
{
  "verdict": "PHISHING | SCAM | SAFE",
  "warning": "<short user-facing warning>",
  "guideline": "<step-by-step safe action>",
  "language": "hi | mr | ta | en | ...",
  "persona": "FARMER | STUDENT | SENIOR | GENERAL",
  "confidence": 0.0
}
```

- `RagRepository` interface + `RagRepositoryImpl`. Provide `FakeRagDataSource` returning canned
  persona/dialect responses so the demo runs offline.
- The RAG service also **clusters regional fraud reports** and can push **dialect-specific
  pre-emptive warnings** to high-risk segments. Model this as a second endpoint
  `GET /rag/segment-alerts?region=&persona=` consumed by WorkManager + FCM (see Flow 4a).

**Domain:** `RagWarning` entity; `AnalyzeMessageWithRagUseCase`.

**Presentation:** On result, post a high-priority `NotificationCompat` notification ("⚠️ This may
be a fake SBI/YONO message") with expandable guideline text and a "Report" action deep-linking to
Flow 4b. Respect a "notifications" runtime permission gate (Android 13+).

---

### 5. FLOW 2 — Link & APK scanning (Panda / WatchGuard-inspired, thin-client cloud model)

**Goal:** If the user ignores the warning and taps a malicious link or downloads an APK, scan it.
Adopt the **Collective-Intelligence thin-client model** from the WatchGuard/Panda reference:
compute a lightweight local signature, check a fast local cache, then escalate unknowns to the
cloud. Optimize for lowest latency, memory, battery, and cost by keeping heavy analysis OFF the
device. The pipeline is strictly **tiered** — most APKs get a verdict without ever leaving the phone.

**URL scanning:**
- `UrlReputationChecker` interface. Extract URLs from messages/clipboard; check against a local
  Room `PhishingDomainDao` cache first (fast path, mirrors the reference's local threat cache), then
  a cloud reputation API on cache miss (`GET /threat/url?u=`). Mockable.

**APK scanning — three-tier escalation (event-driven, mirroring `ACTION_PACKAGE_ADDED`):**

- **Triggers (event-driven only — no background daemon, matches the battery-optimized hook design):**
  - `ApkInstallReceiver` (BroadcastReceiver for `PACKAGE_ADDED` / `PACKAGE_REPLACED`) → scan on
    install/update events only.
  - `DownloadedApkObserver` (FileObserver / DownloadManager query) → detect `.apk` files landing in
    Downloads → scan BEFORE the user installs.

- **TIER 1 — On-device fast path (thin client; sub-millisecond to a few ms):**
  1. **Reverse signature / hash:** SHA-256 of the APK file + a partial hash of key entry points
     (manifest + signing cert). Interface `ApkSignatureExtractor` (mirrors the reference's
     "reverse signature" idea — hash key blocks, not the whole file).
  2. **Local threat-hash cache:** look up in Room `ThreatHashDao` — the on-device equivalent of the
     reference's Redis hot cache. Hit → instant Goodware/Malware verdict, pipeline stops here.
  3. **Branding / impersonation check (core hackathon detection):** compare package name, signing
     certificate, and app label against a known-good SBI/YONO allow-list. A package that *looks*
     like YONO but is signed by a non-SBI certificate → flagged `isImpersonation = true`
     immediately, on-device, with no network call.
  4. **Local behavioral-rule engine** (offline mode — the Android-adapted equivalent of the Panda
     reference's TruPrevent rule set). IMPORTANT: the reference's Rule 4001–4011 target *Windows*
     threats (boot-sector rootkits, `.lnk` exploits, W32/Viking, W32/Beagle) and DO NOT map to
     Android — do not copy those identifiers literally. Instead implement an equivalent rule table
     `LocalBehaviorRule` keyed to Android fake-banking-app tactics, evaluated on `PackageInfo` +
     requested permissions + manifest components, each returning a weighted risk contribution:
       - **SBI-R01 Credential-overlay stack:** requests `SYSTEM_ALERT_WINDOW` (draw-over-other-apps)
         together with an accessibility service — the classic fake-login overlay + input-capture combo.
       - **SBI-R02 OTP interception:** requests SMS read/receive AND `POST_NOTIFICATIONS` /
         notification access without being a messaging app — OTP-theft pattern.
       - **SBI-R03 Accessibility abuse:** binds `BIND_ACCESSIBILITY_SERVICE` while impersonating a
         bank — used to auto-read screens, auto-click, and exfiltrate.
       - **SBI-R04 Dynamic code loading from untrusted source:** DexClassLoader / native `.so` or
         `.jar` loaded from external/Downloads storage (the reference's DCL risk, Android form).
       - **SBI-R05 Sideload / unknown installer:** `getInstallSourceInfo` is not Play/SBI channel +
         `REQUEST_INSTALL_PACKAGES` present — APK side-loading, the core problem-statement vector.
       - **SBI-R06 Device-admin / lock abuse:** requests Device Admin or `MANAGE_EXTERNAL_STORAGE`
         beyond what a banking app needs.
       - **SBI-R07 Signature mismatch:** re-signed/rebranded package reusing SBI branding (ties into
         the Tier 1.3 impersonation check).
     Rules run fully offline; the engine sums weights into the Tier-1 risk contribution. Keep the
     rule set data-driven (a config list) so new tactics are added without code changes.
  5. **Data-Shield-equivalent (scoped, optional):** the reference's ransomware decoy/Data-Shield
     component is NOT relevant to fake-app phishing — do NOT build ransomware decoys. The useful
     adaptation is a **credential-input guard**: on SurakshaSathi's own protected screens, detect an
     active overlay / accessibility service intercepting input during PIN/OTP entry and block the
     action (feeds Adaptive Friction, Flow 3). Note this explicitly so nobody ports Windows ransomware
     logic into an Android banking app.

- **TIER 2 — Cloud reputation lookup (only on Tier-1 miss = "Unknown"):**
  Behind one `CloudApkVerdictSource` interface (all mockable):
  - `POST /threat/apk` → SBI/SurakshaSathi collective-intelligence verdict by hash.
  - **VirusTotal**-style multi-engine lookup (`GET /files/{sha256}`) for freshly circulating
    variants not yet in the local DB.
  Returns Goodware / Malware / Unknown in well under the reference's 6-minute cloud SLA; cache the
  verdict back into `ThreatHashDao`.

- **TIER 3 — Cloud deep behavioral analysis via MaMaDroid (only on Tier-2 "Unknown"):**
  `MaMaDroidAnalyzer` is a **server-side** stage behind `CloudApkVerdictSource` — the app just
  uploads the APK (with consent) or its extracted call-sequence bundle to `POST /threat/apk/deep`
  and polls/receives an async verdict.
  - **Why server-side, not on-device:** MaMaDroid recovers the full call graph (Soot + FlowDroid),
    abstracts every API call to package/family, and builds a Markov chain of call-sequence
    transitions for a classifier. It reaches ~0.99 F-measure and stays robust as malware evolves
    (~0.87 F-measure two years post-training) — exactly the resilience needed against rapid
    rebranding. BUT it needs a high-power server and ~16 GB RAM to extract the call graph, so it
    **fails the low-memory / low-latency / low-cost bar on-device** and MUST run in the cloud.
    Keeping it in Tier 3 means the phone stays a thin client and only genuinely unknown APKs incur
    this cost.
  - Build `MaMaDroidAnalyzer` as a pure interface on the client with a `FakeMaMaDroidSource`
    returning canned Markov-classifier verdicts, so the demo runs with no server. Document the real
    server contract in the README.
  - **On-device alternative to name in the README:** if a future version needs behavioral learning
    *on the device*, use MalDozer (raw DEX API-method sequences → CNN, designed to run on mobile),
    not MaMaDroid. State this trade-off explicitly.

- `DeviceIntegrityChecker` using **Play Integrity API**: evaluate `MEETS_DEVICE_INTEGRITY`. If the
  device is rooted/emulated (fails integrity), set a flag that the Adaptive Friction engine
  (Flow 3) reads to raise baseline risk for future sensitive actions.

**Panda / WatchGuard reference — implement ALL of these strategies explicitly:**
- Thin-client / Collective-Intelligence model (heavy work in cloud, light sensor on device). ✔ Tiers
- Reverse-signature partial hashing of key entry points. ✔ Tier 1.1
- Local hot cache of high-prevalence threat hashes (Redis-equivalent → Room). ✔ Tier 1.2
- Event-driven scanning on package install/update (no persistent background daemon). ✔ Triggers
- Local behavioral blocking rules for offline / low-connectivity mode. ✔ Tier 1.4 (Android-adapted
  SBI-R01…R07, NOT the Windows Rule 4001–4011 verbatim).
- Data Shield → adapted to a credential-input guard, NOT ransomware decoys. ✔ Tier 1.5
- Dynamic-code-loading (DCL) detection from the reference → SBI-R04. ✔
- Three-way cloud classification: Goodware / Malware / Unknown, with async deep analysis on Unknown. ✔ Tiers 2–3
- Latency model: cached verdicts bypass all network latency; only cache-miss Unknowns pay cloud cost.
  Implement a `T_total = T_cache + (1-H)·T_cloud` mindset — maximize local hit-rate H. ✔
- On-device behavioral-learning alternative named (MalDozer, not MaMaDroid). ✔ Tier 3 note

**Domain:** entities
`ApkScanResult(verdict, sha256, packageName, isImpersonation, tierReached, source, engineHits, mamaDroidScore)`,
`UrlScanResult`, `DeviceIntegrity(status, isRooted, isEmulator)`. Use cases:
`ScanApkUseCase` (orchestrates Tier 1 → 2 → 3 with early exit), `ScanUrlUseCase`,
`CheckDeviceIntegrityUseCase`.

**Presentation:** A blocking full-screen warning when a malicious/impersonating APK is found,
showing which tier produced the verdict, with "Delete file", "Learn more", and
"Report to Cybercrime" (→ Flow 4b) actions.

---

### 6. FLOW 3 — SECURE: Adaptive Friction (behavioral biometrics + liveness)

**Goal:** Replace binary block/allow with **proportional friction** driven by a 0.0–1.0 risk score
computed from behavioral signals collected on the app's OWN sensitive screens (e.g. a simulated
transaction confirmation). This is for the app's own protected flows — do not collect biometrics
from other apps.

**Signal collection (data layer):**
- `BehavioralSignalCollector`: instrument Compose input on protected screens to capture up to ~40
  signals — inter-keystroke timing (typing speed), typing rhythm variance, swipe velocity/cadence,
  touch pressure/size where available, dwell time, and correction rate. Also incorporate the
  device-integrity flag from Flow 2 and time-of-day / velocity-of-actions anomalies.
- Feed signals into a `RiskScoringEngine` (interface + a rule/weighted implementation now,
  swappable for a model later) → returns `riskScore: Float` 0.0–1.0.

**Adaptive Friction policy (domain):**
- `FrictionLevel`: `SEAMLESS` (low risk) → proceed; `PIN_CHALLENGE` (medium) → secondary PIN;
  `LIVENESS_WALL` (high) → mandatory video liveness. Use case `EvaluateFrictionUseCase(riskScore,
  integrity)` returns the level with thresholds in a config object (tunable for the demo).

**Video Liveness (presentation + data):**
- CameraX preview + ML Kit Face Detection. Verify **blink** (eye-open probability transition) and a
  **head-turn / smile challenge** for active liveness. (True facial-depth needs a depth sensor;
  approximate liveness with blink + motion challenge and clearly label it as such — do not claim
  real depth mapping.) On pass → proceed; on fail → block and offer to report.
- Do all inference on-device. No face images leave the phone.

**Presentation:** `AdaptiveFrictionViewModel` orchestrates the challenge escalation. Build a demo
"Confirm Transfer" screen so judges can trigger low/medium/high paths deterministically (include a
hidden debug toggle to force a risk level).

---

### 7. FLOW 4a — Fraud Dashboard (geographic heatmap + segment alerts)

**Goal:** Visualize fraud as a **geographic heatmap** and let an operator push **vernacular alerts**
to a targeted user segment (e.g. "farmers in Vidarbha") in one tap.

**Data layer:**
- `FraudReportRepository`: real Retrofit client fetching aggregated, anonymized fraud reports
  (`GET /fraud/aggregate`) → list of `(lat, lng, weight, persona, region, campaignTag)`. Offline
  fallback ships realistic sample clusters across Indian regions so the map always renders.
- `SegmentAlertSender`: `POST /alerts/segment` (region + persona + language + message) → dispatched
  via **FCM** topic (e.g. `farmer_vidarbha_mr`). Real client call; offline fallback logs the payload.

**Domain:** `FraudCluster`, `UserSegment`, use cases `LoadFraudHeatmapUseCase`,
`PushSegmentAlertUseCase`.

**Presentation:** Google Maps Compose with a heatmap overlay; if no Maps API key, fall back to a
Compose Canvas heatmap over a static India map asset so the demo always renders. A side panel lists
active campaigns with a "Push vernacular alert" button per segment.

---

### 7b. FLOW 4b — Automated NCRP / I4C forensic reporting

**Goal:** One-tap reporting that packages forensic data and submits to the **I4C / NCRP API**,
returns a Case ID in seconds, and feeds the data back to improve detection.

**Data layer:**
- `NcrpReporter` builds a forensic JSON packet:

```json
{
  "apkSha256": "...",
  "installSource": "...",
  "deviceIntegrity": { "status": "...", "rooted": false },
  "offendingMessage": { "sender": "...", "body": "...", "urls": ["..."] },
  "location": { "region": "...", "lat": 0.0, "lng": 0.0 },
  "reportedAtMillis": 0,
  "reporterConsent": true
}
```
- `POST /i4c/ncrp/report` → `{ "caseId": "..." , "status": "REGISTERED" }`. Real client call via the
  I4C bridge service; offline fallback returns a locally generated provisional Case ID and reconciles
  on sync. Use **WorkManager** with exponential backoff and guaranteed delivery so a failed
  submission retries when back online (reports are never lost).
- On success, forward the anonymized, persona-tagged record to the RAG training feed
  (`POST /rag/feed`) — but per the reference's contamination-risk note, mark it **quarantined**
  until a minimum cluster threshold is met server-side (client just flags `quarantine=true`).

**Domain:** `ForensicReport`, `NcrpCaseResult`, use case `SubmitNcrpReportUseCase`.

**Presentation:** A confirmation screen that shows the returned Case ID and consent copy. Require
explicit user consent before any message body or location is uploaded.

---

### 7c. FLOW 5 — Awareness & Nudge Architecture (educate customers in real time)

**Goal:** Directly serve the problem statement's "Educate customers in real time about safe app
usage" objective. This is the proactive/preventive pillar: teach users to spot fakes, verify official
SBI links, and receive timely vernacular nudges — so fraud is prevented, not just detected.

**5.1 QR-based official-app validation (highest-value, concrete anti-phishing feature):**
- `OfficialLinkVerifier` (domain interface). The user scans a QR code or pastes a link/APK source;
  the app verifies it against SBI's official allow-list of domains, Play Store package ID, and
  signing-certificate fingerprint. Returns `VERIFIED_OFFICIAL / UNKNOWN / KNOWN_FAKE` with a clear
  explanation. Use CameraX + ML Kit Barcode Scanning for the QR path; reuse `UrlReputationChecker`
  (Flow 2) and the signing-cert allow-list for the link/APK path. The allow-list is fetched from the
  backend and cached in Room so it works offline; ships with a bundled default.
- Presentation: a friendly "Is this the real SBI app?" scanner screen with a big VERIFIED / NOT-SAFE
  result state, and a one-tap "Open official YONO on Play Store" action for the safe path.

**5.2 Gamified cyber-safety lessons:**
- `LessonRepository` (real content from `GET /awareness/lessons?lang=`, cached in Room; bundled
  offline default set). Short interactive lessons — "Real vs fake SBI apps", "Never share your OTP",
  "Safe ways to download YONO" — with a "spot the scam" quiz format. Track progress, streaks, and
  earned badges in Room (`LessonProgressDao`). Entities: `Lesson`, `LessonProgress`, `Badge`.
- Use cases: `ObserveLessonsUseCase`, `CompleteLessonUseCase`, `AwardBadgeUseCase`.

**5.3 Vernacular video alerts + proactive push:**
- `SafetyNudgeRepository`: receives targeted, persona/region-specific safety nudges via **FCM topics**
  (reusing Flow 4a's segment topics, e.g. `farmer_vidarbha_mr`) and can play short **vernacular video
  alerts** (ExoPlayer/Media3, streamed from a CDN URL in the payload; cache last-N for offline).
  Nudges are also surfaced as in-app cards and localized notifications. Respect quiet-hours and a
  user frequency cap so nudges help rather than annoy (anti-nag rule).
- Entities: `SafetyNudge(id, title, body, videoUrl, language, persona, region, priority)`.
  Use case: `ObserveSafetyNudgesUseCase`, `MarkNudgeSeenUseCase`.

**5.4 Real-time contextual education (ties the whole app together):**
- When Flow 1b flags a message or Flow 2 blocks an APK, surface a matching micro-lesson inline
  ("Here's how to spot this next time") — education delivered at the teachable moment, not buried in a
  menu. Reuse the RAG guideline text; link to the relevant Lesson.

**Presentation:** a "Learn" tab (4th bottom-nav item) with the scanner entry point, lesson list with
progress, badges, and the latest vernacular nudge card. All strings/videos localized per §8E.

**Data sources:** all behind repository interfaces with real Retrofit clients; bundled offline
lesson/nudge/allow-list defaults so the feature is fully functional with no backend.

---

### 8. PRODUCTION ENGINEERING STANDARDS (mandatory across every feature)

These are acceptance criteria, not aspirations. A feature is "done" only when it meets them.

#### 8A. Performance budgets (enforce and measure)
- **Cold start** < 2s on a mid-range device (e.g. Snapdragon 6-series, 4 GB RAM). Use App Startup
  library, lazy Hilt injection, no heavy work on the main thread, no blocking I/O in `Application`.
- **UI:** 60 fps, no jank; zero frame drops on scroll. All I/O, hashing, and inference on
  `Dispatchers.Default/IO`; never block the main thread. Use `Baseline Profiles` + R8 for hot paths.
- **Message classification (on-device):** < 50 ms per message end-to-end.
- **APK Tier-1 scan (hash + cache + branding):** < 150 ms; cache hit < 10 ms.
- **Liveness:** ML Kit inference at ≥ 15 fps, decision < 3s.
- **Memory:** steady-state RSS budget; the app is a lightweight sensor — no in-memory data structure
  scales with total message history (page from Room). Explicitly release CameraX + interpreters when
  screens leave. No leaked `Context`/`Activity` (verify with LeakCanary in debug).
- **Battery:** strictly event-driven — no polling loops, no persistent foreground service except
  where a system API demands it. Batch/defer network via WorkManager with appropriate constraints.

#### 8B. Storage & footprint minimization (this is a hard requirement you asked for)
- **APK size:** enable R8 full mode + resource shrinking; ship an **Android App Bundle** so Play
  serves per-device splits. Strip unused ABIs; comply with the 16 KB page-size requirement.
- **ML model:** ship **INT8-quantized** `.tflite` (typically 3–4× smaller than float). Load via
  memory-mapped `MappedByteBuffer` (no full-RAM copy). Consider Play Feature Delivery / on-demand
  download for the model so it isn't in the base APK if size-sensitive.
- **Database:** Room with **bounded retention** — messages, scan results, and threat-hash cache each
  have a max row count / TTL enforced by a periodic WorkManager cleanup job (LRU eviction on the
  threat-hash cache, e.g. cap at N hot entries mirroring the reference's hot-cache design). Never let
  any table grow unbounded. Store message *bodies* only as long as needed for classification/consent;
  prefer hashes/metadata over raw content where possible (data minimization = smaller + safer).
- **No bundled large assets** you can fetch or generate. Compress the static map fallback asset.
- **Logs:** capped, rotated, and disabled in release (§8C). No log file grows without bound.

#### 8C. Security & privacy hardening
- **At-rest encryption:** encrypt the Room DB with **SQLCipher**; encrypt DataStore with
  Jetpack Security / a Keystore-backed key. Keys live in the **Android Keystore**, never in code.
- **In-transit:** TLS only; **certificate pinning** (OkHttp `CertificatePinner`) on all backend
  calls. Reject cleartext (`usesCleartextTraffic=false`, network security config).
- **Secrets:** none in source or VCS. API keys via `local.properties` → `BuildConfig` /
  Gradle secrets plugin. Provide `.env.example` / `local.properties.example`.
- **Code protection:** R8/ProGuard obfuscation on release; strip logs via a release ProGuard rule.
  Never log message bodies, OTPs, credentials, PII, tokens, or face data in any build tier.
- **Tamper/root awareness:** use Play Integrity verdicts server-side-verified (don't trust the
  client verdict alone); optionally detect debugger/hooking frameworks and raise Adaptive Friction.
- **Privacy:** prominent in-app disclosure + explicit opt-in consent BEFORE enabling notification
  access, SMS access, camera, or any upload. On-device inference by default; nothing leaves the phone
  without consent. Face frames for liveness never persist or transmit. Ship a privacy policy link.
- **Consent ledger:** persist a record of what the user consented to and when (needed for DPDP).

#### 8D. Reliability & resilience
- **Offline-first:** every feature degrades gracefully with no network — local classifier, local
  threat cache, offline behavioral rules, queued reports. Show clear offline state, never crash.
- **Retry/backoff:** all uploads (NCRP, telemetry) via WorkManager with exponential backoff and
  guaranteed delivery; reports are never lost on failure.
- **Error handling:** sealed `Result<T>` (Success/Error/Loading) in `core:common`; map exceptions to
  typed `AppError`s; ViewModels expose only mapped errors; UI never sees raw stack traces. No empty
  catch blocks. Global uncaught-exception handling that fails safe.
- **State:** each screen has an immutable `UiState` + `StateFlow`; one-off events via `Channel`/
  `SharedFlow`. Survive process death (SavedStateHandle) and config changes.

#### 8E. Localization, accessibility, compliance
- **Vernacular:** externalize ALL user-facing strings; ship real translations for at least Hindi
  (`values-hi`), Marathi (`values-mr`), Tamil (`values-ta`), plus English. Selected language in
  DataStore, per-app locale via `AppCompatDelegate`/`LocaleManager`. RTL-safe layouts.
- **Accessibility:** content descriptions on all interactive elements, min 48dp touch targets,
  dynamic type / font scaling, sufficient contrast (WCAG AA), TalkBack-navigable critical flows.
- **Compliance hooks:** data-minimization by default; consent gating; audit trail for reports;
  align data handling with DPDP Act 2023, RBI Master Directions on digital banking, and the I4C
  reporting mandate. Note these in the README as the compliance surface for SBI's legal/security team.

#### 8F. DI, architecture & code quality
- **DI:** one Hilt module per core concern + per feature-data layer. Bind repository interfaces to
  impls; real data source is the default, `@Named("offline")` fake selected only by runtime flag.
- **Architecture enforcement:** domain layer has zero Android imports; add a module/lint check (e.g.
  a Konsist or ArchUnit-style test) that fails the build if a layer boundary is violated.
- **Static analysis:** ktlint + detekt + Android Lint all wired into the build and CI, warnings-as-
  errors on the CI gate. Explicit `@VisibleForTesting` where needed. KDoc on public APIs.

#### 8G. Testing & quality gates (CI must enforce)
- **Unit tests** (JUnit5 + MockK + Turbine) for: `HybridDecisionEngine`, `RuleBasedClassifier`,
  `RiskScoringEngine`, `ApkScanner` tier orchestration + early-exit, `TraiDltValidator`, all use
  cases, and every repository mapper. Target ≥ 80% coverage on domain + data logic.
- **Instrumented/UI tests** (Compose test + Espresso) for the flagged-message alert screen, the
  APK-blocking screen, and the adaptive-friction escalation paths.
- **CI/CD:** GitHub Actions (or equivalent) running lint → detekt → unit tests → assemble → UI tests
  on every PR; block merge on failure. Signed release build config (keystore via CI secrets), staged
  rollout notes. Provide the workflow YAML.
- **Observability:** integrate a crash/ANR reporter (e.g. Firebase Crashlytics or Sentry) and a
  lightweight, privacy-safe metrics layer (scan latency, cache hit-rate, detection counts) behind a
  consent flag. Never send PII.

#### 8H. Manifest & permissions
- Least-privilege manifest; scoped `<queries>` (no `QUERY_ALL_PACKAGES`). Declare receivers/services,
  `BIND_NOTIFICATION_LISTENER_SERVICE` on the listener, `POST_NOTIFICATIONS`, `CAMERA`, and location
  only where used. A central `PermissionManager` with rationale UI for each. Comment every permission
  with its justification (this doubles as the Play Permissions Declaration evidence).

---

### 9. DELIVERABLES / BUILD ORDER (phased; each phase must compile, pass its tests, and meet §8)

1. **Phase 0 — Production scaffold:** Gradle version catalog (`libs.versions.toml`), Hilt, Compose,
   Navigation, Material 3 theme/design system, `core:common/database/network/datastore/di`,
   `Result<T>`, `DispatcherProvider`, SQLCipher + encrypted DataStore wiring, R8/ProGuard config,
   detekt/ktlint/lint, CI workflow, base NavHost + home hub + splash/onboarding-permissions screen +
   placeholder screens for all 7 features. Builds, launches, CI green.
2. **Phase 1 — Flow 1:** SMS strategy (default-handler + notification-only) + notification listener,
   Room storage with retention/cleanup, INT8 TFLite + rule hybrid classifier, alerts screen, tests.
3. **Phase 2 — Flow 1b:** real RAG Retrofit client (+ offline fallback), warning notifications,
   full vernacular strings, consent gating, tests.
4. **Phase 3 — Flow 2:** three-tier APK/URL scanning (on-device Tier 1, cloud Tier 2, MaMaDroid
   Tier 3 contract), LRU threat-hash cache, Play Integrity (server-verified), blocking UI, tests.
5. **Phase 4 — Flow 3:** behavioral signal collection, risk engine, adaptive-friction escalation,
   CameraX + ML Kit liveness (no frame persistence), demo "Confirm Transfer" screen with debug risk
   toggle, tests.
6. **Phase 5 — Flow 4:** fraud heatmap dashboard + segment alerts (FCM), NCRP forensic reporting with
   WorkManager guaranteed delivery + quarantine flag, consent, tests.
7. **Phase 6 — Flow 5 (Awareness & Nudge):** QR/link official-app validator (CameraX + ML Kit
   Barcode), gamified lessons + badges (Room progress), vernacular video nudges (Media3 + FCM topics),
   real-time contextual micro-lessons wired into Flows 1b/2, "Learn" tab, tests.
8. **Phase 7 — Hardening & release:** cert pinning, LeakCanary pass, Baseline Profile, coverage gate,
   full CI (lint→detekt→unit→assemble→UI), signed release AAB config, crash/metrics wiring, README.

**At the end, output a `README.md`** documenting: architecture + module map, the client/backend/model
scope boundary, exact API contracts for every external service, the SMS-permission strategy decision
(default-handler vs notification-only vs enterprise distribution) and its Play-review implications,
the full permission list with justifications, the compliance surface (DPDP/RBI/I4C), performance &
storage budgets actually achieved, and honest known limitations (WhatsApp/Telegram via notifications
only; liveness is blink+motion not true facial depth; MaMaDroid + models + backends are separate
deliverables). Keep it honest — overclaiming fails both judges and a real security review.

## PROMPT END
