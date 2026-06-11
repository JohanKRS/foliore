# Foliore

Foliore is an Android-first, local-first EPUB library manager. It is not an ebook reader: it imports EPUB files, keeps the original intact, extracts metadata and cover information, and prepares the foundation for editing/exporting clean copies later.

## Current Delivery

This repository contains the first implementation slice from `plan.md`:

- Kotlin Multiplatform `shared` core.
- Android app with Jetpack Compose and Material 3.
- SQLDelight schema for the local library.
- Android Storage Access Framework EPUB import.
- SHA-256 duplicate detection.
- Minimal EPUB parser for `META-INF/container.xml`, OPF path, title, author, language, metadata, and cover lookup.
- Editorial library UI inspired by the provided concept images.
- Persisted appearance setting with system, light, and dark themes.

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
