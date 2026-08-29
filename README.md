# ExpenseTracker - Offline-First AI Expense Tracker

Buildable Android scaffold from spec `expense_tracker_spec.md`.

## Features Implemented (Scaffold)
- Daily voice alarm via `AlarmManager.setExactAndAllowWhileIdle` + `PromptReceiver` + `BootReceiver`
- Voice input via `SpeechRecognizer` (Google engine) + `TTSManager` confirmation loop (Yes/No)
- On-device categorization via `TFLiteClassifier` (keyword fallback, placeholder for 2.9MB quantized model `assets/expense_classifier.tflite`)
- Encrypted Room + SQLCipher local DB (`expenses.db`, no cloud sync)
- Isolated network: `ExchangeRateService` only sends base currency to `api.exchangerate.host`
- Dashboard with PeriodCalculator (Day/Week/Month/Year) + Room SUM GROUP BY queries

## Build Locally

1. Install Android Studio Hedgehog+ (includes JDK 17 + SDK 34)
2. Open folder `ExpenseTracker` in Android Studio
3. Sync Gradle (will download dependencies)
4. Run: `./gradlew assembleDebug` -> APK at `app/build/outputs/apk/debug/app-debug.apk`
   - Size check target 20-30MB (R8 + splits enabled in release)
5. Install: `adb install app/build/outputs/apk/debug/app-debug.apk`

## Build in Cloud (No local SDK needed)

1. Create GitHub repo, push this folder:
   ```
   git init
   git add .
   git commit -m "init"
   git remote add origin https://github.com/<you>/ExpenseTracker.git
   git push -u origin main
   ```
2. GitHub Actions will auto-build (`build.yml`) -> Download APK from Actions > Artifacts > `app-debug` / `app-release`
3. Or use https://appetize.io/upload to test APK online without device

## Test Offline-First

- Enable airplane mode -> voice entry + categorization + dashboard still work
- Currency rates show stale badge if offline >24h
- Check isolation: `adb logcat | grep exchangerate` should only show base currency, never amounts

## Next Steps to Reach Full Spec
- Bundle quantized model `app/src/main/assets/expense_classifier.tflite` (2.9MB) + `vocab.txt`
- Replace passphrase in `di/AppModule.kt` with AndroidKeystore-secured key
- Add MPAndroidChart rendering in `DashboardScreen.kt`
- Add `baseline-prof.txt` for startup optimization
