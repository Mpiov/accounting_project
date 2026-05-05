# Autobook Android

Autobook is an Android-first accounting app for notification-driven bookkeeping in China. The current app keeps notification auto-bookkeeping local: notifications are parsed by rules, then routed to ledger, pending review, or filtered.

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
- `core:network`: cloud image-import client
- `core:database`: Room database, DAOs, entities
- `core:data`: repositories, DataStore settings, parsing pipeline, duplicate detection
- `feature:onboarding`: permission guide UI
- `feature:ledger`: ledger home UI
- `feature:pending`: pending confirmation UI
- `service:notification`: notification listener service
- `service:automation`: accessibility service for user-triggered page capture

## Run

1. Ensure Java 17 is available.
2. Cloud image import goes through the `api.wenapi.shop` reverse proxy; no upstream model API key needs to be written into `local.properties`.
3. Run `./gradlew assembleDebug`.
4. Install the generated debug APK from `app/build/outputs/apk/debug/`.

## Quality Gates

- `./gradlew assembleDebug`
- `./gradlew testDebugUnitTest`
- `./gradlew ktlintCheck`

## Current Behavior

- On first launch, the app opens a permission guide and capability toggle flow.
- Users can keep using the app with manual bookkeeping even if notification or accessibility permissions are not granted.
- Notification events are normalized with source tagging, process status tracking, and retry metadata.
- Notification auto-bookkeeping now uses local rule-based text parsing only.
- High-confidence parsed transactions are written to the ledger, medium-confidence or possible-duplicate candidates are routed to the pending queue, and low-confidence candidates are filtered out.
- Ledger now supports basic search, day/month/all filters, manual add/edit/delete, and a settings page for source selection and retention controls.
- Image import bookkeeping supports real image picking plus suggestion prefill through cloud image-analysis calls, followed by local rule-based post-processing.
- When cloud analysis is enabled, image import can call the configured cloud model proxy from the app during development; when cloud analysis is disabled, the app falls back to a manual confirmation draft.
- All image-analysis results are still suggestion-only and must be manually confirmed before saving.

## Docs

- Current architecture: `docs/框架.md`
- Notification confidence rules: `docs/置信度规则.md`
- Production maintenance notes: `docs/上线维护注意事项.md`
