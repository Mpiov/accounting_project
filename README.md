# Autobook Android MVP

Autobook is an Android-first accounting app for notification-driven bookkeeping in China. This repository currently contains the Android client, a local-first parsing pipeline, manual bookkeeping fallback flows, and cloud/OCR integration placeholders.

## Stack

- Kotlin + Jetpack Compose
- Hilt
- Coroutines + Flow
- Room
- JUnit5 + MockK

## Modules

- `app`: app entry, navigation, view models, manual entry editor, settings UI
- `core:model`: domain models and shared result types
- `core:common`: Android helpers and formatting utilities
- `core:network`: cloud analysis contracts and direct Gemini image-import client
- `core:database`: Room database, DAOs, entities
- `core:data`: repositories, DataStore settings, parsing pipeline, duplicate detection
- `feature:onboarding`: permission guide UI
- `feature:ledger`: ledger home UI
- `feature:pending`: pending confirmation UI
- `service:notification`: notification listener service skeleton
- `service:automation`: accessibility and capture coordination placeholder

## Run

1. Ensure Java 17 is available.
2. If you want to test cloud image import directly from the app, add `gemini.api.key=<your key>` to `local.properties`.
3. Run `./gradlew assembleDebug`.
4. Install the generated debug APK from `app/build/outputs/apk/debug/`.

## Quality Gates

- `./gradlew assembleDebug`
- `./gradlew testDebugUnitTest`
- `./gradlew ktlintCheck`

## Current Behavior

- On first launch, the app opens a permission guide and capability toggle flow.
- Users can keep using the app with manual bookkeeping even if notification or accessibility permissions are not granted.
- Notification events are normalized with source tagging, dedupe keys, process status tracking, and retry metadata.
- Notification auto-bookkeeping now uses text parsing plus local semantic enhancement, without OCR in the notification path.
- High-confidence parsed transactions are written to the ledger, while low-confidence or possible-duplicate candidates are routed to the pending queue.
- Ledger now supports basic search, day/month/all filters, manual add/edit/delete, and a settings page for source selection and retention controls.
- Image import bookkeeping supports real image picking and suggestion prefill.
- When cloud analysis is enabled, image import can directly call Gemini from the app during development.
- All image-analysis results are still suggestion-only and must be manually confirmed before saving.

## Follow-up Integration

- Move the current direct Gemini image-import call behind a backend proxy before larger-scale release.
- Replace local image-import OCR and light-model fakes with production implementations.
- Wire MediaProjection session management only for future user-triggered image workflows if still needed.
- Add import/export UI, richer analytics, and formal privacy materials for release readiness.
