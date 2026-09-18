import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {test} from 'node:test';
import {runInNewContext} from 'node:vm';

const source = readFileSync(new URL('../androidApp/src/main/assets/reader/image-viewer.js', import.meta.url), 'utf8');
function viewer() {
  const events = {}, windowEvents = {}, keys = {}; let time = 1000, closed = 0;
  const image = {naturalWidth:800, naturalHeight:600, complete:true, style:{}, addEventListener() {}, getBoundingClientRect() {
    const {x, y, scale} = state(), w = parseFloat(image.style.width) * scale, h = parseFloat(image.style.height) * scale;
    return {left:200+x-w/2, right:200+x+w/2, top:400+y-h/2, bottom:400+y+h/2};
  }};
  const stage = {addEventListener:(name, fn) => events[name] = fn, setPointerCapture() {}};
  const context = {document:{documentElement:stage, querySelector:() => image, addEventListener:(name, fn) => keys[name] = fn}, innerWidth:400, innerHeight:800, Date:{now:() => time}, addEventListener:(name, fn) => windowEvents[name] = fn, FolioreImage:{close:() => closed++}};
  context.window = context; runInNewContext(source, context);
  function state() {
    const [, x, y, scale] = image.style.transform.match(/translate\(([-\d.]+)px,([-\d.]+)px\) scale\(([-\d.]+)\)/);
    return {x:+x, y:+y, scale:+scale};
  }
  const pointer = (type, x, y, id = 1) => events['pointer'+type]({clientX:x, clientY:y, pointerId:id, button:0, preventDefault() {}});
  const tap = (x = 200, y = 400) => { pointer('down', x, y); time += 40; pointer('up', x, y); time += 80; };
  return {image, state, pointer, tap, closed:() => closed, api:context.FolioreImageViewer, resize:() => windowEvents.resize(), key:key => keys.keydown({key, preventDefault() {}})};
}
test('fits the original aspect ratio with margins and resets after resizing', () => {
  const f = viewer(); assert.equal(f.image.style.width, '368px'); assert.equal(f.image.style.height, '276px');
  f.api.zoomIn(); assert.equal(f.state().scale, 1.5); f.resize(); assert.deepEqual(f.state(), {x:0,y:0,scale:1});
});
test('single image taps stay open; double taps zoom and restore the fit', () => {
  const f = viewer(); f.tap(); assert.equal(f.state().scale, 1); f.tap(); assert.equal(f.state().scale, 2.5);
  f.tap(); f.tap(); assert.equal(f.state().scale, 1); assert.equal(f.closed(), 0);
});
test('pinch scales around its focus and cannot exceed the zoom limits', () => {
  const f = viewer(); f.pointer('down',150,400,1); f.pointer('down',250,400,2);
  f.pointer('move',350,400,2); assert.equal(f.state().scale, 2);
  f.pointer('move',1500,400,2); assert.equal(f.state().scale, 8);
  f.pointer('move',151,400,2); assert.equal(f.state().scale, 1);
  f.pointer('up',151,400,2); f.pointer('up',150,700,1); assert.equal(f.closed(), 0);
});
test('zoomed drags stay within the image and never dismiss it', () => {
  const f = viewer(); f.tap(); f.tap(); f.pointer('down',200,400); f.pointer('move',1200,1400); f.pointer('up',1200,1400);
  assert.equal(f.state().x, 276); assert.equal(f.state().y, 0); assert.equal(f.closed(), 0);
});
test('a deliberate vertical swipe closes the fitted image; short or horizontal drags do not', () => {
  const f = viewer();
  for (const [x,y] of [[200,450],[350,450]]) { f.pointer('down',200,400); f.pointer('move',x,y); f.pointer('up',x,y); }
  assert.equal(f.closed(), 0); assert.deepEqual(f.state(), {x:0,y:0,scale:1});
  f.pointer('down',200,400); f.pointer('move',200,600); f.pointer('up',200,600); assert.equal(f.closed(), 1);
});
test('backdrop taps and Escape close; cancelled gestures do not', () => {
  const f = viewer(); f.pointer('down',200,400); f.pointer('move',200,600); f.pointer('cancel',200,600); f.pointer('up',200,600);
  assert.equal(f.closed(), 0); assert.deepEqual(f.state(), {x:0,y:0,scale:1});
  f.tap(200,100); assert.equal(f.closed(), 1); f.key('Escape'); assert.equal(f.closed(), 2);
});
