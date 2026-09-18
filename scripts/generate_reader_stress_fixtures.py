#!/usr/bin/env python3
"""Original EPUB fixtures for layout QA. Generated books stay outside the repository."""
import hashlib
import html
import sys
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED, ZIP_STORED

out = Path(sys.argv[1])
out.mkdir(parents=True, exist_ok=True)
svg = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 600 900"><rect width="600" height="900" fill="#e2d0a8"/><rect x="20" y="20" width="560" height="860" fill="none" stroke="#174638" stroke-width="12"/><circle cx="300" cy="300" r="120" fill="#996532"/><text x="300" y="650" text-anchor="middle" font-size="42">FOLIORE QA</text><text x="300" y="780" text-anchor="middle" font-size="24">Imagem inteira visível</text></svg>'


def create(name, title, bodies, css='', fixed=False, rtl=False, font=None):
    uid = 'foliore-qa-' + name
    with ZipFile(out / (name + '.epub'), 'w', ZIP_DEFLATED) as z:
        z.writestr('mimetype', 'application/epub+zip', compress_type=ZIP_STORED)
        z.writestr('META-INF/container.xml', '<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0"><rootfiles><rootfile full-path="OPS/package.opf" media-type="application/oebps-package+xml"/></rootfiles></container>')
        items = ''.join(f'<item id="c{i}" href="chapter{i}.xhtml" media-type="application/xhtml+xml"/>' for i in range(len(bodies)))
        refs = ''.join(f'<itemref idref="c{i}"/>' for i in range(len(bodies)))
        font_item = '<item id="font" href="qa-font.ttf" media-type="font/ttf"/>' if font else ''
        z.writestr('OPS/package.opf', f'<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="uid">{uid}</dc:identifier><dc:title>{title}</dc:title><dc:creator>Equipe Foliore</dc:creator><dc:language>{"ar" if rtl else "pt-BR"}</dc:language><meta property="dcterms:modified">2026-09-18T00:00:00Z</meta>{"<meta property=\"rendition:layout\">pre-paginated</meta>" if fixed else ""}</metadata><manifest>{items}<item id="nav" href="nav.xhtml" properties="nav" media-type="application/xhtml+xml"/><item id="style" href="style.css" media-type="text/css"/><item id="art" href="art.svg" media-type="image/svg+xml"/>{font_item}</manifest><spine page-progression-direction="{"rtl" if rtl else "ltr"}">{refs}</spine></package>')
        links = ''.join(f'<li><a href="chapter{i}.xhtml">Capítulo {i+1}</a></li>' for i in range(len(bodies)))
        z.writestr('OPS/nav.xhtml', f'<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>Contents</title></head><body><nav epub:type="toc"><ol>{links}</ol></nav></body></html>')
        z.writestr('OPS/art.svg', svg)
        z.writestr('OPS/style.css', css)
        for i, body in enumerate(bodies):
            viewport = '<meta name="viewport" content="width=900,height=1200"/>' if fixed else ''
            z.writestr(f'OPS/chapter{i}.xhtml', f'<html xmlns="http://www.w3.org/1999/xhtml" dir="{"rtl" if rtl else "ltr"}"><head><title>{title}</title>{viewport}<link rel="stylesheet" href="style.css"/></head><body>{body}</body></html>')
        if font:
            data = bytearray(Path(font).read_bytes())
            key = hashlib.sha1(uid.encode()).digest()
            for i in range(min(1040, len(data))): data[i] ^= key[i % len(key)]
            z.writestr('OPS/qa-font.ttf', data)
            z.writestr('META-INF/encryption.xml', '<encryption xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><EncryptedData xmlns="http://www.w3.org/2001/04/xmlenc#"><EncryptionMethod Algorithm="http://www.idpf.org/2008/embedding"/><CipherData><CipherReference URI="OPS/qa-font.ttf"/></CipherData></EncryptedData></encryption>')
    print(out / (name + '.epub'))


paragraph = 'Leitura com acentos: ação, coração, café. Cada passagem deve permanecer visível ao mudar fonte, margem e orientação. '
flow = ''.join(f'<p id="p{i}">Trecho {i}. {paragraph * 3}</p>' for i in range(70))
rows = ''.join('<tr>' + ''.join(f'<td>Linha {i}, coluna {j}: conteúdo completo.</td>' for j in range(8)) + '</tr>' for i in range(40))
create('qa-wide-layout', 'QA: imagens e tabelas', [
    '<h1>Imagens sem cortes</h1><div style="width:980px!important"><img src="art.svg" style="width:980px!important"/><p>Legenda da imagem. INÍCIO e FIM devem estar acessíveis.</p></div>' + flow,
    '<h1>Tabela larga e longa</h1><table><caption>40 linhas e 8 colunas</caption>' + rows + '</table><p>FIM DA TABELA</p>' + flow,
], 'table{width:1800px;border-collapse:collapse}td{border:1px solid #888;padding:12px}body{color:#111;background:white}p{margin:1em 0}')
create('qa-fixed-layout', 'QA: layout fixo', [
    '<h1 style="position:absolute;left:50px;top:30px">Página fixa 1</h1><img src="art.svg" style="position:absolute;left:60px;top:180px;width:400px;height:600px"/><p style="position:absolute;left:500px;top:300px;width:320px">Texto à direita. A imagem mantém 400 × 600.</p>',
    '<h1 style="position:absolute;left:50px;top:30px">Página fixa 2</h1><p style="position:absolute;left:60px;top:1000px">Rodapé acessível com zoom e rolagem.</p>',
], 'body{width:900px;height:1200px;margin:0;font-size:32px;background:#f7f2e8;color:#302b24}', fixed=True)
create('qa-rtl', 'QA: leitura RTL', ['<h1>اختبار القراءة</h1>' + ''.join('<p>' + f'{i}. هذه صفحة للاختبار. نقرأ الكلمات من اليمين إلى اليسار. ' * 4 + '</p>' for i in range(80)), '<h1>الفصل الثاني</h1><p>نهاية الاختبار.</p>'], rtl=True)
create('qa-long-chapter', 'QA: capítulo longo', ['<h1>Capítulo longo</h1>' + ''.join(f'<p id="p{i}">Trecho {i}. {paragraph * 8}</p>' for i in range(1000))])
font_path = sys.argv[2] if len(sys.argv) > 2 else None
if font_path:
    create('qa-obfuscated-font', 'QA: fonte incorporada', ['<h1>Fonte incorporada</h1><p>Árvore, coração, café. <ruby>読書<rt>どくしょ</rt></ruby> e 中文.</p>' + flow], '@font-face{font-family:QAEmbedded;src:url(qa-font.ttf)}body{font-family:QAEmbedded,serif}', font=font_path)
