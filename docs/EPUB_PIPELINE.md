# EPUB Pipeline

Initial import pipeline:

1. User picks one or more `.epub`, `.mobi` or `.azw3` files through Android Storage Access Framework (or shares them from another app).
2. Android app copies each file into internal storage.
3. The importer calculates SHA-256 while copying.
4. MOBI/AZW3 files are converted locally into a validated EPUB. The source is retained separately. Deterministic EPUB packaging allows repeated conversions to be checked for exact duplicates by the resulting EPUB hash.
5. The EPUB is opened as a ZIP archive.
6. `META-INF/container.xml` is read to locate the OPF package file.
7. Basic metadata and cover references are extracted from the OPF.
8. A cover thumbnail is generated when the cover image can be decoded by Android.
9. Database rows plus `metadata.json` and `technical.json` are written.

The original `original.epub` is never mutated.

For converted books, `original.epub` is the normalized EPUB used by the existing editing, export and sync pipeline. The unmodified input is additionally stored under `originals/<id>/`, together with its display name; EPUB inputs are also archived separately. The footer in book details exposes save/share/delete actions for these local archives without affecting the library copy. See [format conversion](FORMAT_CONVERSION.md).
