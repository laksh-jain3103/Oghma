/*
 * Oghma PDF viewer (runs inside the in-app WebView).
 *
 * Two ways to read the same PDF:
 *
 *  - Text view (default when the PDF has text): the PDF's text is extracted and re-flowed
 *    as real, resizable text. Selection, highlights and notes work on genuine DOM text.
 *
 *  - Page view: the PDF exactly as printed. Glyphs are drawn on a canvas by PDF.js, with an
 *    invisible text layer on top so text can still be selected and highlighted.
 *
 * Highlights work in both views and show up in both. A highlight is stored as:
 *   { "type":"pdf", "page":3, "y":0.41, "text":"...", "occ":0, "rects":[ {p,x,y,w,h}, ... ] }
 *   - rects (fractions of the page) exist when it was made in Page view
 *   - text + occ (Nth occurrence of that text on the page) let the other view find it again
 *
 * Talks to Android through the `OghmaPdf` JavascriptInterface (see PdfReaderView.kt):
 *   OghmaPdf.getHighlights()            -> JSON array of saved highlight locators (+ "color")
 *   OghmaPdf.onReady(pageCount)
 *   OghmaPdf.onProgress(page, frac, n)  -> page is 1-based, frac is 0..1 inside the page
 *   OghmaPdf.onHighlight(locatorJson, text, page, colorHex, withNote)
 *       colorHex is "" when withNote is true (Android asks the user in a dialog)
 *   OghmaPdf.onCopy(text)
 *   OghmaPdf.onError(message)
 *
 * Android -> page:
 *   oghma.addHighlight(locator, colorHex)   draw a highlight Android just saved
 *   oghma.ttsStart/ttsPause/ttsResume/ttsNext/ttsPrevious/ttsStop/ttsRateChanged()
 *   oghma.ttsOnStart/ttsOnDone/ttsOnError(id)   speech progress from Android
 *
 * Read aloud (Text view only): the page splits the text into sentences, hands them to Android
 * one by one (OghmaPdf.ttsSpeak) and highlights the sentence Android reports as started.
 *   OghmaPdf.ttsSpeak(text, id) / ttsHalt()
 *   OghmaPdf.onTtsState(active, playing) / onTtsError(message)
 *   OghmaPdf.onTtsRequested()      the "Play" button of the selection bar was tapped
 *   OghmaPdf.onDefine(text, topFrac, bottomFrac)   "Dictionary" was tapped; the fractions say
 *                                  where the word is, as 0..1 of the window height
 *   OghmaPdf.onTranslate(text, topFrac, bottomFrac)  same, for "Translate"
 *
 * While the voice is speaking, reading progress is reported from the spoken sentence (so the
 * book's progress bar follows the audio); otherwise it comes from the scroll position.
 * Android -> page: oghma.seekFraction(0..1) jumps to a point in the document.
 *                  oghma.clearLookup() removes the mark on the looked-up word.
 */
(function () {
    'use strict';

    var Android = window.OghmaPdf;
    var qs = new URLSearchParams(window.location.search);

    var FILE_URL = qs.get('file') || '/book.pdf';
    var START_PAGE = parseInt(qs.get('page'), 10) || 1;
    var START_FRAC = parseFloat(qs.get('y')) || 0;
    var MARGIN = 8;

    var DEFAULT_COLORS = ['#FFD60A', '#5DD97C', '#5AA9FF', '#FF7AB8', '#B48CFF'];
    var COLORS = (qs.get('colors') || '').split(',').filter(Boolean).map(function (c) {
        return '#' + c.replace('#', '');
    });
    if (!COLORS.length) { COLORS = DEFAULT_COLORS; }

    if (qs.get('bg')) {
        document.documentElement.style.setProperty('--bg', '#' + qs.get('bg'));
    }

    var pagesEl = document.getElementById('pages');
    var reflowEl = document.getElementById('reflow');
    var statusEl = document.getElementById('status');
    var indEl = document.getElementById('pageInd');
    var barEl = document.getElementById('bar');
    var noticeEl = document.getElementById('notice');
    var modeBtn = document.getElementById('modeBtn');
    var fsMinus = document.getElementById('fsMinus');
    var fsPlus = document.getElementById('fsPlus');

    var pdfDoc = null;
    var pageEls = [];     // Page view sections
    var reflowEls = [];   // Text view sections
    var ioPages = null;
    var ioReflow = null;
    var lastWidth = 0;

    var mode = 'text';    // 'text' | 'page'
    var fontSize = 18;    // px, Text view

    var highlights = [];  // every highlight of this PDF, see registerHighlight()

    function noop() {}

    /* ------------------------------------------------------------------ */
    /* Small helpers                                                      */
    /* ------------------------------------------------------------------ */

    function store(key, value) {
        try { window.localStorage.setItem(key, String(value)); } catch (e) { noop(); }
    }

    function load(key, fallback) {
        try {
            var v = window.localStorage.getItem(key);
            return v === null ? fallback : v;
        } catch (e) { return fallback; }
    }

    function fail(message) {
        statusEl.classList.remove('hidden');
        statusEl.textContent = message;
        if (Android && Android.onError) { Android.onError(String(message)); }
    }

    function showNotice(text, ms) {
        noticeEl.textContent = text;
        noticeEl.classList.remove('hidden');
        clearTimeout(showNotice._t);
        showNotice._t = setTimeout(function () { noticeEl.classList.add('hidden'); }, ms || 8000);
    }
    noticeEl.addEventListener('click', function () { noticeEl.classList.add('hidden'); });

    function normalizeText(s) {
        return String(s || '').replace(/\s+/g, ' ').trim();
    }

    function hexToRgba(hex, alpha) {
        var m = /^#?([0-9a-f]{6})$/i.exec(hex || '');
        var v = parseInt(m ? m[1] : 'FFD60A', 16);
        return 'rgba(' + ((v >> 16) & 255) + ',' + ((v >> 8) & 255) + ',' + (v & 255) + ',' + alpha + ')';
    }

    function activeEls() { return mode === 'text' ? reflowEls : pageEls; }

    /* ------------------------------------------------------------------ */
    /* Loading                                                            */
    /* ------------------------------------------------------------------ */

    function openDoc(extra) {
        var opts = {
            url: FILE_URL,
            // Absolute paths: the PDF.js worker resolves relative URLs against its own location.
            cMapUrl: '/pdfjs/lib/cmaps/',
            cMapPacked: true,
            standardFontDataUrl: '/pdfjs/lib/standard_fonts/',
            isEvalSupported: false,
            enableXfa: false
        };
        Object.keys(extra || {}).forEach(function (k) { opts[k] = extra[k]; });

        var task = pdfjsLib.getDocument(opts);
        task.onPassword = function () {
            fail('This PDF is password protected, which is not supported yet.');
        };
        return task.promise;
    }

    function init() {
        if (!window.pdfjsLib) {
            fail('The PDF engine failed to load.');
            return;
        }
        pdfjsLib.GlobalWorkerOptions.workerSrc = '/pdfjs/lib/pdf.worker.min.js';

        // First try with HTTP range requests + streaming (fast for big files).
        // If anything about that fails, retry by loading the whole file.
        openDoc({})
            .catch(function (err) {
                console.warn('Range load failed, retrying without range', err);
                return openDoc({ disableRange: true, disableStream: true });
            })
            .then(onDocLoaded)
            .catch(function (err) {
                fail('Could not open this PDF: ' + (err && err.message ? err.message : err));
            });
    }

    function onDocLoaded(doc) {
        pdfDoc = doc;

        mode = load('oghma.pdf.mode', 'text') === 'page' ? 'page' : 'text';
        fontSize = parseInt(load('oghma.pdf.fs', '18'), 10) || 18;
        applyFontSize();

        return doc.getPage(1).then(function (page) {
            var base = page.getViewport({ scale: 1 });
            buildPages(doc.numPages, base.width, base.height);
            buildReflow(doc.numPages);
            statusEl.classList.add('hidden');
            loadHighlights();

            // Does this PDF have any real text at all? Scanned PDFs do not.
            return detectText(doc).then(function (hasText) {
                if (!hasText) {
                    mode = 'page';
                    showNotice('This PDF has no selectable text: it looks like scanned images, so ' +
                        'there is nothing to highlight or copy. (Text recognition would be needed.)', 12000);
                }
                enterMode(mode, { page: START_PAGE, frac: START_FRAC });
                if (Android && Android.onReady) { Android.onReady(doc.numPages); }
            });
        });
    }

    function detectText(doc) {
        var limit = Math.min(doc.numPages, 6);
        var chain = Promise.resolve(0);
        for (var i = 1; i <= limit; i++) {
            (function (n) {
                chain = chain.then(function (total) {
                    if (total >= 30) { return total; }
                    return doc.getPage(n)
                        .then(function (p) { return p.getTextContent(); })
                        .then(function (tc) {
                            var t = 0;
                            tc.items.forEach(function (it) { t += (it.str || '').trim().length; });
                            return total + t;
                        })
                        .catch(function () { return total; });
                });
            })(i);
        }
        return chain.then(function (total) { return total >= 30; });
    }

    /* ------------------------------------------------------------------ */
    /* Switching between Text view and Page view                          */
    /* ------------------------------------------------------------------ */

    function enterMode(m, pos) {
        // Reading aloud follows the re-flowed text, so it ends when you leave Text view.
        if (m !== 'text' && tts.active) { ttsStop(); }

        mode = m;
        store('oghma.pdf.mode', m);

        pagesEl.style.display = m === 'page' ? 'block' : 'none';
        reflowEl.style.display = m === 'text' ? 'block' : 'none';

        modeBtn.textContent = m === 'text' ? 'Page view' : 'Text view';
        fsMinus.classList.toggle('hidden', m !== 'text');
        fsPlus.classList.toggle('hidden', m !== 'text');

        clearSelectionUi();

        if (m === 'page') {
            pageEls.forEach(function (el) { ioPages.unobserve(el); ioPages.observe(el); });
            scrollToPos(pos.page, pos.frac);
        } else {
            reflowEls.forEach(function (el) { ioReflow.unobserve(el); ioReflow.observe(el); });
            scrollToPos(pos.page, pos.frac);
            // Make sure the target page has its text before settling on the final position.
            loadReflowPage(reflowEls[Math.min(reflowEls.length, Math.max(1, pos.page)) - 1])
                .then(function () { scrollToPos(pos.page, pos.frac); });
        }
    }

    modeBtn.addEventListener('click', function () {
        var pos = locateTop();
        enterMode(mode === 'text' ? 'page' : 'text', pos);
    });

    function applyFontSize() {
        reflowEl.style.setProperty('--fs', fontSize + 'px');
    }

    function changeFont(delta) {
        var pos = locateTop();
        fontSize = Math.min(40, Math.max(12, fontSize + delta));
        store('oghma.pdf.fs', fontSize);
        applyFontSize();
        scrollToPos(pos.page, pos.frac);
        setTimeout(refreshTtsHighlight, 60);
    }
    fsMinus.addEventListener('click', function () { changeFont(-2); });
    fsPlus.addEventListener('click', function () { changeFont(2); });

    /* ------------------------------------------------------------------ */
    /* PAGE VIEW: layout + rendering                                      */
    /* ------------------------------------------------------------------ */

    function fitScale(baseWidth) {
        var avail = document.documentElement.clientWidth - 2 * MARGIN;
        return Math.max(0.1, avail / baseWidth);
    }

    function layoutPage(el) {
        var scale = fitScale(el._bw);
        el._scale = scale;
        el.style.width = (el._bw * scale) + 'px';
        el.style.height = (el._bh * scale) + 'px';
        el.style.setProperty('--scale-factor', String(scale));
    }

    function buildPages(count, baseW, baseH) {
        lastWidth = document.documentElement.clientWidth;
        var frag = document.createDocumentFragment();

        for (var i = 1; i <= count; i++) {
            var el = document.createElement('div');
            el.className = 'page';
            el.dataset.page = String(i);
            el._bw = baseW;
            el._bh = baseH;
            el._gen = 0;
            el._rendered = false;
            el._rendering = false;
            el._textReady = false;
            el._ratio = 0;

            var canvas = document.createElement('canvas');
            var hl = document.createElement('div');
            hl.className = 'hlLayer';
            var text = document.createElement('div');
            text.className = 'textLayer';

            el.appendChild(canvas);
            el.appendChild(hl);
            el.appendChild(text);

            layoutPage(el);
            frag.appendChild(el);
            pageEls.push(el);
        }
        pagesEl.appendChild(frag);

        ioPages = new IntersectionObserver(function (entries) {
            entries.forEach(function (e) {
                if (e.isIntersecting) {
                    renderPage(e.target);
                } else {
                    unrenderPage(e.target, false);
                }
            });
        }, { rootMargin: '150% 0px 150% 0px' });

        pageEls.forEach(function (el) { ioPages.observe(el); });
    }

    // Canvas resolution. Goes up when the user pinch-zooms so zoomed text stays sharp.
    function currentRatio() {
        var vv = window.visualViewport;
        var zoom = vv && vv.scale ? Math.max(1, vv.scale) : 1;
        return Math.min((window.devicePixelRatio || 1) * 1.25 * zoom, 4);
    }

    function renderPage(el) {
        if (!pdfDoc || el._rendered || el._rendering) { return; }
        el._rendering = true;
        var gen = ++el._gen;
        var n = parseInt(el.dataset.page, 10);

        pdfDoc.getPage(n).then(function (page) {
            if (gen !== el._gen) { return; }

            // Pages in one PDF can have different sizes: correct the placeholder.
            var base = page.getViewport({ scale: 1 });
            if (Math.abs(base.width - el._bw) > 0.5 || Math.abs(base.height - el._bh) > 0.5) {
                var beforeH = el.offsetHeight;
                var top = el.offsetTop;
                el._bw = base.width;
                el._bh = base.height;
                layoutPage(el);
                var delta = el.offsetHeight - beforeH;
                if (delta !== 0 && top + beforeH <= window.scrollY) {
                    window.scrollBy(0, delta);
                }
            }

            var vp = page.getViewport({ scale: el._scale });
            var ratio = currentRatio();
            // Keep the canvas under ~16 megapixels so huge zooms cannot exhaust memory.
            var px = vp.width * vp.height * ratio * ratio;
            if (px > 16000000) { ratio = Math.sqrt(16000000 / (vp.width * vp.height)); }
            el._ratio = ratio;

            var canvas = el.querySelector('canvas');
            canvas.width = Math.floor(vp.width * ratio);
            canvas.height = Math.floor(vp.height * ratio);
            canvas.style.width = vp.width + 'px';
            canvas.style.height = vp.height + 'px';

            var renderTask = page.render({
                canvasContext: canvas.getContext('2d', { alpha: false }),
                viewport: vp,
                transform: [ratio, 0, 0, ratio, 0, 0]
            });
            el._task = renderTask;

            // The invisible, selectable text.
            var tl = el.querySelector('.textLayer');
            tl.innerHTML = '';
            var textTask = pdfjsLib.renderTextLayer({
                textContentSource: page.streamTextContent({ includeMarkedContent: true }),
                container: tl,
                viewport: vp,
                textDivs: []
            });
            el._textTask = textTask;
            textTask.promise.then(function () {
                if (gen !== el._gen) { return; }
                var eoc = document.createElement('div');
                eoc.className = 'endOfContent';
                tl.appendChild(eoc);
                el._textReady = true;
                // Highlights made in Text view have no rectangles yet: place them now.
                highlights.forEach(function (h) {
                    if (h.page === n) { ensureRectDrawn(h); }
                });
            }).catch(noop);

            renderTask.promise.then(function () {
                if (gen !== el._gen) { return; }
                el._rendered = true;
                el._rendering = false;
            }).catch(function (err) {
                el._rendering = false;
                if (err && err.name === 'RenderingCancelledException') { return; }
                console.warn('Page render failed', n, err);
            });
        }).catch(function (err) {
            el._rendering = false;
            console.warn('getPage failed', n, err);
        });
    }

    function selectionTouches(el) {
        var sel = window.getSelection();
        return !!(sel && sel.rangeCount && !sel.isCollapsed && sel.containsNode(el, true));
    }

    function unrenderPage(el, force) {
        if (!el._rendered && !el._rendering) { return; }
        // Never throw away text the user is currently selecting.
        if (!force && selectionTouches(el)) { return; }

        el._gen++;
        try { if (el._task) { el._task.cancel(); } } catch (e) { noop(); }
        try { if (el._textTask) { el._textTask.cancel(); } } catch (e) { noop(); }
        el._task = null;
        el._textTask = null;

        var canvas = el.querySelector('canvas');
        canvas.width = 0;
        canvas.height = 0;
        el.querySelector('.textLayer').innerHTML = '';
        el._rendered = false;
        el._rendering = false;
        el._textReady = false;
    }

    // Pinch-zoom: re-render visible pages at a higher resolution once the zoom settles.
    var zoomTimer = null;
    if (window.visualViewport) {
        window.visualViewport.addEventListener('resize', function () {
            clearTimeout(zoomTimer);
            zoomTimer = setTimeout(function () {
                if (mode !== 'page') { return; }
                var want = currentRatio();
                var vh = window.innerHeight;
                pageEls.forEach(function (el) {
                    if (!el._rendered || !el._ratio || want <= el._ratio * 1.2) { return; }
                    var r = el.getBoundingClientRect();
                    if (r.bottom < 0 || r.top > vh) { return; }
                    if (selectionTouches(el)) { return; }
                    unrenderPage(el, true);
                    renderPage(el);
                });
            }, 350);
        });
    }

    /* ------------------------------------------------------------------ */
    /* TEXT VIEW: extract + re-flow                                       */
    /* ------------------------------------------------------------------ */

    function buildReflow(count) {
        var frag = document.createDocumentFragment();
        for (var i = 1; i <= count; i++) {
            var el = document.createElement('section');
            el.className = 'rpage';
            el.dataset.page = String(i);
            el.dataset.label = '\u2014 page ' + i + ' \u2014';
            el._loaded = false;
            el._promise = null;
            frag.appendChild(el);
            reflowEls.push(el);
        }
        reflowEl.appendChild(frag);

        ioReflow = new IntersectionObserver(function (entries) {
            entries.forEach(function (e) {
                if (e.isIntersecting) { loadReflowPage(e.target); }
            });
        }, { rootMargin: '200% 0px 200% 0px' });

        reflowEls.forEach(function (el) { ioReflow.observe(el); });
    }

    function loadReflowPage(el) {
        if (!el) { return Promise.resolve(); }
        if (el._promise) { return el._promise; }
        var n = parseInt(el.dataset.page, 10);

        el._promise = pdfDoc.getPage(n)
            .then(function (page) {
                return page.getTextContent().then(function (tc) {
                    return buildBlocks(tc, page.view);
                });
            })
            .then(function (blocks) {
                fillSection(el, blocks);
            })
            .catch(function (err) {
                console.warn('Text extraction failed for page', n, err);
                fillSection(el, []);
            });
        return el._promise;
    }

    function fillSection(el, blocks) {
        var beforeH = el.offsetHeight;
        var top = el.offsetTop;

        el.textContent = '';
        if (!blocks.length) {
            var none = document.createElement('p');
            none.className = 'muted';
            none.textContent = 'No text on this page. It may be an image or a figure \u2014 see Page view.';
            el.appendChild(none);
        }
        blocks.forEach(function (b) {
            var node = document.createElement(b.t === 'h' ? 'h3' : 'p');
            node.textContent = b.text;
            el.appendChild(node);
        });
        el.classList.add('loaded');
        el._loaded = true;

        // Keep the reader's place when a section above the viewport changes height.
        if (mode === 'text') {
            var delta = el.offsetHeight - beforeH;
            if (delta !== 0 && top + beforeH <= window.scrollY) {
                window.scrollBy(0, delta);
            }
        }

        // Draw saved highlights that belong on this page (and ones that spilled over from the last).
        var n = parseInt(el.dataset.page, 10);
        highlights.forEach(function (h) {
            if (h.page === n || h.page === n - 1) { ensureTextDrawn(h); }
        });
    }

    function median(arr) {
        if (!arr.length) { return 0; }
        var a = arr.slice().sort(function (x, y) { return x - y; });
        return a[Math.floor(a.length / 2)];
    }

    function percentile(arr, p) {
        if (!arr.length) { return 0; }
        var a = arr.slice().sort(function (x, y) { return x - y; });
        return a[Math.min(a.length - 1, Math.max(0, Math.floor(a.length * p)))];
    }

    /*
     * Turns PDF.js text items into lines, then into headings and paragraphs.
     * PDF.js already gives items in reading order for most documents, so we trust that order
     * (which also keeps multi-column pages readable) and only decide where lines and
     * paragraphs break.
     */
    function buildBlocks(tc, view) {
        var lines = [];
        var cur = null;

        function pushCur() {
            if (cur && cur.text.trim()) { lines.push(cur); }
            cur = null;
        }

        tc.items.forEach(function (it) {
            if (typeof it.str !== 'string') { return; }
            var t = it.transform;
            var fs = Math.hypot(t[2], t[3]) || Math.abs(t[3]) || it.height || 10;
            var x = t[4];
            var y = t[5];

            if (cur) {
                var sameLine = Math.abs(y - cur.y) <= Math.max(1.5, cur.fs * 0.45) &&
                    x >= cur.endX - cur.fs * 0.6;
                if (!sameLine) { pushCur(); }
            }
            if (!cur) {
                cur = { y: y, fs: fs, x0: x, endX: x, text: '', maxFs: fs };
            }

            if (it.str) {
                var gap = x - cur.endX;
                if (cur.text && gap > cur.fs * 0.12 && !/\s$/.test(cur.text) && !/^\s/.test(it.str)) {
                    cur.text += ' ';
                }
                cur.text += it.str;
                cur.endX = x + it.width;
                if (fs > cur.maxFs) { cur.maxFs = fs; }
            }
            if (it.hasEOL) { pushCur(); }
        });
        pushCur();

        // Drop stand-alone page numbers sitting in the top/bottom margin.
        var pageH = view[3] - view[1];
        var yBottom = view[1];
        lines = lines.filter(function (ln) {
            var rel = (ln.y - yBottom) / (pageH || 1);
            if (rel > 0.07 && rel < 0.93) { return true; }
            return !/^\s*(page\s*)?(\d{1,4}|[ivxlcdm]{1,7})(\s*(of|\/)\s*\d{1,4})?\s*$/i.test(ln.text);
        });
        if (!lines.length) { return []; }

        // Body text size = the size used by most characters on the page.
        var weights = {};
        lines.forEach(function (ln) {
            var k = Math.round(ln.fs * 2) / 2;
            weights[k] = (weights[k] || 0) + ln.text.length;
        });
        var bodyFs = 10;
        var best = -1;
        Object.keys(weights).forEach(function (k) {
            if (weights[k] > best) { best = weights[k]; bodyFs = parseFloat(k); }
        });

        var gaps = [];
        for (var i = 1; i < lines.length; i++) {
            var g = lines[i - 1].y - lines[i].y;
            if (g > 0) { gaps.push(g); }
        }
        var medGap = median(gaps) || bodyFs * 1.2;
        var left = percentile(lines.map(function (l) { return l.x0; }), 0.1);
        var right = percentile(lines.map(function (l) { return l.endX; }), 0.9);

        var blocks = [];
        var para = null;
        var prev = null;

        lines.forEach(function (ln) {
            var text = ln.text.replace(/\s+/g, ' ').trim();

            // Headings: clearly bigger than body text, and short.
            if (ln.maxFs > bodyFs * 1.18 && text.length < 140) {
                if (para) { blocks.push(para); para = null; }
                var last = blocks[blocks.length - 1];
                if (last && last.t === 'h' && prev && (prev.y - ln.y) < ln.maxFs * 1.8) {
                    last.text += ' ' + text;
                } else {
                    blocks.push({ t: 'h', text: text });
                }
                prev = ln;
                return;
            }

            var startNew = !para;
            if (para && prev) {
                var gap = prev.y - ln.y;
                if (gap > Math.max(medGap * 1.45, bodyFs * 1.9)) {
                    startNew = true;                                   // blank line between paragraphs
                } else if (ln.x0 - left > bodyFs * 0.8) {
                    startNew = true;                                   // indented first line
                } else if ((right - prev.endX) > bodyFs * 4 && /[.!?:;"\u201D\u2019)\]]\s*$/.test(prev.text)) {
                    startNew = true;                                   // short last line of a paragraph
                }
            }

            if (startNew) {
                if (para) { blocks.push(para); }
                para = { t: 'p', text: text };
            } else if (/[A-Za-z\u00C0-\u024F]-$/.test(para.text) && /^[a-z\u00DF-\u00FF]/.test(text)) {
                para.text = para.text.slice(0, -1) + text;             // re-join a hyphenated word
            } else {
                para.text += ' ' + text;
            }
            prev = ln;
        });
        if (para) { blocks.push(para); }
        return blocks;
    }

    /* ------------------------------------------------------------------ */
    /* Position + progress (works in both views)                          */
    /* ------------------------------------------------------------------ */

    function indexAtY(els, y) {
        var lo = 0;
        var hi = els.length - 1;
        while (lo < hi) {
            var mid = (lo + hi + 1) >> 1;
            if (els[mid].offsetTop <= y) { lo = mid; } else { hi = mid - 1; }
        }
        return lo;
    }

    function locateTop() {
        var els = activeEls();
        if (!els.length) { return { page: START_PAGE, frac: START_FRAC }; }
        var y = window.scrollY;
        var i = indexAtY(els, y);
        var el = els[i];
        var h = el.offsetHeight || 1;
        var frac = Math.min(1, Math.max(0, (y - el.offsetTop) / h));
        return { page: i + 1, frac: frac };
    }

    function scrollToPos(page, frac) {
        var els = activeEls();
        if (!els.length) { return; }
        var idx = Math.min(els.length, Math.max(1, page)) - 1;
        var el = els[idx];
        window.scrollTo(0, el.offsetTop + (frac || 0) * el.offsetHeight);
    }

    function reportProgress() {
        if (!activeEls().length) { return; }
        if (tts.playing) { return; }      // the spoken sentence reports progress instead
        var p = locateTop();
        if (Android && Android.onProgress) {
            Android.onProgress(p.page, p.frac, activeEls().length);
        }
    }

    var progTimer = null;
    var indTimer = null;

    window.addEventListener('scroll', function () {
        if (!activeEls().length) { return; }

        if (!progTimer) {
            progTimer = setTimeout(function () {
                progTimer = null;
                reportProgress();
            }, 500);
        }

        var p = locateTop();
        indEl.textContent = p.page + ' / ' + activeEls().length;
        indEl.classList.remove('hidden');
        clearTimeout(indTimer);
        indTimer = setTimeout(function () { indEl.classList.add('hidden'); }, 1200);
    }, { passive: true });

    // Rotation / window width change: re-fit pages and keep the reading position.
    var resizeTimer = null;
    window.addEventListener('resize', function () {
        clearTimeout(resizeTimer);
        resizeTimer = setTimeout(function () {
            var w = document.documentElement.clientWidth;
            if (!pageEls.length || w === lastWidth) { return; }
            lastWidth = w;

            var pos = locateTop();
            pageEls.forEach(function (el) {
                unrenderPage(el, true);
                layoutPage(el);
            });
            scrollToPos(pos.page, pos.frac);
            if (mode === 'page') {
                // Layout changed without crossing observer thresholds: re-arm it.
                pageEls.forEach(function (el) { ioPages.unobserve(el); ioPages.observe(el); });
            }
        }, 250);
    });

    /* ------------------------------------------------------------------ */
    /* Finding text again (shared by both views)                          */
    /* ------------------------------------------------------------------ */

    // Whitespace and hyphens are ignored when matching, so a word that was split across
    // lines in one view still matches in the other.
    var SKIP = /[\s\u00AD\u2010\u2011\-]/;

    function squashString(str) {
        var out = '';
        for (var i = 0; i < str.length; i++) {
            if (!SKIP.test(str.charAt(i))) { out += str.charAt(i); }
        }
        return out;
    }

    function squashNodes(roots) {
        var chars = [];
        var map = [];
        roots.forEach(function (root) {
            if (!root) { return; }
            var walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
            var node;
            while ((node = walker.nextNode())) {
                var v = node.nodeValue;
                for (var i = 0; i < v.length; i++) {
                    var ch = v.charAt(i);
                    if (SKIP.test(ch)) { continue; }
                    chars.push(ch);
                    map.push([node, i]);
                }
            }
        });
        return { str: chars.join(''), map: map };
    }

    // Range covering the `occ`-th (0-based) occurrence of `text` inside `roots`.
    function findRange(roots, text, occ) {
        var needle = squashString(text || '');
        if (!needle) { return null; }
        var sq = squashNodes(roots);
        var idx = -1;
        var from = 0;
        for (var k = 0; k <= (occ || 0); k++) {
            idx = sq.str.indexOf(needle, from);
            if (idx < 0) { break; }
            from = idx + needle.length;
        }
        if (idx < 0 && occ) { idx = sq.str.indexOf(needle); }   // fall back to the first one
        if (idx < 0) { return null; }

        var a = sq.map[idx];
        var b = sq.map[idx + needle.length - 1];
        var range = document.createRange();
        range.setStart(a[0], a[1]);
        range.setEnd(b[0], b[1] + 1);
        return range;
    }

    // How many earlier occurrences of the selected text are there in `root`?
    function countOccurrence(root, range, text) {
        try {
            var needle = squashString(text);
            if (!needle || !root) { return 0; }
            var pre = document.createRange();
            pre.setStart(root, 0);
            pre.setEnd(range.startContainer, range.startOffset);
            var startIdx = squashString(pre.toString()).length;
            var all = squashNodes([root]).str;
            var count = 0;
            var from = 0;
            for (;;) {
                var i = all.indexOf(needle, from);
                if (i < 0 || i >= startIdx) { break; }
                count++;
                from = i + needle.length;
            }
            return count;
        } catch (e) {
            return 0;
        }
    }

    // Paint a range with <mark> elements (works across several text nodes).
    function wrapRange(range, color) {
        var sc = range.startContainer;
        var so = range.startOffset;
        var ec = range.endContainer;
        var eo = range.endOffset;

        var host = range.commonAncestorContainer;
        if (host.nodeType !== 1) { host = host.parentNode; }

        var nodes = [];
        var walker = document.createTreeWalker(host, NodeFilter.SHOW_TEXT);
        var n;
        while ((n = walker.nextNode())) {
            if (range.intersectsNode(n)) { nodes.push(n); }
        }

        nodes.forEach(function (node) {
            var start = node === sc ? so : 0;
            var end = node === ec ? eo : node.nodeValue.length;
            if (end <= start) { return; }
            var r = document.createRange();
            r.setStart(node, start);
            r.setEnd(node, end);
            var mark = document.createElement('mark');
            mark.style.background = hexToRgba(color, 0.4);
            try { r.surroundContents(mark); } catch (e) { noop(); }
        });
    }

    /* ------------------------------------------------------------------ */
    /* Highlights (one list, drawn by whichever view is on screen)        */
    /* ------------------------------------------------------------------ */

    function drawRect(r, color) {
        var el = pageEls[r.p - 1];
        if (!el) { return; }
        var d = document.createElement('div');
        d.className = 'hl';
        d.style.left = (r.x * 100) + '%';
        d.style.top = (r.y * 100) + '%';
        d.style.width = (r.w * 100) + '%';
        d.style.height = (r.h * 100) + '%';
        d.style.background = hexToRgba(color || COLORS[0], 0.45);
        el.querySelector('.hlLayer').appendChild(d);
    }

    // Page view: rectangles on the page.
    function ensureRectDrawn(h) {
        if (h._rectDrawn) { return; }

        if (h.rects.length) {
            h.rects.forEach(function (r) { drawRect(r, h.color); });
            h._rectDrawn = true;
            return;
        }

        // Made in Text view: find the text in the page's text layer and measure it.
        var el = pageEls[h.page - 1];
        if (!el || !el._textReady) { return; }
        var range = findRange([el.querySelector('.textLayer')], h.text, h.occ);
        if (!range) { return; }
        var rects = collectRects(range);
        if (!rects.length) { return; }
        rects.forEach(function (r) { drawRect(r, h.color); });
        h._rectDrawn = true;
    }

    // Text view: <mark> around the text.
    function ensureTextDrawn(h) {
        if (h._textDrawn) { return; }
        var sec = reflowEls[h.page - 1];
        if (!sec || !sec._loaded) { return; }

        var range = findRange([sec], h.text, h.occ);
        if (!range) {
            var next = reflowEls[h.page];
            if (next && next._loaded) { range = findRange([sec, next], h.text, h.occ); }
        }
        if (!range) { return; }
        wrapRange(range, h.color);
        h._textDrawn = true;
    }

    function registerHighlight(loc, color, alreadyDrawn) {
        var h = {
            page: loc.page || 1,
            text: loc.text || '',
            occ: loc.occ || 0,
            rects: loc.rects || [],
            color: color || COLORS[0],
            _rectDrawn: false,
            _textDrawn: false
        };
        if (alreadyDrawn === 'rects') { h._rectDrawn = true; }
        if (alreadyDrawn === 'text') { h._textDrawn = true; }
        highlights.push(h);
        ensureRectDrawn(h);
        ensureTextDrawn(h);
        return h;
    }

    function loadHighlights() {
        var raw = '[]';
        try {
            if (Android && Android.getHighlights) { raw = Android.getHighlights() || '[]'; }
        } catch (e) { noop(); }

        try {
            JSON.parse(raw).forEach(function (loc) {
                if (loc) { registerHighlight(loc, loc.color, null); }
            });
        } catch (e) {
            console.warn('Bad highlight data', e);
        }
    }

    /* ------------------------------------------------------------------ */
    /* Selection -> highlight                                             */
    /* ------------------------------------------------------------------ */

    var pendingSel = null;
    var selTimer = null;
    var barTouching = false;

    function contains(a, b) {
        var t = 0.002;
        return a.p === b.p && b.x >= a.x - t && b.y >= a.y - t &&
            b.x + b.w <= a.x + a.w + t && b.y + b.h <= a.y + a.h + t;
    }

    function mergeRects(rects) {
        // Drop rects fully inside another one.
        var kept = rects.filter(function (r, i) {
            return !rects.some(function (o, j) {
                if (i === j) { return false; }
                if (contains(o, r)) {
                    return !contains(r, o) || j < i;   // identical rects: keep only the first
                }
                return false;
            });
        });

        // Join neighbouring rects on the same line so highlights don't overlap/darken.
        kept.sort(function (a, b) {
            return a.p - b.p || (Math.abs(a.y - b.y) < 0.004 ? a.x - b.x : a.y - b.y);
        });

        var out = [];
        kept.forEach(function (r) {
            var last = out[out.length - 1];
            if (last && last.p === r.p &&
                Math.abs(last.y - r.y) < Math.min(last.h, r.h) * 0.5 &&
                r.x <= last.x + last.w + 0.012) {
                var right = Math.max(last.x + last.w, r.x + r.w);
                var bottom = Math.max(last.y + last.h, r.y + r.h);
                last.y = Math.min(last.y, r.y);
                last.h = bottom - last.y;
                last.w = right - last.x;
            } else {
                out.push({ p: r.p, x: r.x, y: r.y, w: r.w, h: r.h });
            }
        });
        return out;
    }

    function collectRects(range) {
        var list = range.getClientRects();
        var raw = [];

        for (var i = 0; i < list.length; i++) {
            var r = list[i];
            if (r.width < 1 || r.height < 1) { continue; }

            var cy = r.top + r.height / 2 + window.scrollY;
            var el = pageEls[indexAtY(pageEls, cy)];
            if (!el) { continue; }

            var pr = el.getBoundingClientRect();
            if (!pr.width || !pr.height) { continue; }

            var rect = {
                p: parseInt(el.dataset.page, 10),
                x: (r.left - pr.left) / pr.width,
                y: (r.top - pr.top) / pr.height,
                w: r.width / pr.width,
                h: r.height / pr.height
            };

            // Skip container-sized boxes (whole text layer etc.), only real text lines.
            if (rect.h > 0.12 || rect.w > 1.05) { continue; }
            // Clamp to the page.
            rect.x = Math.max(0, rect.x);
            rect.y = Math.max(0, rect.y);
            rect.w = Math.min(rect.w, 1 - rect.x);
            rect.h = Math.min(rect.h, 1 - rect.y);
            if (rect.w <= 0 || rect.h <= 0) { continue; }
            raw.push(rect);
        }
        return mergeRects(raw);
    }

    function hideBar() {
        barEl.classList.add('hidden');
    }

    function elementOf(node) {
        return node && node.nodeType === 1 ? node : (node ? node.parentElement : null);
    }

    function clearPending() {
        if (!barTouching) {
            pendingSel = null;
            hideBar();
        }
    }

    function updateSelection() {
        var sel = window.getSelection();

        if (!sel || sel.rangeCount === 0 || sel.isCollapsed) { clearPending(); return; }

        var range = sel.getRangeAt(0);
        var anc = range.commonAncestorContainer;
        var text = normalizeText(sel.toString());
        if (!text) { clearPending(); return; }

        // ---- Text view ----
        if (reflowEl.contains(anc)) {
            var startEl = elementOf(range.startContainer);
            var sec = startEl ? startEl.closest('.rpage') : null;
            if (!sec) { clearPending(); return; }

            var secRect = sec.getBoundingClientRect();
            var rr = range.getBoundingClientRect();
            var yFrac = secRect.height ? Math.min(1, Math.max(0, (rr.top - secRect.top) / secRect.height)) : 0;

            pendingSel = {
                view: 'text',
                text: text,
                page: parseInt(sec.dataset.page, 10),
                y: yFrac,
                occ: countOccurrence(sec, range, text),
                range: range.cloneRange(),
                rects: []
            };
            barEl.classList.remove('hidden');
            return;
        }

        // ---- Page view ----
        if (pagesEl.contains(anc)) {
            var rects = collectRects(range);
            if (!rects.length) { clearPending(); return; }

            var first = rects[0];
            var tl = pageEls[first.p - 1] ? pageEls[first.p - 1].querySelector('.textLayer') : null;

            pendingSel = {
                view: 'page',
                text: text,
                page: first.p,
                y: first.y,
                occ: countOccurrence(tl, range, text),
                range: null,
                rects: rects
            };
            barEl.classList.remove('hidden');
            return;
        }

        clearPending();
    }

    document.addEventListener('selectionchange', function () {
        clearTimeout(selTimer);
        selTimer = setTimeout(updateSelection, 200);
    });

    function endBarTouch() {
        setTimeout(function () { barTouching = false; }, 500);
    }

    // Keep the selection alive while the buttons are pressed.
    ['touchstart', 'mousedown'].forEach(function (evt) {
        barEl.addEventListener(evt, function (e) {
            barTouching = true;
            e.preventDefault();
        }, { passive: false });
    });

    // Colour dots: tap one to highlight straight away in that colour.
    var dotsEl = document.getElementById('dots');
    COLORS.forEach(function (color) {
        var b = document.createElement('button');
        b.type = 'button';
        b.className = 'dot';
        b.style.background = color;
        b.setAttribute('aria-label', 'Highlight ' + color);
        b.addEventListener('click', function () { commitSelection(color, false); });
        dotsEl.appendChild(b);
    });

    function clearSelectionUi() {
        var sel = window.getSelection();
        if (sel) { sel.removeAllRanges(); }
        pendingSel = null;
        hideBar();
        endBarTouch();
    }

    function commitSelection(color, withNote) {
        var s = pendingSel;
        if (s) {
            var loc = {
                type: 'pdf',
                page: s.page,
                y: s.y,
                text: s.text,
                occ: s.occ,
                rects: s.rects
            };

            // With a note, Android shows its dialog first and calls addHighlight() when saved.
            if (!withNote) {
                if (s.view === 'text') {
                    wrapRange(s.range, color);
                    registerHighlight(loc, color, 'text');
                } else {
                    registerHighlight(loc, color, 'rects');
                    // Draw right away in the view we are in.
                    loc.rects.forEach(function (r) { drawRect(r, color); });
                }
            } else {
                // Remember where the text is so addHighlight() can draw it in the right view.
                commitSelection._range = s.range;
                commitSelection._view = s.view;
            }

            if (Android && Android.onHighlight) {
                Android.onHighlight(JSON.stringify(loc), s.text, s.page, withNote ? '' : color, !!withNote);
            }
        }
        clearSelectionUi();
    }

    document.getElementById('noteBtn').addEventListener('click', function () {
        commitSelection(null, true);
    });

    document.getElementById('copyBtn').addEventListener('click', function () {
        if (pendingSel && Android && Android.onCopy) {
            Android.onCopy(pendingSel.text);
        }
        clearSelectionUi();
    });

    // Dictionary: look the selected word up. The word stays marked behind the popup.
    var lookupLayer = document.createElement('div');
    lookupLayer.id = 'lookupLayer';
    document.body.appendChild(lookupLayer);

    function clearLookup() { lookupLayer.textContent = ''; }

    // Dictionary and Translate both mark the selected text, hand it to Android, and let a card open
    // on the side of the screen away from it. `bridgeName` is the Android method to call.
    function lookupSelection(bridgeName) {
        var s = pendingSel;
        var sel = window.getSelection();
        if (!s || !sel || sel.rangeCount === 0) { clearSelectionUi(); return; }

        // Remember where the text is, then drop the selection handles.
        var rects = sel.getRangeAt(0).getClientRects();
        var sx = window.scrollX;
        var sy = window.scrollY;
        var top = null;
        var bottom = null;

        clearLookup();
        for (var i = 0; i < rects.length; i++) {
            var rc = rects[i];
            if (rc.width < 1 || rc.height < 1) { continue; }
            var d = document.createElement('div');
            d.className = 'lookupHl';
            d.style.left = (rc.left + sx) + 'px';
            d.style.top = (rc.top + sy) + 'px';
            d.style.width = rc.width + 'px';
            d.style.height = rc.height + 'px';
            lookupLayer.appendChild(d);
            if (top === null || rc.top < top) { top = rc.top; }
            if (bottom === null || rc.bottom > bottom) { bottom = rc.bottom; }
        }

        var text = s.text;
        clearSelectionUi();

        if (Android && Android[bridgeName]) {
            var vh = window.innerHeight || 1;
            Android[bridgeName](
                text,
                top === null ? -1 : Math.max(0, top / vh),
                bottom === null ? -1 : Math.min(1, bottom / vh)
            );
        }
    }

    document.getElementById('dictBtn').addEventListener('click', function () {
        lookupSelection('onDefine');
    });

    document.getElementById('transBtn').addEventListener('click', function () {
        lookupSelection('onTranslate');
    });

    // Play: read aloud starting at the selected text.
    document.getElementById('playBtn').addEventListener('click', function () {
        var s = pendingSel;
        clearSelectionUi();
        if (!s) { return; }

        tts.pendingPlay = { page: s.page, text: s.text, occ: s.occ };

        if (tts.active) {
            ttsStart();                                   // engine already running: jump there
        } else if (Android && Android.onTtsRequested) {
            Android.onTtsRequested();                     // Android starts the engine, then calls ttsStart()
        }
    });


    /* ------------------------------------------------------------------ */
    /* READ ALOUD                                                         */
    /* ------------------------------------------------------------------ */

    var ttsLayer = document.createElement('div');
    ttsLayer.id = 'ttsLayer';
    document.body.appendChild(ttsLayer);

    var tts = {
        active: false,      // the reader is switched on (playing or paused)
        playing: false,
        gen: 0,             // bumped on every restart so late callbacks can be ignored
        seq: 0,
        queue: [],          // sentences already handed to Android: {id, pos, s}
        next: { sec: 0, sent: 0 },   // next sentence to hand over
        cursor: null,       // position of the sentence currently being read
        item: null,         // the same, with its text/element (for the highlight)
        endReached: false,
        filling: false,
        pendingPlay: null   // {page, text, occ}: where "Play" in the selection bar wants to start
    };

    var ABBREVIATION = /(?:^|[\s(])(?:Mr|Mrs|Ms|Dr|Prof|Sr|Jr|St|vs|etc|No|Fig|Vol|Inc|Ltd|Co|e\.g|i\.e|[A-Z])\.$/;

    // Text -> sentences, with their character offsets inside the paragraph.
    function splitSentences(text) {
        var re = /[^.!?\u2026]+(?:[.!?\u2026]+["'\u201D\u2019)\]]*|$)\s*/g;
        var raw = [];
        var m;
        while ((m = re.exec(text)) !== null) {
            if (m[0].length === 0) { re.lastIndex++; continue; }
            if (m[0].trim()) { raw.push({ start: m.index, end: m.index + m[0].length }); }
        }

        // "Dr. Smith" or "e.g. this" are not sentence ends.
        var merged = [];
        raw.forEach(function (r) {
            var prev = merged[merged.length - 1];
            if (prev && ABBREVIATION.test(text.slice(prev.start, prev.end).trim())) {
                prev.end = r.end;
            } else {
                merged.push({ start: r.start, end: r.end });
            }
        });

        // Android refuses very long utterances, so cut those at a space.
        var out = [];
        function add(a, b) {
            var t = text.slice(a, b).replace(/\s+/g, ' ').trim();
            if (t) { out.push({ start: a, end: b, text: t }); }
        }
        merged.forEach(function (r) {
            var a = r.start;
            while (r.end - a > 3000) {
                var cut = text.lastIndexOf(' ', a + 3000);
                if (cut <= a) { cut = a + 3000; }
                add(a, cut);
                a = cut;
            }
            add(a, r.end);
        });
        return out;
    }

    function sentencesOf(secIdx) {
        var el = reflowEls[secIdx];
        if (!el || !el._loaded) { return null; }
        if (el._sents) { return el._sents; }

        var list = [];
        var blocks = el.querySelectorAll('p:not(.muted), h3');
        for (var i = 0; i < blocks.length; i++) {
            var block = blocks[i];
            splitSentences(block.textContent).forEach(function (sn) {
                list.push({ el: block, start: sn.start, end: sn.end, text: sn.text });
            });
        }
        el._sents = list;
        return list;
    }

    // The sentence at `pos`, or the next one after it (moving on to later pages as needed).
    function resolveForward(pos) {
        if (pos.sec >= reflowEls.length) { return Promise.resolve(null); }
        return loadReflowPage(reflowEls[pos.sec]).then(function () {
            var list = sentencesOf(pos.sec) || [];
            if (pos.sent < list.length) { return { pos: pos, s: list[pos.sent] }; }
            return resolveForward({ sec: pos.sec + 1, sent: 0 });
        });
    }

    // The sentence at `pos`, or the previous one before it.
    function resolveBackward(pos) {
        if (pos.sec < 0) { return Promise.resolve(null); }
        return loadReflowPage(reflowEls[pos.sec]).then(function () {
            var list = sentencesOf(pos.sec) || [];
            var i = Math.min(pos.sent, list.length - 1);
            if (i >= 0) { return { pos: { sec: pos.sec, sent: i }, s: list[i] }; }
            return resolveBackward({ sec: pos.sec - 1, sent: Infinity });
        });
    }

    // Range for characters start..end of a paragraph (works even with highlight <mark>s inside).
    function rangeForOffsets(el, start, end) {
        var walker = document.createTreeWalker(el, NodeFilter.SHOW_TEXT);
        var pos = 0;
        var node;
        var range = document.createRange();
        var started = false;
        var last = null;
        while ((node = walker.nextNode())) {
            var len = node.nodeValue.length;
            last = node;
            if (!started && start < pos + len) {
                range.setStart(node, start - pos);
                started = true;
            }
            if (started && end <= pos + len) {
                range.setEnd(node, end - pos);
                return range;
            }
            pos += len;
        }
        if (started && last) {
            range.setEnd(last, last.nodeValue.length);
            return range;
        }
        return null;
    }

    function clearTtsHighlight() {
        ttsLayer.textContent = '';
        tts.item = null;
    }

    function highlightSentence(item) {
        ttsLayer.textContent = '';
        tts.item = item;

        var range = rangeForOffsets(item.s.el, item.s.start, item.s.end);
        if (!range) { return; }

        var sx = window.scrollX;
        var sy = window.scrollY;
        var rects = range.getClientRects();
        var firstTop = null;
        var lastBottom = null;

        for (var i = 0; i < rects.length; i++) {
            var rc = rects[i];
            if (rc.width < 1 || rc.height < 1) { continue; }
            var d = document.createElement('div');
            d.className = 'ttsHl';
            d.style.left = (rc.left + sx) + 'px';
            d.style.top = (rc.top + sy) + 'px';
            d.style.width = rc.width + 'px';
            d.style.height = rc.height + 'px';
            ttsLayer.appendChild(d);
            if (firstTop === null) { firstTop = rc.top; }
            lastBottom = rc.bottom;
        }

        // Progress follows the voice: where in its page is this sentence?
        var secEl = item.pos ? reflowEls[item.pos.sec] : null;
        if (secEl && firstTop !== null && Android && Android.onProgress) {
            var secRect = secEl.getBoundingClientRect();
            var frac = secRect.height ? (firstTop - secRect.top) / secRect.height : 0;
            Android.onProgress(item.pos.sec + 1, Math.min(1, Math.max(0, frac)), reflowEls.length);
        }

        // Keep the sentence being read on screen.
        if (firstTop !== null) {
            var vh = window.innerHeight;
            if (firstTop < 70 || lastBottom > vh - 90) {
                window.scrollTo({ top: sy + firstTop - vh * 0.25, behavior: 'smooth' });
            }
        }
    }

    function refreshTtsHighlight() {
        if (tts.active && tts.item) { highlightSentence(tts.item); }
    }

    function ttsNotify() {
        if (Android && Android.onTtsState) { Android.onTtsState(tts.active, tts.playing); }
    }

    function ttsHaltNative() {
        if (Android && Android.ttsHalt) { Android.ttsHalt(); }
    }

    // Keep up to three sentences queued in Android so there is no gap between them.
    function ttsFill() {
        if (!tts.playing || tts.filling) { return; }
        tts.filling = true;
        var gen = tts.gen;

        (function loop() {
            if (!tts.playing || gen !== tts.gen) { tts.filling = false; return; }
            if (tts.queue.length >= 3) { tts.filling = false; return; }

            resolveForward(tts.next).then(function (r) {
                if (!tts.playing || gen !== tts.gen) { tts.filling = false; return; }

                if (!r) {                       // end of the document
                    tts.filling = false;
                    tts.endReached = true;
                    if (!tts.queue.length) { ttsStop(); }
                    return;
                }

                var id = 'u' + (++tts.seq);
                tts.queue.push({ id: id, pos: r.pos, s: r.s });
                tts.next = { sec: r.pos.sec, sent: r.pos.sent + 1 };
                if (Android && Android.ttsSpeak) { Android.ttsSpeak(r.s.text, id); }
                loop();
            });
        })();
    }

    function ttsPlayFrom(pos) {
        tts.gen++;
        tts.queue = [];
        tts.endReached = false;
        tts.filling = false;
        tts.next = pos;
        tts.playing = true;
        ttsHaltNative();
        ttsNotify();
        ttsFill();
    }

    // Which sentence of `list` contains the given text position?
    function sentenceIndexAt(list, node, offset) {
        var startEl = elementOf(node);
        var block = startEl ? startEl.closest('p, h3') : null;
        if (!block) { return 0; }

        var pre = document.createRange();
        pre.setStart(block, 0);
        pre.setEnd(node, offset);
        var off = pre.toString().length;

        var firstOfBlock = -1;
        for (var i = 0; i < list.length; i++) {
            if (list[i].el !== block) { continue; }
            if (firstOfBlock < 0) { firstOfBlock = i; }
            if (off >= list[i].start && off < list[i].end) { return i; }
        }
        return firstOfBlock >= 0 ? firstOfBlock : 0;
    }

    // Start reading at the text the user selected ("Play" in the selection bar).
    function ttsStartFromSelection(p) {
        tts.active = true;
        tts.cursor = null;
        clearSelectionUi();

        var secIdx = Math.min(reflowEls.length, Math.max(1, p.page)) - 1;

        if (mode !== 'text') { enterMode('text', { page: secIdx + 1, frac: 0 }); }

        loadReflowPage(reflowEls[secIdx]).then(function () {
            var list = sentencesOf(secIdx) || [];
            var sentIdx = 0;
            var range = findRange([reflowEls[secIdx]], p.text, p.occ);
            if (range) { sentIdx = sentenceIndexAt(list, range.startContainer, range.startOffset); }
            ttsPlayFrom({ sec: secIdx, sent: sentIdx });
        });
    }

    function ttsStart() {
        if (!pdfDoc || !reflowEls.length) { return; }

        if (tts.pendingPlay) {
            var pending = tts.pendingPlay;
            tts.pendingPlay = null;
            ttsStartFromSelection(pending);
            return;
        }

        tts.active = true;
        tts.cursor = null;
        clearSelectionUi();

        if (mode !== 'text') { enterMode('text', locateTop()); }

        var secIdx = Math.max(0, locateTop().page - 1);

        loadReflowPage(reflowEls[secIdx]).then(function () {
            var list = sentencesOf(secIdx) || [];
            // First sentence that is (at least partly) below the toolbar.
            var chosen = list.length;
            for (var i = 0; i < list.length; i++) {
                var r = rangeForOffsets(list[i].el, list[i].start, list[i].end);
                if (r && r.getBoundingClientRect().bottom > 48) { chosen = i; break; }
            }
            ttsPlayFrom({ sec: secIdx, sent: chosen });
        });
    }

    function ttsPause() {
        if (!tts.active || !tts.playing) { return; }
        var resume = tts.cursor || (tts.queue.length ? tts.queue[0].pos : tts.next);
        tts.cursor = resume;
        tts.playing = false;
        tts.gen++;
        tts.queue = [];
        tts.filling = false;
        ttsHaltNative();
        ttsNotify();
    }

    function ttsResume() {
        if (!tts.active || tts.playing) { return; }
        ttsPlayFrom(tts.cursor || tts.next);
    }

    function ttsStop() {
        tts.active = false;
        tts.playing = false;
        tts.gen++;
        tts.queue = [];
        tts.cursor = null;
        tts.filling = false;
        ttsHaltNative();
        clearTtsHighlight();
        ttsNotify();
    }

    function ttsJump(direction) {
        if (!tts.active) { return; }
        var base = tts.cursor || tts.next;

        var target = direction > 0
            ? resolveForward({ sec: base.sec, sent: base.sent + 1 })
            : (base.sent > 0
                ? resolveBackward({ sec: base.sec, sent: base.sent - 1 })
                : resolveBackward({ sec: base.sec - 1, sent: Infinity }));

        target.then(function (r) {
            if (!r || !tts.active) { return; }
            if (tts.playing) {
                ttsPlayFrom(r.pos);
            } else {
                tts.cursor = r.pos;
                highlightSentence({ pos: r.pos, s: r.s });
            }
        });
    }

    // Jump to a point in the document (the book's progress bar). If the voice is on, it follows.
    function seekFraction(f) {
        var els = activeEls();
        if (!els.length) { return; }
        f = Math.min(1, Math.max(0, f));
        var total = f * els.length;
        var idx = Math.min(els.length - 1, Math.floor(total));
        var frac = total - idx;

        scrollToPos(idx + 1, frac);

        if (mode === 'text') {
            // The target page may still be a placeholder: load it, then settle on the spot.
            loadReflowPage(reflowEls[idx]).then(function () { scrollToPos(idx + 1, frac); });
        }

        if (tts.active && tts.playing && mode === 'text') {
            // Restart reading from the first sentence on screen at the new spot.
            tts.pendingPlay = null;
            loadReflowPage(reflowEls[idx]).then(function () {
                var list = sentencesOf(idx) || [];
                var chosen = list.length;
                for (var i = 0; i < list.length; i++) {
                    var r = rangeForOffsets(list[i].el, list[i].start, list[i].end);
                    if (r && r.getBoundingClientRect().bottom > 48) { chosen = i; break; }
                }
                ttsPlayFrom({ sec: idx, sent: chosen });
            });
        }
    }

    // Speech speed changed: restart the current sentence at the new speed.
    function ttsRateChanged() {
        if (tts.active && tts.playing) { ttsPlayFrom(tts.cursor || tts.next); }
    }

    function ttsFail(message) {
        if (Android && Android.onTtsError) { Android.onTtsError(message); }
        ttsStop();
    }

    // ---- progress reported by Android ----

    function ttsOnStart(id) {
        for (var i = 0; i < tts.queue.length; i++) {
            if (tts.queue[i].id === id) {
                var item = tts.queue[i];
                tts.cursor = item.pos;
                highlightSentence(item);
                return;
            }
        }
    }

    function ttsOnDone(id) {
        var idx = -1;
        for (var i = 0; i < tts.queue.length; i++) {
            if (tts.queue[i].id === id) { idx = i; }
        }
        if (idx < 0) { return; }            // from a queue we already threw away

        tts.queue.splice(0, idx + 1);

        if (!tts.queue.length && tts.endReached) { ttsStop(); return; }
        ttsFill();
    }

    function ttsOnError(id) {
        for (var i = 0; i < tts.queue.length; i++) {
            if (tts.queue[i].id === id) { ttsFail('Reading aloud stopped unexpectedly.'); return; }
        }
    }

    window.addEventListener('resize', function () { setTimeout(refreshTtsHighlight, 300); });

    /* ------------------------------------------------------------------ */

    window.oghma = {
        flushProgress: reportProgress,
        scrollToPage: function (page, frac) { scrollToPos(page, frac); },

        seekFraction: seekFraction,
        clearLookup: clearLookup,
        ttsStart: ttsStart,
        ttsPause: ttsPause,
        ttsResume: ttsResume,
        ttsNext: function () { ttsJump(1); },
        ttsPrevious: function () { ttsJump(-1); },
        ttsStop: ttsStop,
        ttsRateChanged: ttsRateChanged,
        ttsOnStart: ttsOnStart,
        ttsOnDone: ttsOnDone,
        ttsOnError: ttsOnError,

        // Called by Android after the note dialog saved a highlight.
        addHighlight: function (loc, color) {
            if (!loc) { return; }
            var view = commitSelection._view;
            var range = commitSelection._range;
            commitSelection._range = null;

            if (view === 'text' && range) {
                wrapRange(range, color);
                registerHighlight(loc, color, 'text');
            } else {
                registerHighlight(loc, color, null);   // rects (page view) are drawn by ensureRectDrawn
            }
        }
    };

    init();
})();
