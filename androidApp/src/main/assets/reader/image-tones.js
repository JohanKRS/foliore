(() => {
  'use strict';
  // This is deliberately conservative: ambiguous images keep their original colors.
  function isInkDrawing({data, width, height}) {
    const count = width * height;
    if (width < 12 || height < 12 || data.length !== count * 4) return false;
    let paper = 0, ink = 0, midtones = 0, colored = 0, border = 0, paperBorder = 0;
    for (let y = 0; y < height; y++) for (let x = 0; x < width; x++) {
      const i = (y * width + x) * 4, alpha = data[i + 3] / 255;
      // Transparent margins are equivalent to white paper for classification only.
      const r = data[i] * alpha + 255 * (1 - alpha), g = data[i + 1] * alpha + 255 * (1 - alpha), b = data[i + 2] * alpha + 255 * (1 - alpha);
      const low = Math.min(r, g, b), high = Math.max(r, g, b), light = (r + g + b) / 3;
      const white = low >= 240;
      if (white) paper++;
      if (high < 100) ink++;
      if (light >= 100 && light < 230) midtones++;
      if (high - low > 16) colored++;
      if (x === 0 || y === 0 || x === width - 1 || y === height - 1) { border++; if (white) paperBorder++; }
    }
    return paper / count >= .55 && paperBorder / border >= .9 && colored / count <= .005 &&
      ink / count >= .005 && midtones <= ink * 5;
  }

  function observe(root) {
    const results = new Map(), seen = new WeakMap(), queue = [];
    let pending = false, canvas;
    const schedule = () => {
      if (pending || !queue.length) return;
      pending = true;
      if (window.requestIdleCallback) window.requestIdleCallback(analyze, {timeout:200});
      else setTimeout(analyze, 0);
    };
    function analyze() {
      pending = false;
      const {image, source} = queue.shift();
      if (image.isConnected && (image.currentSrc || image.src) === source) {
        let drawing = results.get(source);
        if (drawing === undefined) {
          drawing = false;
          try {
            canvas ??= document.createElement('canvas');
            const scale = Math.min(1, 96 / Math.max(image.naturalWidth, image.naturalHeight));
            // Reset also clears a tainted canvas after an unsupported resource.
            canvas.width = Math.max(1, Math.round(image.naturalWidth * scale));
            canvas.height = Math.max(1, Math.round(image.naturalHeight * scale));
            const context = canvas.getContext('2d', {willReadFrequently:true});
            // Sampling without interpolation preserves the ink/paper distribution of fine lines.
            context.imageSmoothingEnabled = false;
            context.drawImage(image, 0, 0, canvas.width, canvas.height);
            drawing = isInkDrawing(context.getImageData(0, 0, canvas.width, canvas.height));
          } catch (_) { /* Failed analysis must never interfere with reading. */ }
          if (results.size >= 128) results.delete(results.keys().next().value);
          results.set(source, drawing);
        }
        image.classList.toggle('foliore-ink', drawing);
      }
      schedule();
    }
    function enqueue(image) {
      if (image.tagName !== 'IMG' || !image.complete || !image.naturalWidth) return;
      const source = image.currentSrc || image.src;
      if (!source || seen.get(image) === source) return;
      seen.set(image, source);
      image.classList.remove('foliore-ink');
      if (image.closest('[data-foliore-cover]') || /(?:^data:image\/gif[;,]|\.gif(?:[?#]|$))/i.test(source)) return;
      const css = getComputedStyle(image);
      if (css.filter !== 'none' || css.mixBlendMode !== 'normal') return;
      queue.push({image, source}); schedule();
    }
    const images = [...root.querySelectorAll('img')];
    if (typeof IntersectionObserver !== 'undefined') {
      // Analyze nearby pages, not every illustration in a long chapter at once.
      const observer = new IntersectionObserver(entries => entries.forEach(entry => {
        if (entry.isIntersecting) enqueue(entry.target);
      }), {rootMargin:'100% 100%'});
      images.forEach(image => observer.observe(image));
      document.addEventListener('load', event => {
        if (event.target.tagName === 'IMG' && root.contains(event.target)) {
          observer.unobserve(event.target); observer.observe(event.target);
        }
      }, true);
    } else {
      images.forEach(enqueue);
      document.addEventListener('load', event => { if (root.contains(event.target)) enqueue(event.target); }, true);
    }
  }

  function css(theme, enabled) {
    if (!enabled || theme === 'light') return '';
    return '#foliore-content img.foliore-ink{' + (theme === 'dark'
      ? 'filter:invert(1)!important;mix-blend-mode:screen!important;opacity:.88!important'
      : 'mix-blend-mode:multiply!important') + '}';
  }
  window.FolioreImageTone = {isInkDrawing, observe, css};
})();
