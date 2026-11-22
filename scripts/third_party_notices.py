#!/usr/bin/env python3
"""Regenerate offline notices from reviewed runtime artifacts; see third-party/README.md."""
import gzip
import hashlib
import io
import json
from pathlib import Path
import re
import struct
import sys
import zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "androidApp/src/main/assets"
INVENTORY = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "androidApp/build/third-party/artifacts.tsv"
PROJECTS = json.loads((ROOT / "third-party/projects.json").read_text())
SOURCES = json.loads((ROOT / "third-party/supplemental-sources.json").read_text())


def notice(title, data, source, path=None):
    data.decode("utf-8")  # Fail rather than ship unreadable legal text.
    digest = hashlib.sha256(data).hexdigest()
    path = path or f"licenses/texts/{digest}.txt"
    (ASSETS / path).write_bytes(data)
    return dict(title=title, path=path, sha256=digest, source=source)


def supplemental(title, path):
    return notice(title, (ASSETS / path).read_bytes(), SOURCES[path], path)


apache = supplemental("Apache License 2.0", "licenses/texts/apache-2.0.txt")
groups = {p["id"]: {k: p[k] for k in ("id", "name", "url", "license")} | {
    "coordinates": [], "notices": [] if p["id"] in ("google-play", "jsoup") else [apache],
} for p in PROJECTS}


def add(group, entry):
    # Preserve distinct texts, including different notices for the same bundled project.
    if not any(n["title"] == entry["title"] and n["sha256"] == entry["sha256"] for n in group["notices"]):
        group["notices"].append(entry)


for line in sorted(INVENTORY.read_text().splitlines()):
    coordinate, artifact = line.split("\t")
    matches = [p for p in PROJECTS if coordinate.startswith(p["prefix"])]
    if len(matches) != 1:
        raise ValueError(f"Review the license and add an explicit project entry for {coordinate}")
    group = groups[matches[0]["id"]]
    poms = list(Path(artifact).parent.parent.glob("*/*.pom"))
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    license_names = [element.text or "" for pom in poms
        for element in (ET.parse(pom).getroot().findall("m:licenses/m:license/m:name", ns) or ET.parse(pom).getroot().findall("licenses/license/name"))]
    if not license_names:
        # This POM inherits Apache-2.0 from its Guava parent; reviewed explicitly.
        assert coordinate == "com.google.guava:listenablefuture:1.0", f"Missing POM license: {coordinate}"
    elif group["id"] == "google-play":
        assert all("Android Software Development Kit License" == n for n in license_names), (coordinate, license_names)
    elif group["id"] == "jsoup":
        assert all("MIT" in n for n in license_names), license_names
    else:
        assert all("Apache" in n for n in license_names), f"Review changed license: {coordinate}: {license_names}"
    if coordinate not in group["coordinates"]:
        group["coordinates"].append(coordinate)
    with zipfile.ZipFile(artifact) as archive:
        if "third_party_licenses.json" in archive.namelist():
            ranges = json.loads(archive.read("third_party_licenses.json"))
            data = archive.read("third_party_licenses.txt")
            for title, span in sorted(ranges.items()):
                begin, length = span["start"], span["length"]
                assert begin >= 0 and length > 0 and begin + length <= len(data)
                add(group, notice(title, data[begin:begin + length], f"{coordinate}!third_party_licenses.txt"))

        def extract(archive, prefix=""):
            for name in sorted(archive.namelist()):
                if re.search(r"(?i)(^|/)(notice|license|licence|copying|copyright)(\.[^/]*)?$", name) and not name.endswith(".class"):
                    data = archive.read(name)
                    # AndroidX supplies this exact Apache text; retain it as shipped.
                    title = "Apache License 2.0 (AndroidX)" if group["id"] == "androidx" else name.rsplit("/", 1)[-1]
                    add(group, notice(title, data, f"{coordinate}!{prefix}{name}"))
                if name == "classes.jar":
                    with zipfile.ZipFile(io.BytesIO(archive.read(name))) as classes:
                        extract(classes, "classes.jar!")
        extract(archive)
        if group["id"] == "okhttp":
            add(group, supplemental("Mozilla Public License 2.0", "licenses/texts/mpl-2.0.txt"))
            # Distribute the human-readable rule data corresponding to the exact binary.
            source = gzip.decompress(archive.read("okhttp3/internal/publicsuffix/publicsuffixes.gz"))
            size = struct.unpack(">I", source[:4])[0]
            rules = source[4:4 + size]
            start = 4 + size
            exceptions_size = struct.unpack(">I", source[start:start + 4])[0]
            exceptions = source[start + 4:]
            assert len(exceptions) == exceptions_size
            data = (f"// Public Suffix List rule data distributed with {coordinate}.\n".encode() +
                    b"// Mozilla Public License 2.0: https://mozilla.org/MPL/2.0/\n"
                    b"// https://publicsuffix.org/list/\n\n" + rules +
                    b"".join(b"!" + rule + b"\n" for rule in exceptions.splitlines()))
            add(group, notice("Public Suffix List", data, f"{coordinate}!okhttp3/internal/publicsuffix/publicsuffixes.gz"))

font = supplemental("SIL Open Font License 1.1", "licenses/cormorant_garamond/OFL.txt")
groups["cormorant"] = dict(id="cormorant", name="Cormorant Garamond", url="https://github.com/CatharsisFonts/Cormorant",
    license="SIL Open Font License 1.1", coordinates=[], notices=[font])
groups["libmobi"] = dict(id="libmobi", name="libmobi", url="https://github.com/bfabiszewski/libmobi",
    license="LGPL-3.0-or-later", coordinates=[], notices=[
        notice("GNU Lesser General Public License v3", (ROOT / "third-party/libmobi/COPYING").read_bytes(), "libmobi 906274205c11944b628da1c553b255acb1af7c55/COPYING"),
        notice("GNU General Public License v3", (ROOT / "third-party/libmobi/COPYING.GPL").read_bytes(), "https://www.gnu.org/licenses/gpl-3.0.txt"),
        notice("Source code and library replacement", (ROOT / "third-party/libmobi/INTEGRATION.txt").read_bytes(), "Foliore native conversion integration"),
    ])
for group in groups.values():
    group["coordinates"].sort()
    group["notices"].sort(key=lambda n: (n["title"].casefold(), n["sha256"]))
catalog = {"groups": sorted(groups.values(), key=lambda g: g["name"].casefold())}
(ASSETS / "licenses/catalog.json").write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n")
referenced = {n["path"] for g in groups.values() for n in g["notices"]}
for generated in (ASSETS / "licenses/texts").glob("*.txt"):
    if re.fullmatch(r"[0-9a-f]{64}", generated.stem) and generated.relative_to(ASSETS).as_posix() not in referenced:
        generated.unlink()
print(f"Documented {sum(len(g['coordinates']) for g in groups.values())} runtime components and the bundled font.")
