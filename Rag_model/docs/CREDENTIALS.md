# Credentials / API Keys

Live status as of the last verification pass (each tested with a real API call, not assumed).

| # | Service | Used for | Status |
|---|---|---|---|
| 1 | Groq API | LLM (Llama-3.3-70B-versatile) | ✅ Verified working |
| 2 | VirusTotal v3 | APK/file reputation (Lane A) | ✅ Verified working |
| 3 | Twilio Lookup v2 | Callback-number carrier/line-type | ✅ Verified working (does NOT give India porting history/activation date -- no public API does) |
| 4 | WhoisXML API | URL domain-age/registrar lookup | ✅ Verified working |
| 5 | Tavily | Per-message low-latency web corroboration | ✅ Verified working |
| 6 | Google Cloud Translation API | Language ID + translation | ❌ Key valid but API returns 403 ("translate method is blocked") -- enable "Cloud Translation API" in GCP Console > APIs & Services > Library for the project behind this key, and check the key's API restrictions include Translation. Pipeline degrades gracefully (uses original text) until fixed. |
| 7 | Cloud SQL for PostgreSQL (`POSTGRES_DSN`) | Blue DB + guidelines/case-notes vector store | ⏳ Not provisioned yet -- see step-by-step gcloud commands given in chat. Pipeline degrades gracefully (skips persistence/retrieval) until set. |
| 8 | Memorystore for Redis (`REDIS_URL`) | Hot-path cache (WHOIS/VT results) | ⏳ Not provisioned -- pipeline degrades gracefully (no caching) until set. Bloom filter is in-process, not Redis-backed regardless (Memorystore doesn't support the RedisBloom module). |
| 9 | Firebase Admin SDK service-account JSON (`FIREBASE_CREDENTIALS_PATH`) | Server-side FCM push | ❌ Currently points at `google-services.json`, which is the Android **client's** config file -- not the server-side Admin SDK credential. Needed: Firebase Console -> Project Settings -> Service Accounts -> Generate new private key. |
| 10 | Bhashini API | Vernacular translation fallback | Not provisioned -- code path exists (`language/translate.py`) but disabled (`ENABLE_BHASHINI_FALLBACK=false`) since Google Translate is primary. Optional. |
| 11 | Truecaller | Callback reputation | Deliberately deferred (crowdsourced accuracy + 1-2s latency) -- interface stubbed in `crawlers/truecaller_stub.py`, not called. |

## Not needed (no key required)
- OpenPhish feed, cybercrime.gov.in digest, RBI FAME page -- public, scraped directly.
- SBIDocuments PDFs, the two BlueDB CSVs -- local files, already processed.

## Real vs. stub, honestly
Three sub-models still need labeled training data neither of us has yet:
- URL phishing GBC classifier (`url_qr_callback_engine/url_pipeline.py::gbc_phishing_score`)
- QR quishing XGBoost classifier (`url_qr_callback_engine/qr_pipeline.py::xgboost_quishing_score`)
- APK bytecode-image DenseNet classifier (`apk_engine/lane_b_binctx.py::bytecode_image_verdict`)

Recommended bootstrap datasets (public, real, not fabricated): PhishTank + Tranco-1M for the URL
classifier, CICMalDroid2020/Drebin for the APK classifier, and synthetic QR-encodings of PhishTank
URLs plus real benign UPI/business QR scans for the QR classifier. Everything else in this
codebase (translation, embeddings, PII redaction, LLM orchestration, WHOIS/VT/Twilio/Tavily
lookups, PDF ingestion, clustering) is real, tested, working code -- not mocked.
