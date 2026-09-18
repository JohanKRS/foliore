# Conversion fixtures

Original two-chapter Portuguese test book and a generated solid-color PNG, created for Foliore tests. No third-party book content is included. The text, image and generated ebook fixtures in this directory may be used, modified and redistributed without restriction.

`source.epub` was converted with Calibre 8.15.0 to `legacy.mobi` (MOBI6), `kindle.azw3` (KF8), and `hybrid.mobi` (MOBI6/KF8). Recreate using:

```sh
python3 scripts/generate_conversion_fixtures.py /path/to/ebook-convert
```

Native tests additionally derive DRM-flagged and truncated inputs in temporary directories. No actual encrypted commercial book is used.
