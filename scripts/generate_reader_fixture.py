#!/usr/bin/env python3
"""Create an original, redistributable EPUB for manual reader QA."""
import html
import sys
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED, ZIP_STORED

output = Path(sys.argv[1] if len(sys.argv) > 1 else "/tmp/foliore-reader.epub")
output.parent.mkdir(parents=True, exist_ok=True)
titles = ["O começo de uma história", "Um caminho entre palavras", "O próximo capítulo"]
paragraph = ("Ler é encontrar espaço para as ideias. A cada página, uma paisagem se revela: "
             "o vento passa pelas árvores, a luz desenha caminhos e as palavras nos convidam a continuar. "
             "Este texto foi criado para conferir a leitura, os acentos, a navegação e a retomada no Foliore.")
with ZipFile(output, "w", ZIP_DEFLATED) as archive:
    archive.writestr("mimetype", "application/epub+zip", compress_type=ZIP_STORED)
    archive.writestr("META-INF/container.xml", '<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0"><rootfiles><rootfile full-path="OPS/package.opf" media-type="application/oebps-package+xml"/></rootfiles></container>')
    items = ''.join(f'<item id="c{i}" href="chapter{i}.xhtml" media-type="application/xhtml+xml"/>' for i in range(1, 4))
    refs = ''.join(f'<itemref idref="c{i}"/>' for i in range(1, 4))
    archive.writestr("OPS/package.opf", f'<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="id">foliore-reader-qa-original</dc:identifier><dc:title>Entre páginas</dc:title><dc:creator>Equipe Foliore</dc:creator><dc:language>pt-BR</dc:language><meta property="dcterms:modified">2026-09-17T00:00:00Z</meta></metadata><manifest>{items}<item id="nav" href="nav.xhtml" properties="nav" media-type="application/xhtml+xml"/><item id="style" href="style.css" media-type="text/css"/><item id="art" href="art.svg" media-type="image/svg+xml"/><item id="notes" href="notes.xhtml" media-type="application/xhtml+xml"/></manifest><spine>{refs}</spine></package>')
    links = ''.join(f'<li><a href="chapter{i}.xhtml">{title}</a></li>' for i, title in enumerate(titles, 1))
    archive.writestr("OPS/nav.xhtml", f'<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><body><nav epub:type="toc"><ol>{links}</ol></nav></body></html>')
    archive.writestr("OPS/style.css", 'body{font-family:serif;line-height:1.6}h1{font-size:1.8em}p{margin:1em 0}img{width:100%}table{border-collapse:collapse}td{border:1px solid #92754f;padding:.5em}')
    archive.writestr("OPS/art.svg", '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 600 190"><rect width="600" height="190" fill="#e7ddc7"/><circle cx="460" cy="50" r="25" fill="#ba9055"/><path d="M0 145Q120 0 270 115T600 70V190H0Z" fill="#748366"/></svg>')
    archive.writestr("OPS/notes.xhtml", '<html xmlns="http://www.w3.org/1999/xhtml"><body><aside id="note">Esta nota continua disponível sem sair da leitura.</aside></body></html>')
    for i, title in enumerate(titles, 1):
        paragraphs = ''.join(f'<p id="p{j}">Trecho {i}.{j}. {html.escape(paragraph)}</p>' for j in range(1, 51))
        archive.writestr(f"OPS/chapter{i}.xhtml", f'<html xmlns="http://www.w3.org/1999/xhtml"><head><title>{title}</title><link rel="stylesheet" href="style.css"/></head><body><h1>{title}</h1><p><em>Uma pequena pausa. Um livro aberto. Um novo lugar para descobrir.</em></p><img src="art.svg" alt="Paisagem com colinas e sol"/><p>Uma <a href="notes.xhtml#note">nota de rodapé</a> e um <a href="chapter2.xhtml#p20">link para outro capítulo</a>.</p>{paragraphs}<table><tr><td>Primeira coluna</td><td>Segunda coluna</td></tr></table></body></html>')
print(output)
