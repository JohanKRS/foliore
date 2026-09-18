# Kindle batch delivery — 2026-09-18

Branch: `codex/gmail-send`, based on `0305074`.

Library selection now exposes Send to Kindle. Gmail and SMTP group prepared EPUBs by attachment count and estimated MIME size, send sequentially, and report each book's outcome. See [delivery behavior and limits](../../../GMAIL.md#sending-selected-books).

## Automated checks

- `:androidApp:testDebugUnitTest`: **190 tests, 0 failures, 0 errors, 0 skipped**, including native conversion tests (`-PconversionNativeLibraryDir=/tmp/foliore-native-build`).
- `:androidApp:assembleDebug`, `:androidApp:lintDebug`, signed `:androidApp:assembleRelease`: passed.
- `git diff --check`: passed.
- APK signature verified (v2); release is not debuggable.

Batch tests cover 60 small books becoming 25 + 25 + 10 attachments; deduplicated book IDs; splits by combined raw size and SMTP encoded size; oversize/preparation errors alongside valid books; failure after an accepted message; lost responses; expired authorization/quota failures; cancellation; cleanup; empty selection. The 60-book case stages at most 26 files (one message plus the next book), deleting consumed files.

MIME tests decode every attachment independently, check Unicode and long filenames, folded headers, CRLF and line lengths, reject excess count/combined size before network access, and exercise SMTP dot stuffing across write boundaries. Existing authentication/TLS rejection tests still pass.

## Runtime and UI

Disposable `FolioreBatchQA` emulator, Android 36 Google APIs x86_64, 1080×2400. No Google account added and no external email sent.

Imported the existing Gutenberg fixtures for *Pride and Prejudice*, *Alice’s Adventures in Wonderland* and *On the Origin of Species*. The debug-only `kindleBatchTest` harness ran the actual preparation and Gmail MIME upload code against an in-memory connection. Two EPUBs formed one captured email; the illustrated Pride edition's prepared file was 25,157,090 bytes and was reported as too large. All original file hashes remained unchanged, and staging was removed. A separate Python email parser decoded both attachments and verified their ZIP contents and EPUB mimetype. `runtime-result.txt` records the integration outcome. The captured MIME stays in ignored local artifacts.

UI checks used a synthetic SMTP account on loopback port 9 (connection refused). Verified long-press selection, selecting multiple books, missing-account guidance, the send action, and a result distinguishing oversize from failed delivery. Definitely unsent books remained selected. Checked English/light and Portuguese/dark layouts, long titles, font scale 1.3, and landscape rotation with the result retained. All three rows and the close control remained reachable. Crash buffer was empty.

Screenshots/XML: `kindle-batch-results-pt-dark` and `kindle-batch-results-landscape-stable`.

**Real Gmail consent/refresh, SMTP provider acceptance and Kindle ingestion still need the owner's device test.** Local success indicates correct preparation and MIME handling, not delivery through Google or Amazon.

## Signed preview

`Foliore-0.1.0-gmail-batch-preview-2026-09-18.apk` — version 0.1.0 (2).

SHA-256: `bca3c2323daa166b45ea3225c48301f672a2a4e56310881560cb81e8a592e325`.

Built with the existing local signing/billing configuration; temporary configuration links removed afterward. No push or merge performed for this change.
