# Compact reader controls and paginated clearance

This refinement replaces the overlay-only paginated layout from the earlier reader-layout audit. Paginated, reflowable books now reserve the measured height of both bars. The reader retains that clearance in fullscreen so showing/hiding controls never resizes or repaginates the content. Scrolling and fixed-layout publications retain the overlay behavior.

The header removes the redundant “Reader” subtitle and uses a compact, single-line title with the existing actions. The footer has one small information row (chapter, page/chapter position, percentage) and one control row (contents, previous, slider, next, bookmarks). Icons retain accessible labels. Hidden bars are measured without being drawn or receiving touches, and their accessibility semantics are explicitly cleared.

On the Android 36 Pixel 8 emulator, the paginated viewport increased from **412 × 668** to **412 × 729 CSS pixels**, approximately **9.1% more vertical space** than the full-size bars with clearance. The book's own vertical margins remain inside that viewport. Bar measurements adapt to orientation and system font scaling rather than using a hardcoded bottom inset.

Validation:

- Debug build, Android lint and signed release build passed.
- First, middle and last pages checked in portrait at system font scale 1.0 and 1.3, plus landscape at scale 1.3.
- All **36 paginated fullscreen toggles** preserved the full position/viewport snapshot. Native bounds showed all header controls above and footer controls below the WebView.
- **Six scrolling fullscreen toggles** preserved the full snapshot; scrolling still uses the full 412 × 840 viewport.
- Hidden controls disappeared from the Android accessibility tree. A real center tap restored them.
- Real touch checks: next/previous returned to the same passage; contents and bookmarks opened their dialogs; reading options switched modes without losing the passage; the compact slider still sought within the chapter.
- Visual review: [portrait](portrait.png), [landscape with larger system text](landscape-large.png). Compact measurements are in [results.json](results.json); full local measurements remain in `.codex-artifacts/reader-clearance/`.

APK: `Foliore-0.1.0-reader-compact-preview-2026-09-18.apk`, signed release, `debuggable=false`.

SHA-256: `956004b24ba6875430110daac8875091a38b75422ad50f6222b93c94613866d9`.

Validation covers this emulator/WebView combination; no cloud operation or real email was sent.
