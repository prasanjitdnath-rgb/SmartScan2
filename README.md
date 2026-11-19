# Smart Scan (Android, Kotlin + Jetpack Compose)

No login or signup. Min Android 10 (API 29). Needs Google Play services (scanner + OCR models).

Features: smart scanner (auto edge-detect, crop, filters, gallery import, multi-page) - on-device OCR -
select text directly on the scanned page (tap / long-press + drag) - edit (rotate, filters, delete pages, add pages) -
read aloud (TTS) - merge PDFs - import PDF - export to PDF, Word, Excel (all text), Excel (smart fields: invoice no,
date, amount, GST, vendor, custom columns), JPEG, TXT - search inside scans - AdMob (app-open ad at launch,
banner at the bottom, interstitial after every 2nd action).

## Build
1. Android Studio: open folder, sync, Build > Build APK(s).
2. GitHub: push; .github/workflows/build.yml builds app-debug.apk (Artifacts section of the run).

## Ads
Uses Google TEST ad IDs. Replace in AndroidManifest.xml (APPLICATION_ID) and AdManager.kt (BANNER_ID,
INTERSTITIAL_ID, APP_OPEN_ID) before publishing.
