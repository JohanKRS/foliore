# On-device format conversion

The existing **Import books** action accepts EPUB, MOBI and AZW3, including files shared into the app. MOBI/AZW3 conversion is automatic and offline; the result enters the same metadata, cover, duplicate-review and EPUB export flow. There is no conversion service, API key or extra purchase requirement. The existing library capacity limit applies to imported books.

The native engine is libmobi 0.12, pinned to `906274205c11944b628da1c553b255acb1af7c55`. It reconstructs MOBI6 and KF8 resources and selects KF8 in hybrid MOBI files. jsoup serializes legacy HTML as UTF-8 XHTML; Java writes the EPUB ZIP with an uncompressed first `mimetype` entry and stable timestamps. The existing EPUB parser validates the result before insertion. Conversion reconstructs the book; it does not guarantee pixel-identical layout in every reader.

DRM-protected books are rejected explicitly. Encryption support is disabled at compilation. KFX, AZW4/Print Replica, PDF and conversion from EPUB to Kindle formats are outside this implementation.

## Files, duplicates and limits

- `books/<id>/original.epub` is the immutable imported/converted EPUB. Its hash and size retain their existing database meanings.
- `originals/<id>/` retains the actual input separately, including EPUB, with its original display name. The local index remains after deletion so deleted originals are not recreated. Earlier `source.*` files are migrated on first access; older EPUB imports are archived from the immutable library base. Input files selected through SAF are never modified.
- **Original files** at the end of book details opens the standard options dialog: save a copy, share, or delete each original. Deletion requires confirmation and only removes the archived file on this device. The working library EPUB, edits and prepared export remain available.
- Sync and prepared exports transfer the library EPUB. Original archives are local only and remain independent of downloaded version directories. Local conflict copies retain their originals; deleting a book also removes its archive.
- Exact duplicate detection uses the EPUB hash. Content-equivalent copies still enter duplicate review. Discarding/rejecting/committing a pending import cleans both temporary source and EPUB files.
- Input is bounded at 256 MiB; native text reconstruction at 32 MiB; individual reconstructed parts at 32 MiB; expanded output at 512 MiB; archive entries at 10,000. Existing XML, image and EPUB limits still apply.

## Build and LGPL source

Install NDK `28.2.13676358` and CMake `3.22.1` through SDK Manager. The Android build includes `arm64-v8a`, `armeabi-v7a`, `x86` and `x86_64`. NDK r28 supplies 16 KiB ELF alignment on 64-bit targets.

libmobi and the adapter are separately linked `.so` files. `nativeConversionSource` generates a reproducible ZIP containing the matching native source, build files, GPL/LGPL texts and replacement instructions. Every APK carries this ZIP, exportable from **Settings > About > Libraries > libmobi**. The offline license catalog includes jsoup’s MIT notice and libmobi’s licenses and replacement rights. Run `scripts/third_party_notices.py` after changing those texts.

The precise source/rebuild instructions are in [INTEGRATION.txt](../third-party/libmobi/INTEGRATION.txt). The Foliore application itself is not assigned a new license by this change.

## Verification

Build the JNI libraries for host integration tests (requires CMake, JDK headers and zlib development files):

```sh
cmake -S androidApp/src/main/cpp -B /tmp/foliore-native-build
cmake --build /tmp/foliore-native-build
./gradlew :androidApp:testDebugUnitTest :androidApp:assembleDebug :androidApp:lintDebug \
  -PconversionNativeLibraryDir=/tmp/foliore-native-build
```

Without `conversionNativeLibraryDir`, host JNI integration tests are skipped; Kotlin packaging/routing tests still run. The fixtures contain original test content, generated using Calibre 8.15.0; regeneration does not run during normal builds. See `scripts/generate_conversion_fixtures.py`.

For the full Android import/preparation regression, install the debug APK on a disposable emulator, copy the four fixtures from `androidApp/src/test/resources/conversion` into the app’s `files/conversion-fixtures` directory with `adb run-as`, then run:

```sh
adb shell am broadcast -n com.foliore.android/.debug.DebugImportReceiver \
  -a com.foliore.android.DEBUG_IMPORT_EPUBS --ez conversionTest true
adb exec-out run-as com.foliore.android cat cache/conversion-result.txt
```

The harness uses isolated databases, preferences and book directories. It covers MOBI6, AZW3, hybrid MOBI, existing EPUB import, covers, immutable source bytes, exact duplicates, review/discard/commit cleanup, EPUB preparation, DRM and corrupt input. It does not contact sync or billing services.
