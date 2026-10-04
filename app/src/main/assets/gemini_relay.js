// Injected into gemini.google.com by MainActivity every time the custom chat sends a message.
// Types the text into Gemini's input, sends it, waits for the reply, and streams the reply text
// back to the custom chat through window.GeminiBridge.
(function () {
  'use strict';

  if (window.__geminiRelay && typeof window.__geminiRelay.cancel === 'function') {
    try { window.__geminiRelay.cancel(); } catch (e) { /* ignore */ }
  }

  // The element the reply is read from. If Gemini's markup changes, edit these.
  var PRIMARY = '.response-container-has-multiple-responses';
  var FALLBACK = 'model-response';

  var EDITOR_SELECTORS = [
    '.text-input-field_textarea [contenteditable="true"]',
    'rich-textarea [contenteditable="true"]',
    '.ql-editor[contenteditable="true"]',
    '[contenteditable="true"]',
    '.text-input-field_textarea textarea',
    'textarea'
  ];
  var SEND_SELECTORS = 'button[aria-label*="Send"], button.send-button, .send-button';
  var STOP_SELECTOR = 'button[aria-label*="Stop" i]';

  var runId = 0;

  function bridge() { return window.GeminiBridge; }
  function update(text, done) { try { bridge().onUpdate(text, done); } catch (e) { /* ignore */ } }
  function fail(msg) { try { bridge().onError(msg); } catch (e) { /* ignore */ } }
  function sleep(ms) { return new Promise(function (r) { setTimeout(r, ms); }); }

  // ------------------------------------------------------------ input / send

  function findEditor() {
    for (var i = 0; i < EDITOR_SELECTORS.length; i++) {
      var el = document.querySelector(EDITOR_SELECTORS[i]);
      if (el) return el;
    }
    return null;
  }

  function findSendButton() {
    var nodes = document.querySelectorAll(SEND_SELECTORS);
    for (var i = 0; i < nodes.length; i++) {
      var b = nodes[i].tagName === 'BUTTON' ? nodes[i] : (nodes[i].querySelector('button') || nodes[i]);
      var label = (b.getAttribute('aria-label') || '').toLowerCase();
      if (label.indexOf('stop') !== -1) continue;
      if (b.disabled || b.getAttribute('aria-disabled') === 'true') continue;
      return b;
    }
    return null;
  }

  function isGenerating() { return !!document.querySelector(STOP_SELECTOR); }

  function setText(editor, text) {
    editor.focus();
    if (editor.isContentEditable) {
      var sel = window.getSelection();
      var range = document.createRange();
      range.selectNodeContents(editor);
      sel.removeAllRanges();
      sel.addRange(range);
      var ok = false;
      try { ok = document.execCommand('insertText', false, text); } catch (e) { ok = false; }
      if (!ok) {
        editor.textContent = text;
        editor.dispatchEvent(new InputEvent('input', { bubbles: true, inputType: 'insertText', data: text }));
      }
    } else {
      var proto = editor.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
      Object.getOwnPropertyDescriptor(proto, 'value').set.call(editor, text);
      editor.dispatchEvent(new Event('input', { bubbles: true }));
    }
  }

  function pressEnter(editor) {
    ['keydown', 'keypress', 'keyup'].forEach(function (type) {
      editor.dispatchEvent(new KeyboardEvent(type, {
        key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true, cancelable: true
      }));
    });
  }

  // ------------------------------------------------- reply -> markdown text

  var SKIP = {
    BUTTON: 1, STYLE: 1, SCRIPT: 1, SVG: 1, NOSCRIPT: 1, 'MAT-ICON': 1,
    'SOURCE-FOOTNOTE': 1, 'SOURCES-CAROUSEL-INLINE': 1, 'SOURCE-INLINE-CHIPS': 1
  };

  function tableMd(tbl) {
    var rows = Array.prototype.slice.call(tbl.querySelectorAll('tr')).map(function (tr) {
      return Array.prototype.slice.call(tr.children)
        .filter(function (c) { return /^(TD|TH)$/i.test(c.tagName); })
        .map(function (c) {
          return walk(c).replace(/\s*\n+\s*/g, ' ').replace(/\|/g, '\\|').trim();
        });
    }).filter(function (r) { return r.length; });
    if (!rows.length) return '';
    var w = Math.max.apply(null, rows.map(function (r) { return r.length; }));
    function line(r) {
      var cells = [];
      for (var i = 0; i < w; i++) cells.push(r[i] || '');
      return '| ' + cells.join(' | ') + ' |';
    }
    var sep = [];
    for (var i = 0; i < w; i++) sep.push('---');
    var out = [line(rows[0]), '| ' + sep.join(' | ') + ' |'];
    rows.slice(1).forEach(function (r) { out.push(line(r)); });
    return '\n\n' + out.join('\n') + '\n\n';
  }

  function walk(n) {
    if (n.nodeType === 3) return n.nodeValue.replace(/\s+/g, ' ');
    if (n.nodeType !== 1) return '';
    var t = n.tagName.toUpperCase();
    if (SKIP[t]) return '';

    if (n.hasAttribute('data-math')) {
      var m = n.getAttribute('data-math');
      return n.classList.contains('math-block') ? '\n\n$$' + m + '$$\n\n' : '$' + m + '$';
    }

    function kids() {
      return Array.prototype.map.call(n.childNodes, walk).join('');
    }

    switch (t) {
      case 'CODE-BLOCK': {
        var pre = n.querySelector('pre');
        if (!pre) return kids();
        var langEl = n.querySelector('.code-block-decoration > span');
        var lang = langEl ? langEl.textContent.trim().toLowerCase() : '';
        if (!/^[\w+#-]+$/.test(lang)) lang = '';
        return '\n\n```' + lang + '\n' + pre.textContent.replace(/\n+$/, '') + '\n```\n\n';
      }
      case 'PRE':
        return '\n\n```\n' + n.textContent.replace(/\n+$/, '') + '\n```\n\n';
      case 'H1': case 'H2': case 'H3': case 'H4': case 'H5': case 'H6':
        return '\n\n' + new Array(Number(t.charAt(1)) + 1).join('#') + ' ' + kids().trim() + '\n\n';
      case 'P':
        return '\n\n' + kids().trim() + '\n\n';
      case 'BR':
        return '\n';
      case 'HR':
        return '\n\n---\n\n';
      case 'STRONG': case 'B': {
        var b = kids().trim();
        return b ? '**' + b + '**' : '';
      }
      case 'EM': case 'I': {
        var e = kids().trim();
        return e ? '*' + e + '*' : '';
      }
      case 'CODE':
        return '`' + n.textContent + '`';
      case 'A': {
        var k = kids().trim();
        var href = n.getAttribute('href') || '';
        return (/^https?:/.test(href) && k) ? '[' + k + '](' + href + ')' : k;
      }
      case 'UL': case 'OL': {
        var out = '\n';
        var idx = 0;
        Array.prototype.forEach.call(n.children, function (li) {
          if (li.tagName.toUpperCase() !== 'LI') return;
          idx++;
          var body = walk(li).trim().replace(/\n/g, '\n  ');
          out += (t === 'OL' ? idx + '. ' : '- ') + body + '\n';
        });
        return out + '\n';
      }
      case 'BLOCKQUOTE':
        return '\n\n' + kids().trim().split('\n').map(function (l) { return '> ' + l; }).join('\n') + '\n\n';
      case 'TABLE':
        return tableMd(n);
      case 'DIV': case 'SECTION': case 'LI':
        return '\n' + kids() + '\n';
      default:
        return kids();
    }
  }

  function toMarkdown(root) {
    return walk(root)
      .replace(/^[ \t]+$/gm, '')
      .replace(/^ (?=\S)/gm, '')
      .replace(/\n{3,}/g, '\n\n')
      .trim();
  }

  function extract(el) {
    var root = el.querySelector('message-content') || el;
    var out = '';
    try { out = toMarkdown(root); } catch (e) { out = ''; }
    if (!out) out = (root.innerText || '').trim();
    return out;
  }

  // ------------------------------------------------------------------- main

  async function send(text) {
    var id = ++runId;
    function alive() { return id === runId; }

    try {
      // 1. Find Gemini's input box (give the page up to 15 s to be ready).
      var editor = null;
      var t0 = Date.now();
      while (!(editor = findEditor())) {
        if (!alive()) return;
        if (Date.now() - t0 > 15000) {
          fail('Could not find the Gemini input box. Open the Gemini tab, sign in, and try again.');
          return;
        }
        await sleep(300);
      }

      // 2. If Gemini is still answering something else, let it finish first.
      var tg = Date.now();
      while (isGenerating() && Date.now() - tg < 60000) {
        if (!alive()) return;
        await sleep(400);
      }

      var base = {
        primary: document.querySelectorAll(PRIMARY).length,
        fallback: document.querySelectorAll(FALLBACK).length
      };

      // 3. Type the text and press send.
      setText(editor, text);
      var btn = null;
      var t1 = Date.now();
      while (!(btn = findSendButton())) {
        if (!alive()) return;
        if (Date.now() - t1 > 5000) break;
        await sleep(150);
      }
      if (btn) btn.click(); else pressEnter(editor);

      // 4. Wait for the new reply, stream it, and finish once it stops changing.
      var tStart = Date.now();
      var last = '';
      var lastChange = Date.now();
      while (true) {
        await sleep(400);
        if (!alive()) return;
        if (Date.now() - tStart > 240000) {
          fail('Timed out waiting for Gemini to reply.');
          return;
        }

        var p = document.querySelectorAll(PRIMARY);
        var f = document.querySelectorAll(FALLBACK);
        var el = null;
        var viaPrimary = false;
        if (p.length > base.primary) { el = p[p.length - 1]; viaPrimary = true; }
        else if (f.length > base.fallback) { el = f[f.length - 1]; }
        if (!el) continue;

        var txt = extract(el);
        if (txt !== last) {
          last = txt;
          lastChange = Date.now();
          if (txt) update(txt, false);
        }
        var quiet = Date.now() - lastChange;
        if (txt && !isGenerating() && quiet >= (viaPrimary ? 1200 : 2500)) {
          update(txt, true);
          return;
        }
      }
    } catch (err) {
      fail('Relay error: ' + (err && err.message ? err.message : err));
    }
  }

  window.__geminiRelay = {
    send: send,
    cancel: function () { runId++; }
  };
})();
