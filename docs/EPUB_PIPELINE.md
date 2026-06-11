# EPUB Pipeline

Initial import pipeline:

1. User picks one or more `.epub` files through Android Storage Access Framework.
2. Android app copies each file into internal storage.
3. The importer calculates SHA-256 while copying.
4. The library checks exact duplicates by hash.
5. The EPUB is opened as a ZIP archive.
6. `META-INF/container.xml` is read to locate the OPF package file.
7. Basic metadata and cover references are extracted from the OPF.
8. A cover thumbnail is generated when the cover image can be decoded by Android.
9. Database rows plus `metadata.json` and `technical.json` are written.

The original `original.epub` is never mutated.

