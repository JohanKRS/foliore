#!/usr/bin/env python3
"""Compile the Android vector artwork into distributable icon assets.

Requires rsvg-convert (librsvg) and Pillow for PNG encoding. This uses the native VectorDrawable as the
single source for the F and sparkle; no background removal or raster tracing.
"""

import base64
from copy import deepcopy
from pathlib import Path
import shutil
import subprocess
import xml.etree.ElementTree as ET
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "androidApp/src/main/res"
ART = ROOT / "artwork/app-icon"
OUT = ART / "exports"
ANDROID = "{http://schemas.android.com/apk/res/android}"
AAPT = "{http://schemas.android.com/aapt}"
DECORATIVE_EDGE = "base_contrast_edge"


def svg_color(value):
    """Android uses #AARRGGBB; SVG separates RGB from opacity."""
    if len(value) == 9 and value.startswith("#"):
        return "#" + value[3:], int(value[1:3], 16) / 255
    if len(value) == 7 and value.startswith("#"):
        return value, 1.0
    raise ValueError(f"Expected an explicit RGB/ARGB color, got {value}")


def svg(body, viewbox="0 0 108 108", width=108, height=108, defs=""):
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" '
        f'width="{width}" height="{height}" viewBox="{viewbox}">'
        f"<defs>{defs}</defs>{body}</svg>"
    )


def embedded_png(path):
    return "data:image/png;base64," + base64.b64encode(path.read_bytes()).decode("ascii")


def rasterize(source, destination, size):
    subprocess.run(
        ["rsvg-convert", "--width", str(size), "--output", str(destination)],
        input=source.encode(), check=True,
    )


def main():
    if not shutil.which("rsvg-convert"):
        raise SystemExit("Install librsvg (rsvg-convert) before exporting launcher icons.")
    OUT.mkdir(parents=True, exist_ok=True)
    vector = ET.parse(RES / "drawable/ic_launcher_foreground.xml").getroot()
    def mark(color=None):
        gradients = []

        def paint(element, kind):
            opacity = float(element.get(ANDROID + kind + "Alpha", 1))
            gradient = element.find(f"{AAPT}attr[@name='android:{kind}Color']/gradient")
            if gradient is None:
                rgb, alpha = svg_color(element.get(ANDROID + kind + "Color", "#00000000"))
                return f'{kind}="{rgb}" {kind}-opacity="{alpha * opacity:g}"'
            if gradient.get(ANDROID + "type") != "linear":
                raise ValueError("Expected a native linear gradient")
            name = f"mark-gradient-{len(gradients)}"
            coordinates = " ".join(
                f'{svg_attr}="{gradient.attrib[ANDROID + android_attr]}"'
                for svg_attr, android_attr in [("x1", "startX"), ("y1", "startY"), ("x2", "endX"), ("y2", "endY")]
            )
            stops = []
            for item in gradient.findall("item"):
                rgb, alpha = svg_color(item.attrib[ANDROID + "color"])
                stops.append(f'<stop offset="{item.attrib[ANDROID + "offset"]}" stop-color="{rgb}" stop-opacity="{alpha:g}"/>')
            gradients.append(f'<linearGradient id="{name}" gradientUnits="userSpaceOnUse" {coordinates}>{"".join(stops)}</linearGradient>')
            return f'{kind}="url(#{name})" {kind}-opacity="{opacity:g}"'

        def render(element):
            if element.tag == "path":
                if color and element.get(ANDROID + "name") == DECORATIVE_EDGE:
                    return ""
                paints = f'fill="{color}"' if color else paint(element, "fill")
                if not color and ANDROID + "strokeWidth" in element.attrib:
                    paints += " " + paint(element, "stroke")
                    paints += f' stroke-width="{element.attrib[ANDROID + "strokeWidth"]}"'
                    paints += f' stroke-linejoin="{element.get(ANDROID + "strokeLineJoin", "miter")}"'
                return f'<path {paints} d="{element.attrib[ANDROID + "pathData"]}"/>'
            children = "".join(render(child) for child in element)
            if element.tag == "vector":
                return children
            if element.tag != "group":
                raise ValueError(f"Unsupported vector element: {element.tag}")
            def attr(name, default=0):
                return float(element.get(ANDROID + name, default))
            px, py = attr("pivotX"), attr("pivotY")
            transform = (
                f'translate({px+attr("translateX")} {py+attr("translateY")}) '
                f'rotate({attr("rotation")}) scale({attr("scaleX", 1)} {attr("scaleY", 1)}) '
                f'translate({-px} {-py})'
            )
            return f'<g transform="{transform}">{children}</g>'

        body = render(vector)
        return ("<defs>" + "".join(gradients) + "</defs>" if gradients else "") + body

    # Themed icons use the approved core F/star contours and transforms.
    # Their system-selected flat background needs no decorative contrast edge.
    mono_vector = deepcopy(vector)
    for parent in mono_vector.iter():
        for child in list(parent):
            if child.get(ANDROID + "name") == DECORATIVE_EDGE:
                parent.remove(child)
    for path in mono_vector.iter("path"):
        path.set(ANDROID + "fillColor", "#FFFFFF")
        for child in list(path):
            path.remove(child)
    ET.register_namespace("android", ANDROID[1:-1])
    ET.indent(mono_vector, space="    ")
    monochrome = (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<!-- Generated by scripts/export_launcher_icons.py from the foreground vector. -->\n'
        + ET.tostring(mono_vector, encoding="unicode") + "\n"
    )
    (RES / "drawable/ic_launcher_monochrome.xml").write_text(monochrome)
    for name, color in [("foreground", None), ("monochrome", "#FFFFFF")]:
        source = svg(mark(color))
        (OUT / f"{name}.svg").write_text(source + "\n")
        rasterize(source, OUT / f"{name}.png", 1080)

    source_background = f'<image width="108" height="108" xlink:href="{embedded_png(ART / "source/landscape.png")}"/>'
    # 4 px/dp covers xxxhdpi; nodpi avoids density scaling before adaptive bounds.
    rasterize(svg(source_background), RES / "drawable-nodpi/foliore_landscape.png", 432)
    background = f'<image width="108" height="108" xlink:href="{embedded_png(RES / "drawable-nodpi/foliore_landscape.png")}"/>'
    composition = background + mark()
    # Adaptive layers extend 18 dp past the visible 72 dp viewport on every side.
    visible = "18 18 72 72"
    rasterize(svg(composition, visible), OUT / "play-store-512.png", 512)
    # Play requests a 32-bit PNG. Preserve RGB pixels and add an opaque alpha
    # channel; librsvg otherwise optimizes a fully opaque image to 24-bit RGB.
    with Image.open(OUT / "play-store-512.png") as store_icon:
        store_icon.convert("RGBA").save(OUT / "play-store-512.png")
    (OUT / "composite.svg").write_text(svg(composition, visible) + "\n")
    for density, size in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
        folder = RES / f"mipmap-{density}"
        # Only these legacy fallback bitmaps bake in a mask. Adaptive layers
        # and the Play listing export always keep their full square canvas.
        rasterize(
            svg('<g clip-path="url(#legacy)">' + composition + '</g>', visible,
                defs='<clipPath id="legacy"><rect x="18" y="18" width="72" height="72" rx="16"/></clipPath>'),
            folder / "ic_launcher.png", size,
        )
        rasterize(
            svg('<g clip-path="url(#round)">' + composition + '</g>', visible,
                defs='<clipPath id="round"><circle cx="54" cy="54" r="36"/></clipPath>'),
            folder / "ic_launcher_round.png", size,
        )

    masks = {
        "circle": '<circle cx="54" cy="54" r="36"/>',
        "rounded": '<rect x="18" y="18" width="72" height="72" rx="16"/>',
        "squircle": '<path d="M54,18 C82,18 90,26 90,54 C90,82 82,90 54,90 C26,90 18,82 18,54 C18,26 26,18 54,18Z"/>',
        "teardrop": '<path d="M54,18 C74,18 90,34 90,54 V90 H54 C34,90 18,74 18,54 C18,34 34,18 54,18Z"/>',
    }
    defs = ''.join(f'<clipPath id="{name}">{shape}</clipPath>' for name, shape in masks.items())
    previous = f'<image width="108" height="108" xlink:href="{embedded_png(ART / "source/previous-flattened.png")}"/>'
    panels = [
        ("Anterior", "circle", previous),
        ("Novo · circular", "circle", composition),
        ("Quadrado arredondado", "rounded", composition),
        ("Superelipse", "squircle", composition),
        ("Recorte assimétrico", "teardrop", composition),
        ("Temático · claro", "circle", '<path fill="#DCE8CC" d="M0,0H108V108H0Z"/>' + mark("#263C18")),
        ("Temático · escuro", "circle", '<path fill="#263C18" d="M0,0H108V108H0Z"/>' + mark("#DCE8CC")),
    ]
    review = '<rect width="1120" height="800" fill="#F6F2E9"/>'
    review += '<g fill="#303C2B" font-family="sans-serif"><text x="44" y="58" font-size="29">Foliore · ícone adaptivo</text>'
    review += '<text x="44" y="90" font-size="16" fill="#64695D">Verde profundo com filete claro na base. Proporções, estrela e paisagem preservadas.</text></g>'
    for i, (label, mask, content) in enumerate(panels):
        row, col = divmod(i, 4)
        x, y = 60 + col * 270, 136 + row * 280
        content = content.replace("mark-gradient-", f"panel-{i}-gradient-")
        review += f'<svg x="{x}" y="{y}" width="180" height="180" viewBox="18 18 72 72"><g clip-path="url(#{mask})">{content}</g></svg>'
        review += f'<text x="{x+90}" y="{y+215}" fill="#424A3A" text-anchor="middle" font-family="sans-serif" font-size="16">{label}</text>'
    # A split swatch demonstrates genuine alpha without a misleading checkerboard.
    review += '<svg x="870" y="416" width="180" height="180" viewBox="18 18 72 72">'
    review += '<rect x="18" y="18" width="36" height="72" fill="#FDFBF5"/><rect x="54" y="18" width="36" height="72" fill="#B7C6D0"/>' + mark() + '</svg>'
    review += '<text x="960" y="631" fill="#424A3A" text-anchor="middle" font-family="sans-serif" font-size="16">Frente sem fundo</text>'
    review += '<g font-family="sans-serif" fill="#686C62" font-size="15"><text x="44" y="729">As máscaras e as cores dos ícones temáticos são escolhidas pelo launcher.</text>'
    review += '<text x="44" y="756">Marca: 48,6 dp de altura no quadro de 108 dp. Zona segura: círculo central de 66 dp.</text></g>'
    preview = svg(review, "0 0 1120 800", 1120, 800, defs)
    (OUT / "preview.svg").write_text(preview + "\n")
    rasterize(preview, OUT / "preview.png", 1120)
    print(f"Exported Android resources and review assets to {OUT}")


if __name__ == "__main__":
    main()
