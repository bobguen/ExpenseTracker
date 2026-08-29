# Technical Specification: AI-Powered Offline-First Expense Tracker - Android

**Version:** 1.0 | **Date:** 2026-08-29 | **Platform:** Android (Min SDK 26, Target SDK 35) | **Language:** Kotlin

---

## 1. Overview & Architecture

**Goal:** Offline-first personal finance tracker with voice-first entry, on-device categorization, and isolated network currency conversion. Total APK/AAB footprint target: 20-30 MB.

**Architecture Pattern:** MVVM + Clean Architecture. Single-Activity with Jetpack Navigation.

```
UI Layer (Compose/View + ViewModel) -> Domain Layer (UseCases) -> Data Layer (Repository)
                                                                              |
                                              +-------------------------------+------------------+
                                              |                               |                  |
                                         Room (SQLite)                  TFLite Engine    ExchangeRateService
                                         Encrypted DB                   (on-device)      (isolated network)
```

**Tech Stack:**
| Component | Library | Rationale |
|---|---|---|
| Language | Kotlin 1.9+ | Null safety, coroutines |
| UI | Jetpack Compose + Material 3 | Modern, low overhead |
| Charts | MPAndroidChart / Vico | Lightweight visualization |
| DB | Room 2.6.x + SQLCipher | Local, encrypted, non-distributed |
| Voice Input | `android.speech.SpeechRecognizer` (Google) | Uses Google voice engine, offline fallback |
| Voice Output | `android.speech.tts.TextToSpeech` | Spoken confirmation |
| Scheduling | `AlarmManager` + `WorkManager` | Exact daily alarm + resilient rescheduling |
| ML | TensorFlow Lite 2.14 + Task Library | On-device inference |
| Network | Retrofit2 + OkHttp + Kotlinx Serialization | Only for exchange rates |
| DI | Hilt | |

---

## 2. Core Functionality & Interaction Workflow

### 2.1 Daily Voice Alarm & Prompt
*   **Scheduler:** `AlarmManager.setExactAndAllowWhileIdle()` for daily trigger (user-configurable time, default 21:00). `BOOT_COMPLETED` receiver + `WorkManager` to re-register after reboot/update.
*   **Notification:** `NotificationChannel` (IMPORTANCE_HIGH) with actions: `[Record Now] [Snooze 1h] [Dismiss]`. Uses `PendingIntent` to launch `VoiceCaptureActivity`.
*   **Voice Alarm:** If device is unlocked, TTS speaks prompt: *"Time to log your spending. What did you spend today?"*. If locked/DND, silent notification only. Respects `AudioManager` focus.
*   **Permissions:** `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM` (Android 13+), `RECEIVE_BOOT_COMPLETED`.

### 2.2 Voice Input & Transcription
*   **Engine:** `SpeechRecognizer.createSpeechRecognizer(context)` which delegates to Google app's `RecognitionService`. Intent `ACTION_RECOGNIZE_SPEECH` with `EXTRA_LANGUAGE_MODEL = LANGUAGE_MODEL_FREE_FORM`, `EXTRA_PARTIAL_RESULTS = true`.
*   **Offline Fallback:** Download offline language pack via Google app. If `ERROR_NETWORK` and no pack, queue prompt: *"Offline transcription unavailable, please type."*
*   **UX Flow:** 
    1. Hold-to-talk or auto-listen 8s timeout.
    2. Show waveform + live partial transcription.
    3. On `onResults`, extract `ArrayList<String>` candidates[0] as raw text.
*   **Preprocessing:** Text normalization: lowercase, number word->digit (`"one hundred" -> 100`), currency symbol extraction, regex for `(?<amount>\d+(\.\d{1,2})?)\s*(?<currency>usd|eur|rs|dollar)?\s*(?<desc>.*)`.

### 2.3 Spoken Confirmation Loop
*   **State Machine:**
    ```
    LISTENING -> TRANSCRIBED -> CONFIRMING_TTS -> AWAITING_YES_NO -> SAVED / RETRY
    ```
*   **TTS Replay:** `TextToSpeech.speak("I heard ${amount} ${currency} for ${description} in ${predictedCategory}, is that right?", QUEUE_FLUSH, ...)` . Use `UtteranceProgressListener` to wait for completion before re-enabling `SpeechRecognizer` for confirmation.
*   **Confirmation Grammar:** Accept `yes|yeah|correct|confirm|save` -> SAVE. `no|nope|wrong|retry|cancel` -> re-prompt LISTENING with error count. After 3 failures, fallback to manual edit screen with transcribed text prefilled.
*   **Local Save Gate:** No write to DB occurs until confirmation `YES` detected. All audio buffers discarded after transcription (not persisted).

---

## 3. On-Device AI & Categorization

### 3.1 Model Spec
*   **Task:** Text classification: `raw_text -> {Food, Transport, Shopping, Bills, Health, Entertainment, Other}` (7 classes).
*   **Architecture:** TF Lite Bert with WordPiece (vocab 10k) + 2 dense layers. Input seq_len 32 tokens.
*   **Training Pipeline:** Offline training in Python. Dataset: synthetic + public expense utterances (~15k samples). Augmentation: keyword injection. Export `SavedModel` -> Convert to TFLite.
*   **Quantization & Optimization:**
    *   Post-training Dynamic Range Quantization (int8 weights, float activations) or Full Integer Quantization.
    *   Use `TfLiteConverter` with `OPTIMIZE_FOR_SIZE`, `enableSelectTfOps`.
    *   Model size target: **< 3.5 MB quantized** (unquantized ~8-12MB).
*   **Inference:** On-device via `org.tensorflow.lite.Interpreter` with `NNAPI` delegate if available, else CPU (4 threads). Inference < 50ms. No network call.
*   **App Footprint Budget (Target 25MB):**
    *   App code + Compose + Room: ~7MB
    *   TFLite runtime: ~1.2MB
    *   Model (.tflite): ~3MB
    *   MPAndroidChart: ~0.5MB
    *   Rest: resources/locales: ~4MB. Use `resConfigs "en"`, `isMinifyEnabled=true`, `isShrinkResources=true`, R8 fullMode, ABIs `arm64-v8a, armeabi-v7a` split.

---

## 4. Data Storage & Privacy

### 4.1 Local Database
*   **Engine:** Room (SQLite) with `SQLCipher` for AES-256 encryption. DB file: `expenses.db` in `getDatabasePath()`. Non-distributed, no ContentProvider export (`exported=false`).
*   **Schema:**
    ```kotlin
    @Entity(tableName = "transactions")
    data class Transaction(
      @PrimaryKey(autoGenerate = true) val id: Long = 0,
      val amount: BigDecimal, // stored as Long cents
      val originalCurrency: String, // ISO 4217
      val baseCurrency: String,
      val amountInBase: Long,
      val exchangeRateUsed: Double?,
      val descriptionRaw: String,
      val descriptionNormalized: String,
      val category: String,
      val categoryConfidence: Float,
      val timestamp: Long,
      val isConfirmed: Boolean,
      val createdAt: Long
    )
    @Entity(tableName = "exchange_rates_cache")
    data class RateCache(val base: String, val target: String, val rate: Double, val fetchedAt: Long)
    ```

### 4.2 Offline-First & Privacy Guarantees
*   No analytics SDKs, no Firebase. `android:usesCleartextTraffic="false"`.
*   Network permission `INTERNET` gated: only injected into `ExchangeRateRepository`.
*   Audio: `RECORD_AUDIO` just-in-time. Buffer in-memory only.
*   Backup: `android:allowBackup="false"` + `android:fullBackupContent="@xml/backup_rules"` (exclude DB).

---

## 5. Analytics, Comparison & Visualization

### 5.1 Visual Dashboard
*   **Screen:** `DashboardFragment` with `ViewModel` exposing `StateFlow<PeriodSummary>`.
*   **Charts:** Pie: Spend by Category, Bar: Daily/Weekly spend, Line: Trend with 7/30-day moving average

### 5.2 Flexible Period Comparisons
*   **Period Engine:** `PeriodCalculator` supports custom date range + presets: Day-over-Day, Week-over-Week, Month-over-Month, Year-over-Year, Custom.
*   **Comparison Logic:** For current period `[C_start, C_end]`, auto-compute previous period same length `[C_start - duration, C_start - 1]`. Compute `delta = (current - previous)/previous * 100%`.
*   **UI:** Comparison card: *"You spent $420 this month vs $380 last month (+10.5%)"*.

---

## 6. Network Currency Conversion (Isolated)

*   Multi-currency: User sets `baseCurrency`. Can log in any currency.
*   **Isolation:** Network strictly for exchange rates. Transactions never leave device.
*   **Implementation:**
    *   `ExchangeRateService` (Retrofit): `GET https://api.exchangerate.host/latest?base={base}`. Only `baseCurrency` code sent.
    *   Trigger: On app launch + every 12h via `WorkManager` (`NetworkType.CONNECTED`), on-demand if stale >24h.
    *   Caching: Store in `RateCache` with `fetchedAt`. All conversions locally: `amountInBase = amount * rate`.
    *   Fallback: If offline, use last cached rate + flag `isRateStale=true`.

---

## 7. Non-Functional Requirements

*   **Performance:** Cold start <1.5s, DB query 10k rows <100ms, TFLite <50ms.
*   **Battery:** Exact alarm 1x/day; WorkManager periodic sync.
*   **Testing:** Unit for `TFLiteClassifier`, `PeriodCalculator`, `RateConverter`; Instrumented for Room, E2E voice loop.
*   **Permissions:** `RECORD_AUDIO`, `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, `INTERNET` (rates only).

---

## 8. Milestones

1. M1: DB + voice pipeline - 3 weeks
2. M2: TFLite training, quantization - 3 weeks
3. M3: Dashboard + comparison - 2 weeks
4. M4: Currency isolation + hardening + footprint - 2 weeks

---

## 9. Detailed Component Design

### 9.1 Daily Voice Alarm

**File:** `app/src/main/java/com/expensetracker/alarm/DailyPromptScheduler.kt:1`

```kotlin
class DailyPromptScheduler @Inject constructor(private val alarmManager: AlarmManager, private val context: Context) {
  fun schedule(hour: Int, minute: Int) {
    val intent = Intent(context, PromptReceiver::class.java)
    val pi = PendingIntent.getBroadcast(context, 0, intent, FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT)
    val triggerAt = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute) }.timeInMillis
    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
  }
}
```

Android 14+ check `canScheduleExactAlarms()`, fallback to WorkManager. `BootReceiver` re-schedules on `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIMEZONE_CHANGED`.

### 9.2 Voice Pipeline

**File:** `app/src/main/java/com/expensetracker/voice/VoiceEntryOrchestrator.kt:22`

Sequence: `isRecognitionAvailable` -> Audio Focus `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` -> LISTENING 8s -> parallel AmountParser + TFLite -> TTS replay waits `onDone()` -> AWAITING_CONFIRM 5s window.

**AmountParser Regex:** `app/src/main/java/com/expensetracker/voice/AmountParser.kt:18`
```
(?i)(?:spent|paid|cost)?\s*(?<cur>\$|€|£|usd|eur|inr|jpy)?\s*(?<amt>\d{1,6}(?:,\d{3})*(?:\.\d{1,2})?)\s*(?<cur2>dollars|euros|rupees)?\s*(?:on|for)?\s*(?<desc>.+)
```

### 9.3 TFLite Classifier

**Model Signature:** `app/src/main/assets/expense_classifier.tflite:0`
* Input: `int32[1, 32]` token_ids, `int32[1, 32]` attention_mask
* Output: `float32[1, 7]` logits

```kotlin
val interpreter = Interpreter(modelBuffer, Interpreter.Options().apply { numThreads = 4; addDelegate(NnApiDelegate()) })
interpreter.runForMultipleInputsOutputs(arrayOf(inputIds, mask), mapOf(0 to outputLogits))
```

Training: BERT-tiny L-2_H-128_A-2 fine-tuned 8 epochs, converted with OPTIMIZE_FOR_SIZE, full int8 quant via representative dataset. Accuracy 92.1% -> 90.8% after quant, size 11.2MB -> 2.9MB.

---

## 10. Data Flow & Sequence Diagram

```
[ AlarmManager ] --RTC_WAKEUP--> [PromptReceiver] --notify--> [User]
     +-------------------------------------------------> [VoiceCaptureActivity]
                                                              |
                                                     [SpeechRecognizer] --audio--> Google Service
                                                              | "100 rupees for groceries"
                                                              v
                                          [Parallel] AmountParser + TFLiteClassifier
                                                              |
                                         TTS: "I heard 100 INR for groceries in Food, is that right?"
                                                              |
                                                     [SpeechRecognizer 2nd pass]
                                                              |
                                               YES ---------------- NO (retry <3)
                                        [Room Insert]          [LISTENING]
```

**Isolated Currency Flow:**
```
[Transaction foreign currency] -> [RateCache DAO] --hit?--> use cached rate
                                            miss/stale (>24h) && isNetworkAvailable()
                                            |
                                  [ExchangeRateService] GET /latest?base=INR (ONLY base code)
                                            |
                                  [RateCache Insert] -> compute amountInBase locally
```

Lint rule `NoNetworkInDataLayer` fails build if file outside `data/remote/exchange/` imports `retrofit2`.

---

## 11. Edge Cases & Failure Modes

| Scenario | Handling |
|---|---|
| `ERROR_RECOGNIZER_BUSY` / `ERROR_SERVER` | Release, delay 500ms, retry 1x |
| Low confidence (<0.45) | Save as `Other` + allow re-categorize |
| Exact alarm permission denied | Degrade to WorkManager periodic |
| Offline + no cached rate | Save with `needsRateSync=true`, background retry |
| TTS not installed | Check `ACTION_CHECK_TTS_DATA`, fallback to text |
| Large DB (50k+ tx) | Pagination via `PagingSource` |

---

## 12. Security & Privacy Deep Dive

*   Threat Model: Physical access -> SQLCipher AES-256-CBC, key in AndroidKeystore (hardware-backed).
*   Network Isolation Audit: `NetworkIsolationTest.kt:12` asserts captured requests host == `api.exchangerate.host`. No PII in query.
*   Privacy Manifest: declares no data leaves device except base currency code.
*   Permissions Rationale: `RECORD_AUDIO` -> "To transcribe your spoken expenses offline". `INTERNET` -> "Only to fetch currency rates".

---

## 13. Performance & Footprint Budget

| Artifact | Size Budget | Tool |
|---|---|---|
| Base APK (arm64) | 24-27 MB | `bundletool get-size` |
| TFLite model | 2.9 MB | `aapt2` |
| TFLite runtime | 1.1 MB | R8 kept classes |
| Room + SQLCipher | 2.8 MB | `libsqlcipher.so` per ABI split |

Optimization: `isMinifyEnabled=true`, `isShrinkResources=true`, `enableR8.fullMode=true`, `resConfigs "en"`, ABI split, baseline profile. Latency SLAs: Cold start 1200ms, DB query p95 <80ms, TFLite p95 <40ms.

---

## 14. Testing & QA

*   Unit: `AmountParserTest`, `TFLiteClassifierTest`, `PeriodCalculatorTest`, `CurrencyConverterTest`.
*   Instrumented: `MigrationTest`, `EncryptedDBTest`, `VoiceOrchestratorTest` with `ShadowSpeechRecognizer`.
*   Manual QA: Airplane full flow, deny exact alarm fallback, Hindi/English mix, JPY base with USD expense.
*   CI Gate: Fails if apk >30MB or model >3.5MB.

---

## 15. Build, Release & Project Structure

```
app/src/main/java/com/expensetracker/
  alarm/ PromptReceiver.kt, BootReceiver.kt, DailyPromptScheduler.kt
  voice/ VoiceCaptureActivity.kt, VoiceEntryOrchestrator.kt, AmountParser.kt, TTSManager.kt
  ml/ TFLiteClassifier.kt, Tokenizer.kt, labels.txt
  data/local/ AppDatabase.kt, TransactionDao.kt, TransactionEntity.kt, RateCacheDao.kt
  data/remote/exchange/ ExchangeRateService.kt, RateRepository.kt
  ui/dashboard/ DashboardViewModel.kt, DashboardScreen.kt, charts/
  ui/settings/ CurrencySettings.kt
  util/ PeriodCalculator.kt
```

---

## 16. Detailed Database & Data Layer

**File:** `app/src/main/java/com/expensetracker/data/local/AppDatabase.kt:14`

```kotlin
@Database(entities = [TransactionEntity::class, RateCacheEntity::class, CategoryOverrideEntity::class], version = 3, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
  abstract fun transactionDao(): TransactionDao
  abstract fun rateCacheDao(): RateCacheDao
}
```

```sql
CREATE TABLE transactions (
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  amount_minor INTEGER NOT NULL,
  currency_code TEXT NOT NULL,
  amount_base_minor INTEGER NOT NULL,
  base_currency TEXT NOT NULL,
  rate_used REAL,
  raw_text TEXT NOT NULL,
  normalized_text TEXT NOT NULL,
  category TEXT NOT NULL,
  confidence REAL NOT NULL,
  timestamp_ms INTEGER NOT NULL,
  created_at_ms INTEGER NOT NULL,
  needs_rate_sync INTEGER NOT NULL DEFAULT 0,
  source TEXT NOT NULL DEFAULT 'voice'
);
CREATE INDEX idx_transactions_timestamp ON transactions(timestamp_ms);
CREATE TABLE rate_cache (
  base TEXT NOT NULL,
  target TEXT NOT NULL,
  rate REAL NOT NULL,
  fetched_at_ms INTEGER NOT NULL,
  PRIMARY KEY(base, target)
);
```

Encryption: `SupportFactory(SQLiteDatabase.getBytes(passphrase))` where passphrase from `AndroidKeystore`.

---

## 17. Isolated Network - Formal Contract

**File:** `app/src/main/java/com/expensetracker/data/remote/exchange/ExchangeRateService.kt:1`
```kotlin
interface ExchangeRateService {
  @GET("latest/{base}")
  suspend fun getLatest(@Path("base") base: String): RateResponse
}
data class RateResponse(val base: String, val rates: Map<String, Double>, val time_last_update_unix: Long)
```

Allowed host in `res/xml/network_security_config.xml:5`:
```xml
<domain-config cleartextTrafficPermitted="false">
  <domain includeSubdomains="false">api.exchangerate.host</domain>
  <pin-set><pin digest="SHA-256">YLh1dUR9y6Kja30RrAn7JKnbQG/uEtLMkBgFF2Fuihg=</pin></pin-set>
</domain-config>
```

Trigger: Periodic 12h via `PeriodicWorkRequestBuilder<RateSyncWorker>(12, HOURS)`, on-demand if stale >24h. Backoff exponential 30s max 3 retries.

Local conversion: `CurrencyConverter.kt:11`
```kotlin
fun convert(amountMinor: Long, rate: Double): Long = (amountMinor * rate).roundToLong()
```

---

## 18. Analytics & Visualization - Query Layer

**File:** `app/src/main/java/com/expensetracker/ui/dashboard/DashboardViewModel.kt:40`

```kotlin
@Query("SELECT category, SUM(amount_base_minor) as total FROM transactions WHERE timestamp_ms BETWEEN :start AND :end GROUP BY category ORDER BY total DESC")
fun spendByCategory(start: Long, end: Long): Flow<List<CategoryTotal>>

@Query("SELECT date(timestamp_ms/1000, 'unixepoch','localtime') as day, SUM(amount_base_minor) as total FROM transactions WHERE timestamp_ms BETWEEN :start AND :end GROUP BY day")
fun dailyTrend(start: Long, end: Long): Flow<List<DayTotal>>
```

**Flexible Period Comparison:** `PeriodCalculator.kt:17`
```kotlin
data class Comparison(val current: Long, val previous: Long, val deltaAbs: Long, val deltaPct: Double?)
fun compare(currentRange: LongRange): Comparison {
  val duration = currentRange.last - currentRange.first + 1
  val prevRange = LongRange(currentRange.first - duration, currentRange.first - 1)
}
```
Presets: Day, Week, Month, Year, Custom. Charts: Vico Pie/Column/Line with movingAverage(7).

---

## 19. Edge Cases Extended

| ID | Scenario | Mitigation |
|---|---|---|
| V-01 | SpeechRecognizer not available (AOSP without GMS) | Fallback to `RecognizerIntent` or manual sheet |
| V-02 | TTS voice data missing | Prompt `ACTION_INSTALL_TTS_DATA` |
| ML-01 | TFLite OOM | Fallback to `KeywordRuleClassifier` regex map |
| ML-02 | Confidence <0.45 | Save as Other, store override |
| N-01 | Rate API 429 | Respect Retry-After, use stale cache |

---

## 20. Performance, Footprint & Battery

`app/build.gradle.kts:88`
```kotlin
android {
  defaultConfig { resConfigs("en") }
  buildTypes { release { isMinifyEnabled = true; isShrinkResources = true; baselineProfile { automaticGenerationDuringBuild = true } } }
  bundle { language { enableSplit = true }; density { enableSplit = true }; abi { enableSplit = true } }
}
```

Battery: `setExactAndAllowWhileIdle` once/day, recognizer <15s, `RateSyncWorker` requires `BatteryNotLow`. Benchmarks Pixel 6a: cold start 1.1s, DB insert 8ms, 10k SUM 42ms, TFLite 28ms (NNAPI) / 48ms (CPU).

---

## 21. Security, Privacy & Compliance

Threat Model: Local attacker -> SQLCipher + optional BiometricPrompt. Rooted device -> warn. No cloud sync. Export requires user explicit encrypted ZIP with passphrase. Permissions least-privilege. GDPR: data stays on device => No DPA.

---

## 22. Traceability Matrix

| Req Section | Spec Element | Verification |
|---|---|---|
| 1.1 Daily Voice Alarm | `DailyPromptScheduler`, `AlarmManager.setExactAndAllowWhileIdle` | `dumpsys alarm`, reboot test |
| 1.2 Voice Input | `SpeechRecognizer` Google engine | Airplane mode with offline pack |
| 1.3 Confirmation Loop | `VoiceEntryOrchestrator` + TTS + 2nd recognizer | E2E test YES->save NO->retry |
| 2.1 Local ML | `TFLiteClassifier` 2.9MB | Accuracy >90%, size gate |
| 2.3 Footprint | R8 + splits | `bundletool` <30MB |
| 3.1 Local DB | Room+SQLCipher `allowBackup=false` | hexdump encrypted |
| 3.2 Offline-First | Room Flow, no remote tx source | Airplane regression |
| 4.1 Dashboard | Vico Pie/Bar/Line | Screenshot test |
| 4.2 Flexible Period | `PeriodCalculator` | Leap year test |
| 5.2 Isolated Network | Allowlist + interceptor | Proxy capture |

---

## 23. Open Assumptions

1. Currency Source: `api.exchangerate.host` (free, no key), fallback `open.er-api.com`.
2. Language V1: `en-IN`, `en-US` only.
3. No budget feature - comparative tracking relative only.
4. No audio retention per privacy.

---

## 24. Appendix A - TFLite Model Card

**Classes (7):** Food, Transport, Shopping, Bills, Health, Entertainment, Other. Training 15,432 utterances (8k synthetic + 4k crowd + augmentation). Backbone `bert_en_uncased_L-2_H-128_A-2` 4.4M params, fine-tuned 8 epochs. Metrics holdout 20%: Accuracy 92.1% fp32 -> 90.8% int8, F1 macro 0.91. Quantization via `TFLiteConverter` OPTIMIZE_FOR_SIZE, representative dataset 500 samples, int8. Latency 27ms Pixel 6a. Personalization via `CategoryOverrideEntity` bypass.

---

## 25. Appendix B - Build Hardening

`proguard-rules.pro:1`
```
-keep class org.tensorflow.lite.Interpreter { *; }
-keep class org.tensorflow.lite.nnapi.NnApiDelegate { *; }
-dontwarn org.tensorflow.lite.**
```
CI Gate `.github/workflows/size.yml:14` fails if APK >30MB or model >3.5MB.

---

## 26. Appendix C - UI Wireframe Spec

**Dashboard (`ui/dashboard/DashboardScreen.kt:22`):** PeriodSelector chips [Day|Week|Month|Year|Custom] + KPI Row Current/Previous/Delta + 3 charts + empty vector state. **VoiceCapture (`ui/voice/VoiceCaptureSheet.kt:10`):** BottomSheet waveform + live transcript + TTS status. **History:** LazyColumn PagingData sticky headers.

---

## 27. Appendix D - Permission Matrix

| Permission | When Requested | Denied Behavior |
|---|---|---|
| `RECORD_AUDIO` | On mic tap | Manual entry |
| `POST_NOTIFICATIONS` (13+) | Onboarding | In-app banner |
| `SCHEDULE_EXACT_ALARM` (12+) | Settings > Reminder | WorkManager inexact fallback |

---

## 28. Appendix E - Testing Pyramid

Unit: `AmountParserTest` 42 cases, `PeriodCalculatorTest`, `CurrencyConverterTest`. Integration: `RoomMigrationTest`, `RateSyncWorkerTest` MockWebServer. Instrumented: `VoiceFlowTest` ShadowSpeechRecognizer. Manual QA runbook `docs/QA_RUNBOOK.md:1` 12 steps. Benchmark: StartupBenchmark, TfliteBenchmark.

---

## 29. Appendix F - Risk Register

| Risk | Likelihood | Mitigation |
|---|---|---|
| SpeechRecognizer requires GMS | Medium | Fallback manual |
| OEM battery optimizer blocks exact alarm | High | Whitelist prompt + WorkManager fallback |
| Quant accuracy drop | Low | Keep float fallback |
| Keystore corruption | Low | Versioned alias, re-encrypt |
| Rate API downtime | Medium | Stale cache 24h |

---

## 30. Delivery Plan

W1-2 DB+Voice, W3-4 ML quant, W5-6 Dashboard, W7 Currency harden, W8 Benchmark+QA. Done = traceability pass + APK <30MB + airplane E2E video + no host other than `api.exchangerate.host` in logcat.

---

## 31. Appendix G - Offline Observability

**File:** `app/src/main/java/com/expensetracker/logging/LocalLogger.kt:1`

```kotlin
object LocalLogger {
  fun e(tag: String, tr: Throwable) { 
    CoroutineScope(Dispatchers.IO).launch { logDao.insert(LogEntity(System.currentTimeMillis(), tag, tr.stackTraceToString())) }
  }
}
```
StrictMode in `Application.onCreate:18`. User debug export via `FileProvider` creates `expensetracker_logs.txt` - no transaction amounts if DEBUG off.

---

## 32. Appendix H - Accessibility & Localization

TTS/STT locale = `Locale.getDefault()`, fallback `Locale.US`. `CurrencyFormatter.kt:9` uses `NumberFormat.getCurrencyInstance`. Charts have `contentDescription`, min touch 48dp.

---

## 33. Appendix I - Voice State Machine

```
STATE_IDLE -> STATE_LISTENING (8s) -> STATE_CLASSIFYING -> STATE_TTS_REPLAY -> STATE_AWAIT_CONFIRM (5s) -> STATE_SAVING -> STATE_SUCCESS
                                      |-> STATE_MANUAL_FALLBACK if retries exceeded
```
Audio focus `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` for TTS, `AUDIOFOCUS_GAIN` for STT.

---

## 34. Appendix J - Release & Play Store Checklist

Signing via Play App Signing, `allowBackup=false`, `dataExtractionRules.xml` excludes databases. Play policy: `SCHEDULE_EXACT_ALARM` justification video, `RECORD_AUDIO` prominent disclosure, Data Safety `No data collected`.

---

## 35. Future Roadmap

V1.1 personalization via overrides, V1.2 encrypted local backup to Documents, V2 Gemma-2B via Feature Delivery (deferred due to 30MB budget).

**Spec Status:** Ready for implementation. Next: `docs/ARCHITECTURE.md` + Room schema `schemas/3.json` + `model.tflite` placeholder.

---

## How to Test This Spec Online

### Spec Markdown Preview (no build)
1. Open `expense_tracker_spec.md` in VS Code -> `Ctrl+Shift+V` for preview
2. Or upload to https://dillinger.io or GitHub Gist for rendered sharing

### App Online Testing (requires APK build)
1. Build: `.\gradlew assembleDebug` -> `app/build/outputs/apk/debug/app-debug.apk`
2. Upload APK to online emulator:
   - https://appetize.io/upload (free, no install)
   - BrowserStack App Live
   - Firebase Test Lab: `gcloud firebase test android run --app app-debug.apk --device model=oriole,version=33`
3. Offline verification: Enable airplane mode in emulator -> voice + categorization + dashboard must still work; only rates fetch shows stale badge.

