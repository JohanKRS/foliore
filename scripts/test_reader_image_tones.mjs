import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {test} from 'node:test';
import {runInNewContext} from 'node:vm';

const source = readFileSync(new URL('../androidApp/src/main/assets/reader/image-tones.js', import.meta.url), 'utf8');
function sample(pixel, width = 64, height = 64) {
  const data = new Uint8ClampedArray(width * height * 4);
  for (let y = 0; y < height; y++) for (let x = 0; x < width; x++) data.set(pixel(x, y), (y * width + x) * 4);
  return {data, width, height};
}
const drawing = sample((x, y) => x > 5 && x < 58 && y > 5 && y < 58 && (x % 7 === 0 || y % 11 === 0) ? [25, 25, 25, 255] : [255, 255, 255, 255]);
const context = {window:{}}; runInNewContext(source, context);
const {isInkDrawing, css} = context.window.FolioreImageTone;

test('detects ink drawings with white or transparent margins', () => {
  assert.equal(isInkDrawing(drawing), true);
  assert.equal(isInkDrawing(sample((x, y) => x > 5 && x < 58 && y > 5 && y < 58 && x % 7 === 0 ? [30, 30, 30, 255] : [0, 0, 0, 0])), true);
});
test('keeps colorful illustrations, tonal photographs, dark backgrounds and blank images unchanged', () => {
  const color = sample((x, y) => x > 5 && x < 58 && y > 5 && y < 58 && x % 7 === 0 ? [180, 35, 20, 255] : [255, 255, 255, 255]);
  const photograph = sample((x, y) => { const v = x > 12 && x < 52 && y > 12 && y < 52 ? 110 + ((x * 3 + y) % 110) : 255; return [v, v, v, 255]; });
  for (const input of [color, photograph, sample(() => [30, 30, 30, 255]), sample(() => [255, 255, 255, 255]), sample(() => [0, 0, 0, 0])]) assert.equal(isInkDrawing(input), false);
});
test('rejects tiny or incomplete samples', () => {
  assert.equal(isInkDrawing(sample(() => [0, 0, 0, 255], 2, 2)), false);
  assert.equal(isInkDrawing({width:64, height:64, data:[]}), false);
});
test('adapts only recognized images and removes all effects when disabled or using white paper', () => {
  assert.match(css('paper', true), /img\.foliore-ink\{mix-blend-mode:multiply/);
  assert.match(css('dark', true), /invert\(1\).*mix-blend-mode:screen/);
  assert.equal(css('dark', false), ''); assert.equal(css('paper', false), ''); assert.equal(css('light', true), '');
});

function observerFixture() {
  const tasks = [], events = {}, images = []; let reads = 0, widths = [], selected;
  const canvas = {set width(n) { widths.push(n); }, set height(n) {}, getContext: () => ({drawImage(image) { selected = image; }, getImageData() { reads++; if (selected.fail) throw Error('Unsupported image'); return drawing; }})};
  const ctx = {setTimeout:task => tasks.push(task), document:{createElement:() => canvas, addEventListener:(name, callback) => { events[name] = callback; }}, getComputedStyle:image => ({filter:image.filter || 'none', mixBlendMode:'normal'})};
  ctx.window = ctx; runInNewContext(source, ctx);
  function image(src, extra = {}) {
    const classes = new Set(); const value = {tagName:'IMG', src, currentSrc:src, complete:true, naturalWidth:4000, naturalHeight:3000, isConnected:true,
      closest() {return this.cover ? this : null;}, classList:{toggle(name, enabled) {if(enabled) classes.add(name); else classes.delete(name);}, remove:name => classes.delete(name)}, classes, ...extra};
    images.push(value); return value;
  }
  return {image, events, tasks, reads:() => reads, widths:() => widths, flush() {while(tasks.length) tasks.shift()();}, observe:() => ctx.FolioreImageTone.observe({querySelectorAll:() => images, contains:value => images.includes(value)})};
}
test('analyzes a repeated resource once, in bounded samples outside the initial call', () => {
  const f = observerFixture(), a = f.image('drawing.png'), b = f.image('drawing.png');
  f.observe(); assert.equal(f.reads(), 0); f.flush();
  assert.equal(f.reads(), 1); assert.deepEqual(f.widths(), [96]);
  assert.ok(a.classes.has('foliore-ink') && b.classes.has('foliore-ink'));
});
test('never samples covers, animated GIFs, or images already filtered by the book', () => {
  const f = observerFixture(); f.image('cover.png', {cover:true}); f.image('animation.gif'); f.image('data:image/gif;base64,R0lGODlh'); f.image('styled.png', {filter:'sepia(1)'});
  f.observe(); f.flush(); assert.equal(f.reads(), 0);
});
test('waits for image loading and leaves failed analysis harmless', () => {
  const f = observerFixture(), loading = f.image('late.png', {complete:false}), failed = f.image('failed.png', {fail:true});
  f.observe(); f.flush(); assert.equal(failed.classes.size, 0); assert.equal(loading.classes.size, 0);
  loading.complete = true; f.events.load({target:loading}); f.flush(); assert.ok(loading.classes.has('foliore-ink'));
});
test('discards a queued result if the image source changed or was removed', () => {
  const f = observerFixture(), changed = f.image('before.png'), removed = f.image('removed.png'); f.observe();
  changed.currentSrc = 'after.png'; removed.isConnected = false; f.flush(); assert.equal(f.reads(), 0);
  f.events.load({target:changed}); f.flush(); assert.equal(f.reads(), 1); assert.ok(changed.classes.has('foliore-ink'));
});
