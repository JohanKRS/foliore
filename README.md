# Foliore

Foliore is an Android-first, local-first EPUB library manager. It is not an ebook reader: it imports EPUB files, keeps the original intact, extracts metadata and cover information, edits metadata and covers, and exports prepared copies.

## Current Delivery

The Android app currently includes:

- Kotlin Multiplatform core, SQLDelight storage, and Compose UI.
- Bounded EPUB import, original-file preservation, metadata/cover editing and export.
- Optional metadata lookup, AI cover generation, and TLS-only SMTP delivery.
- Offline synchronization through hidden Google Drive app data or a selected SAF folder.
- Immutable remote revisions, causal merging, conflict copies and bounded retention.
- Light/dark glass surfaces, localization and in-app privacy/reporting entries.

See [sync protocol](docs/SYNC_SPEC.md), [pre-publication audit](docs/audits/2026-09-07/README.md), and [reporting service setup](support/README.md). The public privacy site, reporting endpoint, developer identity, support mailbox, OAuth configuration and release signing must be verified before distribution; `publication.properties.example` records the release confirmations.

## Open In Android Studio

Open this folder in Android Studio and let Gradle sync. The Gradle Wrapper is included and was verified with the JBR bundled in Android Studio plus the Android SDK under the standard Windows user path.

On Windows, the shared module configures Android targets only. iOS targets are enabled when the project is opened on macOS, avoiding Kotlin/Native downloads during Android-first work.

## Module Layout

```text
shared/      KMP domain models, parser, SQLDelight schema, repository
androidApp/  Android Compose app, SAF import, storage adapters, theme
iosApp/      Placeholder for future native SwiftUI app
docs/        Product and implementation notes distilled from plan.md
```
