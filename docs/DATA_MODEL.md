# Data Model

The local database starts with three tables:

- `book`: file locations, hashes, timestamps, lifecycle status, sync status.
- `book_metadata`: editable metadata extracted from EPUB or later edited manually.
- `epub_technical_info`: OPF path, nav/NCX/covers, package structure counts, parser warnings/errors.

Portable JSON files mirror metadata and technical state under each book folder so future sync can rebuild the local database without treating SQLite as the source of truth.

