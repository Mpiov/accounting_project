# Autobook Android Skeleton

Autobook is an Android-first accounting app skeleton for notification-driven bookkeeping in China. This repo currently contains the Android client only, with local mock parsing and cloud analysis placeholders.

## Stack

- Kotlin + Jetpack Compose
- Hilt
- Coroutines + Flow
- Room + SQLCipher
- JUnit5 + MockK

## Modules

- `app`: app entry, navigation, view models, top-level dependency graph
- `core:model`: domain models and shared result types
- `core:common`: Android helpers and formatting utilities
- `core:network`: cloud analysis contracts and DTO placeholders
- `core:database`: Room database, DAOs, entities
- `core:data`: repositories, DataStore settings, parsing pipeline
- `feature:onboarding`: permission guide UI
- `feature:ledger`: ledger home UI
- `feature:pending`: pending confirmation UI
- `service:notification`: notification listener service skeleton
- `service:automation`: accessibility and capture coordination skeleton

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
- Notification events can be ingested into the local pipeline.
- High-confidence parsed transactions are written to the ledger.
- Lower-confidence candidates are routed to the pending queue.

## Follow-up Integration

- Replace `FakeCloudAnalysisApi` with a real backend client.
- Replace mock OCR and parser implementations with on-device OCR / LLM routing.
- Wire MediaProjection session management into the capture coordinator.
