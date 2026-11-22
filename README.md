# Foliore

Foliore is a free and open source, Android-first, local-first EPUB library manager. Its main focus is organizing and preparing books for ebook readers. It imports EPUB files and converts unprotected MOBI/AZW3 files to EPUB on-device, keeps the source intact, extracts metadata and cover information, edits metadata and covers, and exports prepared copies.

## Current Delivery

The Android app currently includes:

- Kotlin Multiplatform core, SQLDelight storage, and Compose UI.
- Bounded EPUB import, original-file preservation, metadata/cover editing and export.
- Optional built-in EPUB reader with pagination, search, bookmarks, typography controls and synchronized reading positions.
- Offline MOBI/AZW3 conversion using libmobi, with retained local source files and explicit DRM errors.
- Optional metadata lookup, Gmail OAuth delivery and TLS-only SMTP delivery.
- Optional AI features: Sign in with ChatGPT fills in synopses and book details and finds existing covers on the user’s own ChatGPT plan; an OpenAI API key also generates new covers. Neither is required to use the app.
- Offline synchronization through hidden Google Drive app data or a selected SAF folder.
- Immutable remote revisions, causal merging, conflict copies and bounded retention.
- Light/dark glass surfaces, localization and in-app privacy/reporting entries.

See [Gmail delivery and setup](docs/GMAIL.md), [built-in reader](docs/READER.md), [sync protocol](docs/SYNC_SPEC.md), [pre-publication audit](docs/audits/2026-09-07/README.md), and [reporting service setup](support/README.md). The public privacy site, reporting endpoint, developer identity, support mailbox, OAuth configuration and release signing must be verified before distribution; `publication.properties.example` records the release confirmations.

## Free and open source

Foliore is free software released under the [GNU General Public License v3.0](LICENSE). There are no purchases, plans or library limits, and no account is required: Google Drive, Gmail, Kindle, ChatGPT and the OpenAI API are all optional. Third-party components keep their own licenses; see [third-party notices](third-party/README.md).

Signed APKs are published on [GitHub Releases](https://github.com/JohanKRS/Foliore/releases). See the [changelog](CHANGELOG.md) and [how releases are made](docs/RELEASING.md).

### Sign in with ChatGPT

The optional ChatGPT connection uses OpenAI’s [Sign in with ChatGPT](https://developers.openai.com/siwc) program for open source projects: OAuth 2.0 with PKCE, a dynamically registered client and a loopback redirect served by the app on `127.0.0.1` while the browser sign-in is open. Requests go straight from the device to the OpenAI Responses API and count toward the user’s ChatGPT plan (Plus or Pro). Tokens are encrypted with the Android Keystore and never leave the device except in requests to OpenAI. Image generation is not available through this connection yet, so new covers still use an OpenAI API key; the code path is ready behind `AiFeatureFlags.CHATGPT_IMAGE_GENERATION`.

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
