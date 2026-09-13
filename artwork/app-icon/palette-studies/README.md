# Launcher palettes

The Settings icon picker uses these four approved masters:

- **Dawn**: soft yellow and plum (revised version selected by the user).
- **Day**: sage and emerald (default).
- **Dusk**: soft peach and lavender, dark violet F (third version, choice delegated by the user).
- **Night**: blue and silver.

Each directory preserves the full background, transparent foreground SVG, composite SVG and visual proof. `selection.json` records palette colors and provenance.

Run `python3 scripts/export_launcher_palettes.py` to generate Android resources. The F/star contours come from the canonical native foreground vector; the exporter verifies them against the approved SVGs. Adaptive layers keep the full 108 dp canvas, including overscan. Settings previews alone have a baked rounded mask. All palettes share the same monochrome silhouette for Android themed icons.

Selection uses launcher activity aliases and is persisted by Android for this device/user; it is independent of the app theme and library synchronization. Day is the manifest default. Keep alias names stable in future updates so an existing choice remains launchable.

A no-display launcher activity forwards to the stable MainActivity task. Its empty task affinity keeps alias changes from finishing the visible Settings screen. EPUB sharing continues to target MainActivity directly.
