(() => {
  'use strict';
  let lastLocator = null, resizeAnchor = null;
  let options = {}, page = 0, pages = 1, ready = false, suppress = true, timer = 0, touch = null;
  const fixed = document.body.dataset.folioreFixed === 'true';
  const textDirection = getComputedStyle(document.body).direction;
  const content = document.createElement('main'); content.id = 'foliore-content';
  [...document.body.childNodes].filter(n => n.nodeName !== 'SCRIPT').forEach(n => content.appendChild(n));
  const viewport = document.createElement('div'); viewport.id = 'foliore-viewport'; viewport.append(content); document.body.prepend(viewport);
  // Measure a fixed viewport, not the document's transient overflow width while images load.
  if (!fixed) { viewport.style.setProperty('position', 'fixed', 'important'); viewport.style.setProperty('inset', '0', 'important'); }
  if (!fixed) content.querySelectorAll('table').forEach(table => {
    const frame = document.createElement('div'); frame.className = 'foliore-table'; table.replaceWith(frame); frame.append(table);
  });
  if (!fixed) content.querySelectorAll('img').forEach(image => {
    for (let node = image; node && node !== content && /^[\s"'“”‘’«»‹›„‚]*$/.test(node.textContent); node = node.parentElement) {
      const css = getComputedStyle(node);
      if (css.float === 'none') continue;
      // A raised floating initial can start in the previous column even with break-inside:avoid.
      if (parseFloat(css.marginTop) < 0) node.classList.add('foliore-raised-image');
      break;
    }
  });
  const scroller = () => fixed ? document.scrollingElement : viewport;
  const scrollTo = (x, y, smooth = false) => scroller().scrollTo({left:x, top:y, behavior:smooth ? 'smooth' : 'instant'});
  const style = document.getElementById('foliore-style');
  function send(value) { if (window.FolioreBridge) window.FolioreBridge.message(JSON.stringify(value)); }
  function nodes() {
    const walker = document.createTreeWalker(content, NodeFilter.SHOW_TEXT, {acceptNode(n) {
      return n.parentElement && !n.parentElement.closest('script,style,nav,noscript,iframe,object,mark[data-foliore-hidden]') ? NodeFilter.FILTER_ACCEPT : NodeFilter.FILTER_REJECT;
    }});
    const result = []; let n; while ((n = walker.nextNode())) result.push(n); return result;
  }
  let cachedText = null;
  function allText() { return cachedText ??= nodes().map(n => n.textContent).join(''); }
  function currentOffset() {
    let range;
    const inset = Math.max(options.margin, (innerWidth - 720) / 2) + 8;
    const x = Math.max(8, Math.min(innerWidth - 8, textDirection === 'rtl' ? innerWidth - inset : inset)), y = Math.min(innerHeight - 8, options.margin + 8);
    if (document.caretRangeFromPoint) range = document.caretRangeFromPoint(x, y);
    if (range && range.startContainer.nodeType === Node.TEXT_NODE && content.contains(range.startContainer)) {
      let offset = 0; for (const n of nodes()) { if (n === range.startContainer) {
        let start = range.startOffset;
        while (start > 0 && !/\s/.test(n.textContent[start - 1]) && range.startOffset - start < 40) start--;
        return offset + start;
      } offset += n.length; }
    }
    let offset = 0;
    for (const n of nodes()) {
      if (n.textContent.trim()) {
        const r = document.createRange(); r.selectNodeContents(n); const rects = r.getClientRects();
        const table = n.parentElement.closest('.foliore-table')?.getBoundingClientRect();
        const left = Math.max(0, table?.left || 0), top = Math.max(0, table?.top || 0);
        const right = Math.min(innerWidth, table?.right ?? innerWidth), bottom = Math.min(innerHeight, table?.bottom ?? innerHeight);
        if (left < right && top < bottom && [...rects].some(r => r.bottom > top && r.top < bottom && r.right > left && r.left < right)) return offset;
      }
      offset += n.length;
    }
    return 0;
  }
  function maxScroll() { return Math.max(0, scroller().scrollHeight - innerHeight); }
  function measure() { pages = options.mode === 'pages' && !fixed ? Math.max(1, Math.round(viewport.scrollWidth / innerWidth)) : 1; }
  function locator() {
    measure(); const offset = currentOffset();
    const progression = options.mode === 'pages' && !fixed ? (pages > 1 ? page / (pages - 1) : 1) : (maxScroll() ? scroller().scrollTop / maxScroll() : 0);
    return {event:'position', progression:Math.max(0, Math.min(1, progression)), textOffset:offset, quote:allText().slice(offset, offset + 100), page:page + 1, pages};
  }
  function report(user) { if (!ready) return; if (user) resizeAnchor = null; lastLocator = locator(); send({...lastLocator, user:!!user}); }
  function atOffset(offset, quote) {
    const list = nodes(); const text = list.map(n => n.textContent).join('');
    if (quote && text.slice(offset, offset + quote.length) !== quote) {
      const next = text.indexOf(quote, Math.max(0, offset - 500));
      const anywhere = next >= 0 ? next : text.indexOf(quote); if (anywhere >= 0) offset = anywhere;
    }
    let count = 0;
    for (const n of list) {
      if (count + n.length > offset) {
        const r = document.createRange(); r.setStart(n, Math.max(0, offset - count)); r.setEnd(n, Math.min(n.length, offset - count + 1));
        const table = n.parentElement.closest('.foliore-table');
        if (table) { const a = r.getBoundingClientRect(), b = table.getBoundingClientRect(); table.scrollTop += a.top - b.top; table.scrollLeft += a.left - b.left; }
        return r.getBoundingClientRect();
      }
      count += n.length;
    }
    return null;
  }
  function goPosition(loc, fragment, user) {
    suppress = true; measure();
    const target = fragment && document.getElementById(fragment);
    const rect = target ? target.getBoundingClientRect() : loc && (loc.textOffset > 0 || loc.quote) ? atOffset(loc.textOffset || 0, loc.quote || '') : null;
    if (options.mode === 'pages' && !fixed) {
      page = Math.max(0, Math.min(pages - 1, rect ? Math.floor((rect.left + scroller().scrollLeft) / innerWidth) : Math.round((loc?.progression || 0) * (pages - 1))));
      scrollTo(page * innerWidth, 0);
    } else scrollTo(0, rect ? Math.max(0, rect.top + scroller().scrollTop - options.margin) : (loc?.progression || 0) * maxScroll());
    setTimeout(() => { suppress = false; report(user); }, 120);
  }
  function configure(settings, loc, fragment, user, resizing = false) {
    if (!resizing) resizeAnchor = null;
    const previous = ready ? locator() : null;
    options = settings; suppress = true; ready = false;
    const width = fixed ? innerWidth : viewport.clientWidth, height = fixed ? innerHeight : viewport.clientHeight;
    const palette = {paper:['#f7f2e8','#302b24'], light:['#ffffff','#202020'], dark:['#191c1b','#e4e3dd']}[options.theme] || ['#f7f2e8','#302b24'];
    const m = Math.max(options.margin, (width - 720) / 2), vertical = options.margin;
    const colors = `html,body{background:${palette[0]}!important;color:${palette[1]}!important;}`;
    const typography = options.publisherStyles || fixed ? '' : `#foliore-content{font-family:${options.font}!important;font-size:${options.fontSize}px!important;line-height:${options.lineHeight}!important;text-align:${options.justify ? 'justify' : 'start'}!important;}#foliore-content *:not(pre):not(code){font-family:inherit!important;line-height:inherit!important;color:inherit!important;background-color:transparent!important;}#foliore-content :is(p,div,span,section,article,li,td,th,blockquote,a,em,strong){font-size:inherit!important;}#foliore-content h1{font-size:1.5em!important;line-height:1.2!important}#foliore-content h2{font-size:1.3em!important;line-height:1.3!important}#foliore-content h3{font-size:1.15em!important}#foliore-content p{orphans:2;widows:2;}`;
    const layout = fixed ? 'html{overflow:auto}body{margin:0}#foliore-content{padding:0}' : options.mode === 'pages' ?
      `html,body{overflow:hidden!important;margin:0!important;padding:0!important;height:100%!important}#foliore-viewport{position:fixed!important;inset:0!important;overflow:hidden!important;direction:ltr!important;}#foliore-content{box-sizing:border-box!important;margin:${vertical}px ${m}px!important;padding:0!important;width:${Math.max(80, width-2*m)}px!important;height:${Math.max(80, height-2*vertical)}px!important;column-width:${Math.max(80, width-2*m)}px!important;column-gap:${2*m}px!important;column-fill:auto!important;overflow:visible!important;direction:ltr!important}#foliore-content>*{direction:${textDirection}}` :
      `html,body{overflow:hidden!important;margin:0!important;padding:0!important;height:100%!important}#foliore-viewport{position:fixed!important;inset:0!important;overflow-x:hidden!important;overflow-y:auto!important;}#foliore-content{box-sizing:border-box!important;padding:${vertical}px!important;max-width:${720 + 2*vertical}px!important;margin:0 auto!important;column-width:auto!important;height:auto!important}`;
    const mediaWidth = Math.max(80, width - 2*m), mediaHeight = Math.max(80, height - 2*vertical);
    const media = fixed ? '' : `#foliore-content :is(div,section,article,figure,aside,blockquote){max-width:100%!important;min-width:0!important;box-sizing:border-box!important}#foliore-content img,#foliore-content svg{max-width:100%!important;max-width:min(100%,${mediaWidth}px)!important;object-fit:contain}#foliore-content img{height:auto!important;max-height:${mediaHeight}px!important}#foliore-content .foliore-table{overflow:auto!important;max-height:${mediaHeight}px!important;break-inside:avoid!important}#foliore-content table{max-width:100%}#foliore-content pre{white-space:pre-wrap;overflow-wrap:anywhere}`;
    const floats = !fixed && options.mode === 'pages' ? '#foliore-content .foliore-raised-image{margin-top:0!important;break-inside:avoid!important}' : '';
    style.textContent = `${colors}${layout}${typography}${media}${floats}#foliore-content a[href]{color:${options.theme === 'dark' ? '#d7b17a' : '#95612c'}!important}::selection{background:#d7b17a66}#foliore-content mark.foliore-match{background:#e7c467!important;color:#211b12!important}`;
    requestAnimationFrame(() => requestAnimationFrame(() => { ready = true; goPosition(loc || previous || {}, fragment, user); }));
  }
  function step(direction) {
    if (!ready) return;
    if (options.mode === 'pages' && !fixed) {
      measure(); const next = page + direction;
      if (next < 0 || next >= pages) send({event:'chapter', direction});
      else { page = next; suppress = true; scrollTo(page * innerWidth, 0); setTimeout(() => { suppress = false; report(true); }, 40); }
    } else {
      const next = scroller().scrollTop + direction * innerHeight * 0.85;
      if ((direction < 0 && scroller().scrollTop <= 2) || (direction > 0 && scroller().scrollTop >= maxScroll() - 2)) send({event:'chapter',direction});
      else scrollTo(0, Math.max(0, Math.min(maxScroll(), next)), true);
    }
  }
  window.FolioreReader = {configure, step, report:() => report(false), position:locator, flush:() => {if(timer){clearTimeout(timer);timer=0;report(true)}}, seek:(value) => goPosition({progression:value}, null, true), go:(loc, fragment) => goPosition(loc, fragment, true),
    highlight(query) {
      document.querySelectorAll('mark.foliore-match').forEach(n => n.replaceWith(document.createTextNode(n.textContent))); content.normalize();
      if (!query) return;
      const needle = query.toLocaleLowerCase(); let count = 0;
      for (const n of nodes()) { if (count > 250) break; const i = n.textContent.toLocaleLowerCase().indexOf(needle); if (i < 0) continue;
        const after = n.splitText(i); after.splitText(query.length); const mark = document.createElement('mark'); mark.className='foliore-match'; after.replaceWith(mark); mark.append(after); count++;
      }
    }
  };
  document.addEventListener('scroll', () => { if (suppress || !ready) return; clearTimeout(timer); timer=setTimeout(() => {timer=0;report(true)}, 180); }, {passive:true,capture:true});
  document.addEventListener('click', e => {
    if (!ready || e.defaultPrevented || e.button !== 0) return;
    if (!getSelection().isCollapsed) { e.preventDefault(); return; }
    // A WebView may synthesize a click after a drag, long press or interrupted gesture.
    const recentTouch = e.detail !== 0 && touch && Date.now() - touch.endedAt < 750;
    if (recentTouch && (touch.moved || touch.long || touch.blocked)) { e.preventDefault(); return; }
    const a = e.target.closest?.('a[href]');
    if (a) { e.preventDefault(); send({event:'link', href:a.href}); return; }
    if (e.detail === 0 || e.target.closest?.('button,input,textarea,select,summary,[role="button"],[contenteditable="true"]')) return;
    // Use the visible viewport so the target stays centered even in a zoomed fixed-layout book.
    const visible = window.visualViewport;
    const x = (e.clientX - (visible?.offsetLeft || 0)) / (visible?.width || innerWidth);
    const y = (e.clientY - (visible?.offsetTop || 0)) / (visible?.height || innerHeight);
    if (options.mode === 'pages' && !fixed && x < .2) step(options.rtl ? 1 : -1);
    else if (options.mode === 'pages' && !fixed && x > .8) step(options.rtl ? -1 : 1);
    else if (x >= .25 && x <= .75 && y >= .25 && y <= .75) {
      // Save the visible passage before hiding/showing controls resizes the page.
      const user = !!timer; clearTimeout(timer); timer = 0; report(user);
      resizeAnchor ??= lastLocator;
      send({event:'chrome'});
    }
  });
  document.addEventListener('touchstart', e => {
    if (touch && !touch.endedAt) { touch.blocked = true; return; }
    const t = e.touches[0]; if (!t) return;
    touch = {x:t.clientX, y:t.clientY, time:Date.now(), endedAt:0, moved:false, long:false, blocked:e.touches.length !== 1, table:!!e.target.closest?.('.foliore-table')};
  }, {passive:true});
  document.addEventListener('touchmove', e => {
    if (!touch) return;
    const t = e.touches[0];
    if (e.touches.length !== 1) touch.blocked = true;
    if (t && Math.hypot(t.clientX - touch.x, t.clientY - touch.y) > 12) touch.moved = true;
  }, {passive:true});
  document.addEventListener('touchend', e => {
    if (!touch) return;
    if (e.touches.length) { touch.blocked = true; return; }
    touch.endedAt = Date.now(); touch.long = touch.endedAt - touch.time > 400;
    const t = e.changedTouches[0]; if (!t) { touch.blocked = true; return; }
    const dx = t.clientX - touch.x, dy = t.clientY - touch.y;
    touch.moved ||= Math.hypot(dx, dy) > 12;
    if (options.mode==='pages' && !fixed && !touch.table && !touch.long && !touch.blocked && Math.abs(dx)>60 && Math.abs(dx)>Math.abs(dy)*1.5 && getSelection().isCollapsed) { step((dx<0?1:-1)*(options.rtl?-1:1)); e.preventDefault(); }
  }, {passive:false});
  document.addEventListener('touchcancel', () => { if (touch) { touch.blocked = true; touch.endedAt = Date.now(); } }, {passive:true});
  let resizeTimer;
  window.addEventListener('resize', () => {
    // Reflow can put the passage on a page that starts earlier. Keep the original anchor
    // through consecutive layout changes, until the user actually moves through the book.
    resizeAnchor ??= lastLocator;
    suppress = true;
    clearTimeout(timer); timer = 0;
    clearTimeout(resizeTimer);
    resizeTimer = setTimeout(() => { if (Object.keys(options).length) configure(options, resizeAnchor, null, false, true); }, 100);
  });
  document.addEventListener('keydown', e => { if (e.key==='ArrowRight' || e.key==='PageDown') {e.preventDefault();step(1)} if (e.key==='ArrowLeft' || e.key==='PageUp') {e.preventDefault();step(-1)} });
})();
