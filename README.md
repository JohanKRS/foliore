# Foliore

Foliore is an Android-first, local-first EPUB library manager. Its main focus is organizing and preparing books for ebook readers. It imports EPUB files and converts unprotected MOBI/AZW3 files to EPUB on-device, keeps the source intact, extracts metadata and cover information, edits metadata and covers, and exports prepared copies.

## Current Delivery

The Android app currently includes:

- Kotlin Multiplatform core, SQLDelight storage, and Compose UI.
- Bounded EPUB import, original-file preservation, metadata/cover editing and export.
- Optional built-in EPUB reader with pagination, search, bookmarks, typography controls and synchronized reading positions.
- Offline MOBI/AZW3 conversion using libmobi, with retained local source files and explicit DRM errors.
- Optional metadata lookup, AI cover generation, Gmail OAuth delivery and TLS-only SMTP delivery.
- Offline synchronization through hidden Google Drive app data or a selected SAF folder.
- Immutable remote revisions, causal merging, conflict copies and bounded retention.
- Light/dark glass surfaces, localization and in-app privacy/reporting entries.

See [Gmail delivery and setup](docs/GMAIL.md), [built-in reader](docs/READER.md), [sync protocol](docs/SYNC_SPEC.md), [pre-publication audit](docs/audits/2026-09-07/README.md), and [reporting service setup](support/README.md). The public privacy site, reporting endpoint, developer identity, support mailbox, OAuth configuration and release signing must be verified before distribution; `publication.properties.example` records the release confirmations.

## Open In Android Studio

Open this folder in Android Studio and let Gradle sync. The Gradle Wrapper is included and was verified with the JBR bundled in Android Studio plus the Android SDK under the standard Windows user path.

On Windows, the shared module configures Android targets only. iOS targets are enabled when the project is opened on macOS, avoiding Kotlin/Native downloads during Android-first work.

Android conversion also builds native C libraries with NDK `28.2.13676358` and CMake `3.22.1` (install through SDK Manager). Source is vendored, so native builds do not fetch code from GitHub. See [format conversion](docs/FORMAT_CONVERSION.md) for limits, verification and LGPL source distribution.

## Module Layout

```text
shared/      KMP domain models, parser, SQLDelight schema, repository
androidApp/  Android Compose app, SAF import, storage adapters, theme
iosApp/      Placeholder for future native SwiftUI app
docs/        Product and implementation notes distilled from plan.md
```
