# Basir AI v0.4.1 Build Audit

This package is a build-hardened revision of v0.4.0.

## Build target

- Android Gradle Plugin: 8.13.2
- Gradle used by GitHub Actions: 8.13
- Java used by GitHub Actions: Temurin 17
- compileSdk: 35
- targetSdk: 35
- minSdk: 23
- APK artifact: `BasirAI-debug-apk`

## Hardening changes

- Added GitHub Actions preflight checks for required project files and Android package identity.
- Added explicit build environment variables in the workflow.
- Added SDK license acceptance and installed Android SDK platform/build-tools before Gradle build.
- Added `gradle tasks` validation before `gradle assembleDebug`.
- Added `if-no-files-found: error` to artifact upload.
- Updated version to `0.4.1-build-audited` / versionCode 5.
- Improved runtime permission request to include coarse location in addition to fine location.
- Adjusted contact mail URI and SMS URI handling.

## Local audit performed

- ZIP integrity check: passed.
- XML parse check for manifest/resources: passed.
- YAML parse check: passed.
- `server/index.js` syntax check with Node: passed.
- Java source structural checks: balanced braces, parentheses, and brackets.
- No merge-conflict markers or ellipsis placeholders found.

## Limitation

The local container used for this audit does not include Android SDK or Gradle, so the APK build itself is intended to run in GitHub Actions using the included workflow.
