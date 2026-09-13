"""Create local screenshot copies, preserving the source books and their notices."""
from pathlib import Path
from html import escape
import argparse, json, zipfile, posixpath, re
import xml.etree.ElementTree as ET
from bs4 import BeautifulSoup

ROOT=Path(__file__).resolve().parent
WORK=ROOT.parents[1]/'.codex-artifacts/play-store-2026-09-13'
BOOKS=json.loads((ROOT/'books.json').read_text())
NS={'opf':'http://www.idpf.org/2007/opf','dc':'http://purl.org/dc/elements/1.1/'}
ET.register_namespace('',NS['opf']); ET.register_namespace('dc',NS['dc'])

def build(book):
    key=book['id']; cover=ROOT/'covers'/f'{key}.png'
    if not cover.exists(): return None
    source=WORK/'sources'/key
    source_file=Path(book['source_file']) if book.get('source_file') else source/('original.epub' if book.get('pg') else 'original.html')
    target=WORK/'epubs'/f'{key}.epub';target.parent.mkdir(exist_ok=True)
    uid=f'urn:foliore:store-demo:{key}'
    if source_file.suffix.lower()=='.epub':
        with zipfile.ZipFile(source_file) as z:
            files={name:z.read(name) for name in z.namelist()}
        container=ET.fromstring(files['META-INF/container.xml'])
        opf_path=next(x.attrib['full-path'] for x in container.iter() if x.tag.endswith('rootfile'))
        opf=ET.fromstring(files[opf_path]);metadata=opf.find('opf:metadata',NS)
        for name,value in [('title',book['title']),('language',book['lang'])]:
            e=metadata.find(f'dc:{name}',NS)
            if e is not None:e.text=value
        # Use the familiar display name while retaining additional contributors,
        # original text, source identifiers and the embedded publication notices.
        creator=metadata.find('dc:creator',NS)
        if creator is not None:creator.text=book['author']
        # Preserve all author credits, publication details and embedded licenses.
        for child in list(metadata):
            if child.tag.endswith('meta') and child.attrib.get('name')=='cover':metadata.remove(child)
        ET.SubElement(metadata,'{'+NS['opf']+'}meta',{'name':'cover','content':'foliore-demo-cover'})
        manifest=opf.find('opf:manifest',NS)
        for item in manifest:
            props=item.attrib.get('properties','').split()
            if 'cover-image' in props:
                props.remove('cover-image')
                if props:item.set('properties',' '.join(props))
                else:item.attrib.pop('properties',None)
        ET.SubElement(manifest,'{'+NS['opf']+'}item',{'id':'foliore-demo-cover','href':'foliore-cover.png','media-type':'image/png','properties':'cover-image'})
        files[posixpath.join(posixpath.dirname(opf_path),'foliore-cover.png')]=cover.read_bytes()
        files[opf_path]=ET.tostring(opf,encoding='utf-8',xml_declaration=True)
    else:
        raw=source_file.read_bytes().decode(book.get('source_encoding','shift_jis'))
        soup=BeautifulSoup(raw,'html.parser')
        # Text conversion retains the full public-domain work and its source credits.
        paragraphs=soup.body.get_text('\n',strip=True).splitlines()
        body=''.join('<p>'+escape(t)+'</p>' for t in paragraphs if t.strip())
        files={'mimetype':b'application/epub+zip',
          'META-INF/container.xml':b'<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>',
          'OEBPS/chapter.xhtml':f'<?xml version="1.0" encoding="utf-8"?><html xmlns="http://www.w3.org/1999/xhtml" xml:lang="ja"><head><title>{escape(book["title"])}</title></head><body>{body}</body></html>'.encode(),
          'OEBPS/cover.png':cover.read_bytes()}
        files['OEBPS/content.opf']=f'''<?xml version="1.0" encoding="utf-8"?><package xmlns="http://www.idpf.org/2007/opf" unique-identifier="uid" version="3.0"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="uid">{uid}</dc:identifier><dc:title>{book['title']}</dc:title><dc:creator>{book['author']}</dc:creator><dc:language>ja</dc:language><dc:source>{book['source']}</dc:source><meta property="dcterms:modified">2026-09-13T00:00:00Z</meta><meta name="cover" content="cover"/></metadata><manifest><item id="cover" href="cover.png" media-type="image/png" properties="cover-image"/><item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/></manifest><spine><itemref idref="chapter"/></spine></package>'''.encode()
        files['OEBPS/nav.xhtml']=f'''<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>目次</title></head><body><nav epub:type="toc"><ol><li><a href="chapter.xhtml">{book['title']}</a></li></ol></nav></body></html>'''.encode()
    with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED) as z:
        z.writestr('mimetype',b'application/epub+zip',compress_type=zipfile.ZIP_STORED)
        for name,contents in files.items():
            if name!='mimetype':z.writestr(name,contents)
    return target

if __name__=='__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('--locale',action='append',choices=BOOKS['selections'])
    args=parser.parse_args()
    locales=args.locale or list(BOOKS['selections'])
    selected={key for locale in locales for key in BOOKS['selections'][locale]}
    built={b['id']:build(b) for b in BOOKS['books'] if b['id'] in selected}
    for locale in locales:
        ids=BOOKS['selections'][locale]
        folder=WORK/'libraries'/locale;folder.mkdir(parents=True,exist_ok=True)
        for previous in folder.glob('*.epub'):previous.unlink()
        for i,key in enumerate(ids):
            # The app's Recent sort shows the most recently imported book first.
            if built[key]:(folder/f'{len(ids)-i:02}-{key}.epub').write_bytes(built[key].read_bytes())
        print(locale, sum(built[key] is not None for key in ids), '/ 6')
