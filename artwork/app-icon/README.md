# Foliore — adaptive launcher icon

The adaptive icon has two independent layers, with the existing F and gold
sparkle redrawn as native vector paths over the parchment mountain landscape.
The vector contains no background rectangle, raster texture, checkerboard,
external shadow or baked launcher mask. A narrow vector stroke increases
contrast along the lower F without changing the approved core proportions.

## Source and exports

- Canonical mark: `androidApp/src/main/res/drawable/ic_launcher_foreground.xml`.
- Landscape master: `source/landscape.png` (opaque, full canvas).
- Previous flattened icon, retained for visual comparison: `source/previous-flattened.png`.
- `exports/foreground.svg`: transparent F and sparkle, editable paths, no embedded bitmap or font dependency.
- `exports/foreground.png`: the same foreground, true RGBA transparency, 1080 × 1080.
- `exports/monochrome.svg` / `.png`: the same silhouette for themed icons.
- `exports/composite.svg`: complete icon preview; the landscape is an embedded raster, the mark remains vector.
- `exports/play-store-512.png`: 512 × 512, 32-bit RGBA, opaque square, under 1 MB; no rounded corners or external shadow.
- `exports/preview.png` / `.svg`: comparison, mask examples and illustrative themed colors.

Regenerate resources and exports from the canonical vector with:

```sh
python3 scripts/export_launcher_icons.py
```

Dependencies: Python 3, Pillow (PNG encoding only), and `rsvg-convert` from librsvg.
The exporter also generates the Android monochrome vector from the same core
paths and transforms, omitting only the decorative lower contrast stroke.

## Android sizing

Both layers use a 108 × 108 dp canvas. The F and sparkle together occupy
33.84 × 48.6 dp, centered within the central 66 dp safe circle. This is the
selected third proposal: 10% smaller than the first vector, taking 67.5% of the
nominal visible 72 dp icon height, with more mountains in the visible background.
The final finish is the selected contrast option 3: a uniform deep green
(`#1C3129`) F with a fine parchment-colored lower edge (`#F4E8CA`). A 0.85 dp
stroke behind the fill fades in from y=62 to y=75 at up to 88% opacity,
leaving only its outer half visible (0.3825 dp after the group scale). The
upper F remains uniformly dark over the light sky. Its middle arm extends
1.8 dp farther and the star moves 2 dp left before the 0.9 group scale, leaving
a gap of about 0.85 dp. The monochrome layer preserves these exact core contours;
the system-selected flat background does not need the decorative lower edge.
The outer 18 dp of the artwork stays available for launcher
masks and motion. No extra inset is applied by the adaptive XML.

The landscape is packaged at 432 × 432 in `drawable-nodpi`, scaled to the
adaptive layer bounds. Normal and round launcher resources both point to the
same adaptive layers and explicit monochrome drawable. Legacy bitmap fallbacks
are also regenerated, with their own rounded/circular silhouettes.

Launcher mask and themed colors are controlled by Android/the launcher, not by
the app's light or dark UI theme. The preview's themed colors are examples.

## Artwork provenance

The landscape was edited using the **built-in image generation tool**, with the
previous launcher artwork as the reference. No CLI/API fallback was used.

The initial landscape edit removed the flattened F and sparkle. The selected
revision brings more layered beige and olive mountains and woodland into the
central crop while retaining the parchment sky. It remains opaque and
edge-to-edge, with no text, symbols, frame, rounded corners or external shadow.
The final generation prompt is retained in `source/landscape-prompt.txt`.

The attempted foreground raster generations contained a baked checkerboard and
were rejected. The shipped foreground is the manually reconstructed native
vector, as approved by the user; its PNG is a deterministic export with actual
alpha, not a generated transparency illustration.

## Verification — 2026-09-12

- Release compilation, Android resource linking and lint succeeded (no lint errors).
- On an isolated Android 16 / API 36 emulator, the installed release resolves
  normal and round resources to `AdaptiveIconDrawable`, with a non-null monochrome layer.
- The final 512 px export is pixel-identical to the selected option 3 preview.
- The Android renderer confirms real foreground transparency, a uniform deep
  green F and a lower-only contrast edge. Maximum occupied radius including
  that stroke is 29.947 dp (limit 33 dp).
- Native rendering verified four masks and nine motion positions per mask
  (±3 dp in each axis), with no clipped foreground pixels. The generated
  monochrome paths and group transforms are unchanged from the approved mark.
- Foreground export: RGBA, alpha range 0–255, ink bounds [366, 297, 710, 787]
  in a 1080 × 1080 canvas; 95.92% of canvas pixels are transparent.
- Real Pixel Launcher screenshot and native rendered images are in
  `.codex-artifacts/adaptive-icon-2026-09-12/option3/` (local evidence, not shipped).
- Physical phone/tablet checks remain for when ADB is available.

References: [Android adaptive icon guidance](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive?hl=en)
and [Google Play listing icon specification](https://developer.android.com/distribute/google-play/resources/icon-design-specifications).
