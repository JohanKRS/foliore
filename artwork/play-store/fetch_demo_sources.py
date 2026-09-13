"""Download public-domain source editions for isolated screenshot fixtures only."""
from pathlib import Path
import json, re, time, urllib.request
from urllib.parse import urljoin
from html.parser import HTMLParser

ROOT = Path(__file__).resolve().parent
DEST = ROOT.parents[1] / '.codex-artifacts/play-store-2026-09-13/sources'
DEST.mkdir(parents=True, exist_ok=True)

class Links(HTMLParser):
    def __init__(self):
        super().__init__(); self.links = []
    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if tag == 'a' and a.get('href'): self.links.append(a['href'])

def get(url):
    request = urllib.request.Request(url, headers={'User-Agent': 'Foliore-Store-Demo/1.0 (public-domain source preparation)'})
    with urllib.request.urlopen(request, timeout=45) as response:
        return response.read()

results = []
for book in json.loads((ROOT/'books.json').read_text())['books']:
    key = book['id']; folder = DEST/key; folder.mkdir(exist_ok=True)
    page = folder/'source-page.html'
    if not page.exists(): page.write_bytes(get(book['source'])); time.sleep(.4)
    raw = page.read_bytes()
    encoding = 'utf-8' if book.get('pg') else 'shift_jis'
    text = raw.decode(encoding, errors='replace')
    links = Links(); links.feed(text)
    if book.get('pg'):
        href = next(h for h in links.links if h.endswith('.epub3.images'))
        target = folder/'original.epub'
    else:
        href = next(h for h in links.links if '/files/' in h and h.endswith('.html'))
        target = folder/'original.html'
    url = urljoin(book['source'], href)
    if not target.exists(): target.write_bytes(get(url)); time.sleep(.4)
    entry = dict(book, download_url=url, file=str(target), bytes=target.stat().st_size)
    results.append(entry)
    (DEST/'manifest.json').write_text(json.dumps(results,ensure_ascii=False,indent=2))
    print(key, target.stat().st_size, flush=True)
