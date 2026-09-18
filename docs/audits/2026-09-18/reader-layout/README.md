# Reader layout — 18 September 2026

Fixed three reported problems: reflow when showing/hiding reader controls, uneven final-page margins, and sideways movement during text selection.

## Changes and reproduced causes

- The controls previously removed space from the WebView, and hiding the Android system bars changed its insets. The scrolling fixture changed from **412 × 668** to **412 × 864** CSS pixels. Controls now overlay the reading surface, with stable system-bar/cutout insets. Showing them temporarily covers the top and bottom of the page; a center tap hides them without repagination.
- The paginated fixture's final page requested horizontal position **9,476**, but the browser clamped it to **9,451.81**. This shifted the margins and exposed part of the preceding column. Pages now use a translation by the exact viewport width, independently of the browser's scroll range. First, middle and last pages of the plain-text fixture all have **24 px** left/right margins at the default setting.
- Reflowable chapters use a clipped outer frame, and selection cannot pan the native WebView or its outer document. Vertical scrolling and nested table scrolling remain independent. Native zoom remains available for fixed-layout publications; original illustrations retain their separate image viewer.
- Sanitized chapters always receive an HTML5 doctype so EPUBs without one do not fall into quirks layout. Older asynchronous layout callbacks cannot overwrite a newer configuration.

## Validation

**191 Android unit tests**, including native conversion tests, passed. **37 JavaScript tests** passed, including new regressions for selection, final-page positioning and switching modes. Android lint, debug and signed release builds passed.

On a disposable Android 36 Pixel 8 emulator, using the actual Android WebView:

| Publication | Layout checks |
| --- | ---: |
| Fixed-layout fixture | 2 |
| Portuguese reader fixture | 15 |
| Wide images/tables fixture | 30 |
| RTL fixture | 30 |
| Gutenberg: Pride and Prejudice | 30 |
| Gutenberg: Alice's Adventures in Wonderland (converted MOBI) | 15 |
| **Total** | **122** |

The matrix includes chapter start/middle/end, paginated and scrolling modes, 12/20/36 px fonts, 8/24/64 px margins, paper/light/dark themes and publisher formatting. Fixed-layout chapters retain their own sizing. All chapters rendered in standards mode, with no broken images detected.

**66 real native fullscreen toggles** preserved the complete viewport/position snapshot, including dimensions, text offset, quote, page count, translation and scroll offsets. The reflowable viewport stayed at **412 × 840** CSS pixels. Programmatic selection/focus on text in another column could not scroll the paginated frame away from its current page.

Native long-press selection was visually checked in both reading modes. In paginated mode the word “texto” was selected on page 10/19; selecting the entire chapter with Ctrl+A kept that page and all horizontal offsets unchanged. In scrolling mode a settled long-press preserved both horizontal and vertical offsets. The native copy/selection toolbar remained visible. See [paginated selection](selection-pages.png) and [scrolling selection](selection-scroll.png). No app crashes were recorded.

This verifies the Android 36 emulator/WebView combination, not every device or WebView version. No real email, cloud synchronization or account operation was performed for this reader-only change. Full local measurements are in `.codex-artifacts/reader-layout/`; [compact results](results.json) preserve the fullscreen snapshots.

## Preview APK

`Foliore-0.1.0-reader-layout-preview-2026-09-18.apk` — signed release, `debuggable=false`.

SHA-256: `7f1ed4de9597d06d0452f3d290ceb3ea6f12cf314bdd2ad6cbceba991cdf89c8`

Repeatable automated checks:

```sh
node --test scripts/test_reader_gestures.mjs scripts/test_reader_image_tones.mjs scripts/test_reader_image_viewer.mjs
ANDROID_HOME=/home/johan/Android/Sdk ./gradlew :androidApp:testDebugUnitTest :androidApp:lintDebug -PconversionNativeLibraryDir=/tmp/foliore-native-build --console=plain
```
