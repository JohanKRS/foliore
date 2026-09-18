#!/usr/bin/env python3
"""Generate original, tiny conversion fixtures. Usage: script /path/to/ebook-convert."""
import struct
import subprocess
import sys
import zlib
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parents[1] / "androidApp/src/test/resources/conversion"
root.mkdir(parents=True, exist_ok=True)


def chunk(kind, data):
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))


png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 2, 0, 0, 0))
png += chunk(b"IDAT", zlib.compress((b"\x00" + bytes([41, 99, 67]) * 16) * 16)) + chunk(b"IEND", b"")
with zipfile.ZipFile(root / "source.epub", "w") as epub:
    epub.writestr("mimetype", "application/epub+zip")
    epub.writestr("META-INF/container.xml", '<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="book.opf" media-type="application/oebps-package+xml"/></rootfiles></container>')
    epub.writestr("book.opf", '''<package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="uid"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="uid">foliore-conversion-fixture</dc:identifier><dc:title>Conversão Foliore</dc:title><dc:creator>Equipe Foliore</dc:creator><dc:language>pt</dc:language><meta name="cover" content="cover"/></metadata><manifest><item id="one" href="one.xhtml" media-type="application/xhtml+xml"/><item id="two" href="two.xhtml" media-type="application/xhtml+xml"/><item id="cover" href="cover.png" media-type="image/png"/><item id="toc" href="toc.ncx" media-type="application/x-dtbncx+xml"/></manifest><spine toc="toc"><itemref idref="one"/><itemref idref="two"/></spine></package>''')
    epub.writestr("one.xhtml", '<html xmlns="http://www.w3.org/1999/xhtml"><head><title>Primeiro capítulo</title></head><body><h1 id="first">Primeiro capítulo</h1><p>Olá, conversão: ação, coração &amp; café.</p><img src="cover.png" alt="Capa verde"/><a href="two.xhtml#second">Próximo capítulo</a></body></html>')
    epub.writestr("two.xhtml", '<html xmlns="http://www.w3.org/1999/xhtml"><head><title>Segundo capítulo</title></head><body><h1 id="second">Segundo capítulo</h1><p>Última página do teste Foliore.</p><a href="one.xhtml#first">Voltar ao início</a></body></html>')
    epub.writestr("toc.ncx", '<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1"><head><meta name="dtb:uid" content="foliore-conversion-fixture"/></head><docTitle><text>Conversão Foliore</text></docTitle><navMap><navPoint id="one" playOrder="1"><navLabel><text>Primeiro capítulo</text></navLabel><content src="one.xhtml#first"/></navPoint><navPoint id="two" playOrder="2"><navLabel><text>Segundo capítulo</text></navLabel><content src="two.xhtml#second"/></navPoint></navMap></ncx>')
    epub.writestr("cover.png", png)

for name, args in [("legacy.mobi", ["--mobi-file-type", "old"]), ("kindle.azw3", []), ("hybrid.mobi", ["--mobi-file-type", "both"])]:
    subprocess.run([sys.argv[1], str(root / "source.epub"), str(root / name), *args], check=True)
