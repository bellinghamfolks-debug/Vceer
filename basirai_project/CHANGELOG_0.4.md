# Basir AI 0.4.0-final-review

## What changed

- Added a dedicated **About and Contact** screen.
- Added contact email inside the app: `ubdallahalrashdee@gmail.com`.
- Added an email action that opens the user's mail app with a Basir AI feedback subject.
- Rebuilt the main UI text flow to support Arabic/English switching across the primary screens, buttons, prompts, and core messages.
- Updated Text-to-Speech language switching when the user changes language.
- Updated versionCode to 4 and versionName to `0.4.0-final-review`.
- Kept the secure GPT-5.5 proxy design: the OpenAI API key stays on the server and is never stored in the APK.
- Kept image analysis through the proxy using selected gallery images.
- Added clear safety wording for walking, health, legal, and emergency features.
- Added `android:usesCleartextTraffic="true"` for development proxy testing. Production deployments should still use HTTPS.

## Build review

- Android Gradle Plugin: `8.13.2`.
- Gradle installed by GitHub Actions: `8.13`.
- Java: `17`.
- compileSdk: `35`.
- targetSdk: `35`.
- minSdk: `23`.
- Main app source: Java only; no Kotlin dependency required.
- External Android dependencies: none.
- Server syntax check: `node --check server/index.js` passed.
- JSON validation: `server/package.json` passed.
- Static source integrity check: Java brace balance passed, no merge markers or truncation markers found.

## Known limitation

The current environment does not include the Android SDK/Gradle runtime, so APK compilation was not executed locally here. The included GitHub Actions workflow is configured to install the Android SDK packages and Gradle, then run `gradle assembleDebug --stacktrace`.
