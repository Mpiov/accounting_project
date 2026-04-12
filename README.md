# Autobook Android MVP

Autobook is an Android-first accounting app for notification-driven bookkeeping in China. This repository currently contains the Android client, a local-first parsing pipeline, manual bookkeeping fallback flows, and cloud/OCR integration placeholders.

## Stack

- Kotlin + Jetpack Compose
- Hilt
- Coroutines + Flow
- Room + SQLCipher
- JUnit5 + MockK

## Modules

- `app`: app entry, navigation, view models, manual entry editor, settings UI
- `core:model`: domain models and shared result types
- `core:common`: Android helpers and formatting utilities
- `core:network`: cloud analysis contracts and DTO placeholders
- `core:database`: Room database, DAOs, entities, passphrase provider
- `core:data`: repositories, DataStore settings, parsing pipeline, duplicate detection
- `feature:onboarding`: permission guide UI
- `feature:ledger`: ledger home UI
- `feature:pending`: pending confirmation UI
- `service:notification`: notification listener service skeleton
- `service:automation`: accessibility and capture coordination placeholder

## Run

1. Ensure Java 17 is available.
2. Run `./gradlew assembleDebug`.
3. Install the generated debug APK from `app/build/outputs/apk/debug/`.

## Quality Gates

- `./gradlew assembleDebug`
- `./gradlew testDebugUnitTest`
- `./gradlew ktlintCheck`

## Current Behavior

- On first launch, the app opens a permission guide and capability toggle flow.
- Users can keep using the app with manual bookkeeping even if notification or accessibility permissions are not granted.
- Notification events are normalized with source tagging, dedupe keys, process status tracking, and retry metadata.
- High-confidence parsed transactions are written to the ledger, while low-confidence or possible-duplicate candidates are routed to the pending queue.
- Ledger now supports basic search, day/month/all filters, manual add/edit/delete, and a settings page for source selection and retention controls.
- Database encryption no longer uses a fixed hard-coded passphrase; the app now generates and stores a device-local passphrase.

## Follow-up Integration

- Replace `FakeCloudAnalysisApi` with a real backend client.
- Replace placeholder OCR and parser implementations with production OCR / LLM routing.
- Wire MediaProjection session management into the capture coordinator.
- Add import/export UI, richer analytics, and formal privacy materials for release readiness.
