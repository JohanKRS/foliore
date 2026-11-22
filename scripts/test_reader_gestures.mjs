// Exercise the shipped runtime's event handlers without Android or third-party dependencies.
// Emulator QA additionally checks WebView's real touch events and Compose chrome changes.
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { runInNewContext } from 'node:vm';

const runtime = ['image-tones.js', 'runtime.js'].map(name => readFileSync(new URL(`../androidApp/src/main/assets/reader/${name}`, import.meta.url), 'utf8')).join('\n');
function reader({ mode = 'pages', fixed = false, rtl = false, visible, overflowWidth = 400, overflowHeight = 800, images = [] } = {}) {
  const listeners = {}, windowListeners = {}, messages = [], selection = { isCollapsed: true }, elements = [], timers = new Map();
  let now = 10000, timerId = 0;
  const flush = () => { while (timers.size) { const pending = [...timers.values()]; timers.clear(); pending.forEach(callback => callback()); } };
  const stylesheet = {};
  const element = () => ({ childNodes: [], clientWidth: 400, clientHeight: 800, style: { setProperty(name, value) { this[name] = value; }, removeProperty(name) { delete this[name]; } }, scrollWidth: 1160, scrollHeight: 3000, scrollTop: 0, scrollLeft: 0,
    append() {}, prepend() {}, querySelectorAll: selector => selector === 'img,svg image' ? images : [], scrollTo({ left, top }) { this.scrollLeft = left; this.scrollTop = top; } });
  const body = { ...element(), dataset: { folioreFixed: String(fixed) } };
  const plain = { closest: () => null };
  const context = {
    innerWidth: overflowWidth, innerHeight: overflowHeight, visualViewport: visible,
    document: { body, documentElement: element(), scrollingElement: element(), createElement: () => { const el = element(); elements.push(el); return el; },
      getElementById: id => id === 'foliore-style' ? stylesheet : null,
      createTreeWalker: () => ({ nextNode: () => null }),
      addEventListener: (type, callback) => { listeners[type] = callback; } },
    getComputedStyle: () => ({ direction: rtl ? 'rtl' : 'ltr' }), getSelection: () => selection,
    Date: { now: () => now }, NodeFilter: { SHOW_TEXT: 4 },
    setTimeout: callback => { timers.set(++timerId, callback); return timerId; }, clearTimeout: id => timers.delete(id), requestAnimationFrame: callback => callback(),
    addEventListener: (type, callback) => { windowListeners[type] = callback; }, FolioreBridge: { message: value => messages.push(JSON.parse(value)) },
  };
  context.window = context;
  runInNewContext(runtime, context);
  context.FolioreReader.configure({ mode, rtl, margin: 20, theme: 'paper' }, {}, null, false);
  flush(); messages.length = 0;
  const dispatch = (type, extras) => {
    const event = { target: plain, defaultPrevented: false, preventDefault() { this.defaultPrevented = true; }, ...extras };
    listeners[type](event); return event;
  };
  const point = (x, y) => ({ clientX: x, clientY: y });
  return { messages, selection, context, flush, stylesheet, content: elements[0], viewport: elements[1],
    select: () => dispatch('selectionchange', {}),
    resizePages: count => { elements[0].scrollWidth = 400 * count - 40; windowListeners.resize(); flush(); },
    actions: () => messages.filter(m => m.event !== 'position'),
    click: (x = 200, y = 400, extras = {}) => dispatch('click', { ...point(x, y), button: 0, detail: 1, ...extras }),
    key: (key, target = plain) => dispatch('keydown', {key, target}),
    start: (x = 200, y = 400, count = 1, target = plain) => dispatch('touchstart', { target, touches: Array(count).fill(point(x, y)) }),
    move: (x, y, count = 1) => dispatch('touchmove', { touches: Array(count).fill(point(x, y)) }),
    end: (x = 200, y = 400, remaining = 0) => dispatch('touchend', { touches: Array(remaining).fill(point(x, y)), changedTouches: [point(x, y)] }),
    cancel: () => dispatch('touchcancel', {}),
    wait: ms => { now += ms; },
  };
}
test('initial pagination reports the measured fraction even without a page turn', () => {
  for (const pages of [1, 3]) {
    const r = reader();
    r.content.scrollWidth = 400 * pages - 40;
    r.context.FolioreReader.configure({ mode: 'pages', margin: 20, theme: 'paper' }, { progression: 0 }, null, false);
    r.flush();
    const position = r.messages.find(m => m.event === 'position');
    assert.equal(position.user, false);
    assert.equal(position.pages, pages);
    assert.equal(position.progression, pages === 1 ? 1 : 0);
  }
});

const link = { closest: selector => selector === 'a[href]' ? { href: 'https://example.org/note' } : null };

const illustration = (src = 'https://appassets.androidplatform.net/publication/art.png') => ({src,
  setAttribute() {}, getAttribute: () => 'Drawing', closest(selector) { return selector === 'img,image' ? this : null; }});
test('image taps open the original without turning pages, hiding chrome or following a wrapping link', () => {
  const image = illustration(), r = reader({images:[image]});
  for (const x of [10, 200, 390]) r.click(x, 400, {target:image});
  assert.deepEqual(r.actions(), Array(3).fill({event:'image', index:0, href:image.src}));
  assert.equal(r.messages.some(m => m.event === 'position'), false);
  const nestedLink = {closest:selector => selector === 'img,image' ? image : {href:'https://example.org'}};
  r.click(200, 400, {target:nestedLink}); assert.equal(r.actions().at(-1).event, 'image');
});
test('image keyboard activation works and data images are referenced without copying base64 through the bridge', () => {
  const image = illustration('data:image/png;base64,' + 'A'.repeat(10000)), r = reader({images:[image]});
  assert.equal(r.key('Enter', image).defaultPrevented, true); r.key(' ', image);
  r.click(0, 0, {target:image, detail:0});
  assert.deepEqual(r.actions(), Array(3).fill({event:'image', index:0, href:''}));
});
test('image drags, long presses and text selection do not open the viewer', () => {
  const image = illustration(), r = reader({images:[image]});
  r.start(); r.move(200, 500); r.end(200, 500); r.click(200, 500, {target:image});
  r.start(); r.wait(650); r.end(); r.click(200, 400, {target:image});
  r.selection.isCollapsed = false; r.click(200, 400, {target:image}); assert.deepEqual(r.actions(), []);
});

test('initial pagination uses the visible frame even when publisher content overflows', () => {
  const r = reader({ overflowWidth: 1216, overflowHeight: 1972 });
  assert.match(r.stylesheet.textContent, /width:360px!important;height:760px!important;column-width:360px/);
  assert.match(r.stylesheet.textContent, /max-height:760px!important/);
  const fixed = reader({ fixed: true });
  assert.doesNotMatch(fixed.stylesheet.textContent, /#foliore-content img\{height:auto/);
});

test('scrolling a wide table does not turn the book page', () => {
  const r = reader();
  r.start(300, 400, 1, { closest: selector => selector === '.foliore-table' ? {} : null });
  r.move(100, 400); r.end(100, 400); r.flush();
  assert.deepEqual(r.actions(), []);
  assert.equal(r.messages.length, 0);
});

for (const config of [{ mode: 'pages' }, { mode: 'scroll' }, { fixed: true }]) {
  test(`center taps toggle chrome in ${JSON.stringify(config)}`, () => {
    const r = reader(config);
    for (const [x, y] of [[200, 400], [120, 240], [280, 560]]) {
      r.start(x, y); r.end(x, y); r.click(x, y);
    }
    assert.deepEqual(r.actions(), Array(3).fill({ event: 'chrome' }));
    assert.equal(r.messages[0].event, 'position', 'flush the passage before toggling controls');
  });
}
test('top and bottom of the page do not hide controls', () => {
  const r = reader(); r.click(200, 80); r.click(200, 720); assert.deepEqual(r.actions(), []);
});
test('the center follows the visual viewport when a fixed-layout page is zoomed', () => {
  const r = reader({ fixed: true, visible: { width: 200, height: 400, offsetLeft: 150, offsetTop: 300 } });
  r.click(250, 500); r.click(200, 320);
  assert.deepEqual(r.actions(), [{ event: 'chrome' }]);
});
test('page edges still navigate with the book direction', () => {
  for (const rtl of [false, true]) {
    const r = reader({ rtl }); r.click(rtl ? 390 : 10, 400);
    assert.deepEqual(r.actions(), [{ event: 'chapter', direction: -1 }]);
  }
  for (const config of [{ mode: 'scroll' }, { fixed: true }]) {
    const r = reader(config); r.click(10, 400); r.click(390, 400); assert.deepEqual(r.actions(), []);
  }
});
test('links and keyboard link activation retain their own action', () => {
  const r = reader(); r.click(200, 400, { target: link }); r.click(0, 0, { target: link, detail: 0 });
  assert.deepEqual(r.actions(), Array(2).fill({ event: 'link', href: 'https://example.org/note' }));
});
test('selected text and embedded controls never toggle chrome', () => {
  const r = reader(); r.selection.isCollapsed = false;
  r.click(); assert.equal(r.click(200, 400, { target: link }).defaultPrevented, true); r.selection.isCollapsed = true;
  r.click(200, 400, { target: { closest: selector => selector === 'a[href]' ? null : {} } });
  assert.deepEqual(r.actions(), []);
});
test('a long press does not toggle or follow a link; the next tap works', () => {
  const r = reader(); r.start(); r.wait(650); r.end(); r.click(); r.click(200, 400, { target: link });
  assert.deepEqual(r.actions(), []);
  r.start(); r.end(); r.click(); assert.deepEqual(r.actions(), [{ event: 'chrome' }]);
});
test('dragging away and back is not a tap', () => {
  const r = reader(); r.start(); r.move(200, 450); r.move(200, 400); r.end(); r.click();
  assert.deepEqual(r.actions(), []);
});
test('vertical scroll gestures do not toggle chrome', () => {
  const r = reader({ mode: 'scroll' }); r.start(200, 600); r.move(200, 400); r.end(); r.click();
  assert.deepEqual(r.actions(), []);
});
test('a horizontal swipe turns a page once, without toggling chrome', () => {
  const r = reader(); r.start(200, 400); r.move(350, 400); const end = r.end(350, 400); r.click(350, 400);
  assert.equal(end.defaultPrevented, true);
  assert.deepEqual(r.actions(), [{ event: 'chapter', direction: -1 }]);
});
test('pinch and cancelled gestures ignore synthesized clicks', () => {
  const r = reader(); r.start(); r.start(200, 400, 2); r.end(200, 400, 1); r.move(350, 400); r.end(350, 400); r.click();
  r.start(); r.cancel(); r.click(); assert.deepEqual(r.actions(), []);
});
test('consecutive viewport resizes preserve the anchor until actual reading resumes', () => {
  const r = reader(); r.resizePages(5); r.context.FolioreReader.step(1); r.flush();
  assert.equal(r.context.FolioreReader.position().page, 2);
  r.click(); r.resizePages(2); assert.equal(r.context.FolioreReader.position().page, 1);
  r.click(); r.resizePages(5); assert.equal(r.context.FolioreReader.position().page, 2);
  // Actual reading replaces the anchor; the next resize must not restore the old passage.
  r.context.FolioreReader.step(1); r.flush(); r.click(); r.resizePages(5);
  assert.equal(r.context.FolioreReader.position().page, 3);
});

test('the final page is independent of the browser scroll range and stays stable on a center tap', () => {
  const r = reader();
  r.viewport.scrollTo = () => assert.fail('pagination must not use the clamped scroll range');
  r.context.FolioreReader.seek(1); r.flush();
  assert.equal(r.context.FolioreReader.position().page, 3);
  assert.equal(r.content.style.transform, 'translateX(-800px)');
  r.click(); r.flush();
  assert.equal(r.context.FolioreReader.position().page, 3);
  assert.equal(r.content.style.transform, 'translateX(-800px)');
});

test('selection cannot pan a paginated page, while scrolling mode preserves vertical progress', () => {
  for (const mode of ['pages', 'scroll']) {
    const r = reader({mode});
    r.selection.isCollapsed = false;
    r.viewport.scrollLeft = 23; r.viewport.scrollTop = 400;
    r.context.document.documentElement.scrollLeft = 12;
    r.context.document.body.scrollTop = 50;
    r.select();
    assert.equal(r.viewport.scrollLeft, 0);
    assert.equal(r.viewport.scrollTop, mode === 'pages' ? 0 : 400);
    assert.equal(r.context.document.documentElement.scrollLeft, 0);
    assert.equal(r.context.document.body.scrollTop, 0);
    assert.equal(r.selection.isCollapsed, false);
    assert.deepEqual(r.messages, []);
  }
  const fixed = reader({fixed:true});
  fixed.context.document.scrollingElement.scrollTop = 250;
  fixed.select();
  assert.equal(fixed.context.document.scrollingElement.scrollTop, 250);
});

test('switching to scrolling clears the old page translation and obsolete position reports', () => {
  const r = reader();
  r.context.FolioreReader.step(1);
  assert.equal(r.content.style.transform, 'translateX(-400px)');
  r.context.FolioreReader.configure({mode:'scroll', margin:20, theme:'paper'}, {progression:.5}, null, false);
  r.flush();
  assert.equal(r.content.style.transform, undefined);
  assert.equal(r.viewport.scrollTop, 1100);
  assert.equal(r.messages.length, 1);
  assert.equal(r.messages[0].user, false);
});

// The viewport scroll coordinates remain LTR; only publication text and gestures are RTL.
test('RTL text cannot reverse the pagination scroll container', () => {
  const r = reader({ rtl: true });
  assert.match(r.stylesheet.textContent, /#foliore-viewport\{[^}]*direction:ltr!important/);
  assert.match(r.stylesheet.textContent, /#foliore-content>\*\{direction:rtl\}/);
});
