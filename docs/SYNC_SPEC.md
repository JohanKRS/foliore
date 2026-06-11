# Sync Spec

Sync is planned but intentionally not implemented in this first slice.

The future portable library folder will contain a `library.manifest.json`, device descriptors, per-book folders, and snapshots. The local SQLite database remains a rebuildable cache.

The MVP conflict model will compare revisions and mark divergent edits as conflicts instead of silently overwriting user changes.

