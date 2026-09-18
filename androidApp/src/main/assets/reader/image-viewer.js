(() => {
  'use strict';
  const image = document.querySelector('img'), stage = document.documentElement, pointers = new Map();
  let width = 0, height = 0, scale = 1, x = 0, y = 0, start, previous, pinched = false, moved = false, lastTap = null;
  const clamp = (v, min, max) => Math.max(min, Math.min(max, v));
  const center = () => { const a = [...pointers.values()]; return {x:(a[0].x+a[1].x)/2, y:(a[0].y+a[1].y)/2, distance:Math.hypot(a[0].x-a[1].x,a[0].y-a[1].y)}; };
  function bounds() {
    const maxX = Math.max(0, (width * scale - innerWidth + 32) / 2), maxY = Math.max(0, (height * scale - innerHeight + 32) / 2);
    x = clamp(x, -maxX, maxX); y = clamp(y, -maxY, maxY);
  }
  function paint(animate = false) {
    image.style.transition = animate ? 'transform 160ms ease-out' : 'none';
    image.style.transform = `translate(-50%,-50%) translate(${x}px,${y}px) scale(${scale})`;
  }
  function reset() { scale = 1; x = y = 0; paint(true); }
  function zoom(next, point = {x:innerWidth/2,y:innerHeight/2}) {
    next = clamp(next, 1, 8);
    x = point.x - innerWidth/2 - (point.x - innerWidth/2 - x) * next/scale;
    y = point.y - innerHeight/2 - (point.y - innerHeight/2 - y) * next/scale;
    scale = next; bounds(); paint(true);
  }
  function fit() {
    if (!image.naturalWidth || !image.naturalHeight) return;
    const factor = Math.min((innerWidth-32)/image.naturalWidth, (innerHeight-32)/image.naturalHeight);
    width = image.naturalWidth * factor; height = image.naturalHeight * factor;
    image.style.width = width + 'px'; image.style.height = height + 'px'; image.style.visibility = 'visible'; reset();
  }
  const close = () => window.FolioreImage?.close();
  const failed = () => window.FolioreImage?.failed();
  image.addEventListener('load', fit); image.addEventListener('error', failed);
  if (image.complete) { if (image.naturalWidth) fit(); else failed(); }
  window.addEventListener('resize', fit);
  stage.addEventListener('pointerdown', event => {
    if (event.button !== 0) return;
    event.preventDefault(); stage.setPointerCapture(event.pointerId);
    const point = {x:event.clientX,y:event.clientY}; pointers.set(event.pointerId, point);
    if (pointers.size === 1) { start = {...point,time:Date.now()}; moved = pinched = false; previous = point; }
    else { pinched = true; lastTap = null; previous = center(); }
    image.style.transition = 'none';
  });
  stage.addEventListener('pointermove', event => {
    if (!pointers.has(event.pointerId)) return;
    event.preventDefault();
    const point = {x:event.clientX,y:event.clientY}; pointers.set(event.pointerId, point);
    if (pointers.size > 1) {
      const next = center(), newScale = clamp(scale * next.distance / Math.max(1, previous.distance), 1, 8);
      x = next.x - innerWidth/2 - (previous.x - innerWidth/2 - x) * newScale/scale;
      y = next.y - innerHeight/2 - (previous.y - innerHeight/2 - y) * newScale/scale;
      scale = newScale; bounds(); previous = next;
    } else {
      if (Math.hypot(point.x-start.x, point.y-start.y) > 10) moved = true;
      if (scale > 1) { x += point.x-previous.x; y += point.y-previous.y; bounds(); }
      else { x = (point.x-start.x) * .15; y = point.y-start.y; }
      previous = point;
    }
    paint();
  });
  stage.addEventListener('pointerup', event => {
    if (!pointers.has(event.pointerId)) return;
    pointers.delete(event.pointerId);
    if (pointers.size) {
      if (pointers.size === 1) { previous = [...pointers.values()][0]; start = {...previous,time:Date.now()}; }
      else previous = center();
      return;
    }
    const dx = event.clientX-start.x, dy = event.clientY-start.y;
    if (!pinched && scale === 1 && Math.abs(dy) > Math.max(80, innerHeight*.12) && Math.abs(dy)>Math.abs(dx)*1.25) { close(); return; }
    if (!pinched && !moved && Date.now()-start.time < 400) {
      const rect = image.getBoundingClientRect();
      if (event.clientX<rect.left || event.clientX>rect.right || event.clientY<rect.top || event.clientY>rect.bottom) { close(); return; }
      const point = {x:event.clientX,y:event.clientY,time:Date.now()};
      if (lastTap && point.time-lastTap.time < 300 && Math.hypot(point.x-lastTap.x,point.y-lastTap.y)<32) {
        zoom(scale > 1 ? 1 : 2.5, point); lastTap = null; return;
      }
      lastTap = point;
    } else lastTap = null;
    bounds(); paint(true);
  });
  stage.addEventListener('pointercancel', () => { pointers.clear(); lastTap = null; bounds(); paint(true); });
  stage.addEventListener('contextmenu', event => event.preventDefault());
  document.addEventListener('keydown', event => {
    if (event.key === 'Escape') close();
    else if (event.key === '+' || event.key === '=') zoom(scale*1.5);
    else if (event.key === '-') zoom(scale/1.5);
    else if (event.key === '0') reset();
    else return;
    event.preventDefault();
  });
  // Also available as nonvisual accessibility actions on the Android view.
  window.FolioreImageViewer = {zoomIn:() => zoom(scale*1.5), zoomOut:() => zoom(scale/1.5), reset};
})();
