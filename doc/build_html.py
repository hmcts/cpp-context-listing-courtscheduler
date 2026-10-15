#!/usr/bin/env python3
"""Build doc/COURT_SCHEDULER_GUIDE.html from doc/COURT_SCHEDULER_GUIDE.md.

The Markdown is embedded verbatim and rendered in the browser with marked + mermaid,
so the HTML always matches the Markdown source. Re-run after editing the .md file:  python3 doc/build_html.py
"""
import sys
from pathlib import Path

here = Path(__file__).resolve().parent
src = Path(sys.argv[1]) if len(sys.argv) > 1 else here / "COURT_SCHEDULER_GUIDE.md"
out = Path(sys.argv[2]) if len(sys.argv) > 2 else here / "COURT_SCHEDULER_GUIDE.html"
md = src.read_text(encoding="utf-8")
# Protect the embedding <script> block.
md_embedded = md.replace("</script", "<\\/script")

TEMPLATE = r"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Court Scheduler Guide</title>
<meta name="description" content="Application guide for the HMCTS Court Scheduler service: business rules, architecture, API, data model, QA and operations.">
<style>
:root {
  --bg: #ffffff; --bg-soft: #f6f7f9; --bg-code: #f2f4f7; --fg: #1d2330; --fg-muted: #5b6475;
  --border: #dde1e8; --accent: #1d5fbf; --accent-soft: #e8f0fc; --sidebar-w: 300px;
}
@media (prefers-color-scheme: dark) {
  :root:not([data-theme="light"]) {
    --bg: #12151b; --bg-soft: #1a1e26; --bg-code: #1f2430; --fg: #e3e7ee; --fg-muted: #9aa3b2;
    --border: #2c3240; --accent: #7fb0ff; --accent-soft: #1d2a40;
  }
}
:root[data-theme="dark"] {
  --bg: #12151b; --bg-soft: #1a1e26; --bg-code: #1f2430; --fg: #e3e7ee; --fg-muted: #9aa3b2;
  --border: #2c3240; --accent: #7fb0ff; --accent-soft: #1d2a40;
}
* { box-sizing: border-box; }
html { scroll-behavior: smooth; }
body {
  margin: 0; background: var(--bg); color: var(--fg);
  font: 16px/1.6 -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
}
header.top {
  position: sticky; top: 0; z-index: 10; display: flex; align-items: center; gap: 12px;
  padding: 10px 16px; background: var(--bg-soft); border-bottom: 1px solid var(--border);
}
header.top .title { font-weight: 600; flex: 1; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
header.top button {
  background: var(--bg); color: var(--fg); border: 1px solid var(--border); border-radius: 6px;
  padding: 6px 10px; cursor: pointer; font: inherit; font-size: 14px;
}
#menuBtn { display: none; }
.layout { display: flex; }
nav.toc {
  position: sticky; top: 49px; align-self: flex-start; width: var(--sidebar-w); flex: 0 0 var(--sidebar-w);
  height: calc(100vh - 49px); overflow-y: auto; padding: 16px; border-right: 1px solid var(--border);
  background: var(--bg-soft); font-size: 14px;
}
nav.toc input {
  width: 100%; padding: 6px 8px; margin-bottom: 10px; border: 1px solid var(--border); border-radius: 6px;
  background: var(--bg); color: var(--fg); font: inherit;
}
nav.toc ol { list-style: none; margin: 0; padding: 0; }
nav.toc li a {
  display: block; padding: 4px 8px; border-radius: 5px; color: var(--fg); text-decoration: none;
}
nav.toc li.sub a { padding-left: 22px; color: var(--fg-muted); font-size: 13px; }
nav.toc li a:hover { background: var(--accent-soft); }
nav.toc li a.active { background: var(--accent-soft); color: var(--accent); font-weight: 600; }
main { flex: 1; min-width: 0; padding: 24px 40px 80px; max-width: 1100px; }
main h1 { font-size: 2rem; margin-top: 0; }
main h2 { margin-top: 2.6rem; padding-top: .4rem; border-top: 1px solid var(--border); scroll-margin-top: 60px; }
main h3, main h4 { scroll-margin-top: 60px; }
a { color: var(--accent); }
blockquote {
  margin: 1rem 0; padding: .6rem 1rem; border-left: 4px solid var(--accent);
  background: var(--accent-soft); border-radius: 0 6px 6px 0;
}
blockquote p { margin: .3rem 0; }
code { background: var(--bg-code); padding: .1em .35em; border-radius: 4px; font-size: .9em;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; overflow-wrap: anywhere; }
pre { background: var(--bg-code); padding: 12px 14px; border-radius: 8px; overflow-x: auto; }
pre code { background: none; padding: 0; overflow-wrap: normal; }
.table-wrap { overflow-x: auto; margin: 1rem 0; }
table { border-collapse: collapse; width: 100%; font-size: 14.5px; }
th, td { border: 1px solid var(--border); padding: 7px 10px; text-align: left; vertical-align: top; }
th { background: var(--bg-soft); }
tr:nth-child(even) td { background: color-mix(in srgb, var(--bg-soft) 50%, transparent); }
.diagram {
  margin: 1.2rem 0; padding: 14px; border: 1px solid var(--border); border-radius: 10px;
  background: var(--bg); overflow-x: auto; text-align: center;
}
.diagram pre.mermaid { background: none; margin: 0; padding: 0; }
.diagram svg { max-width: 100%; height: auto; }
.footer { margin-top: 3rem; color: var(--fg-muted); font-size: 13px; }
#toTop {
  position: fixed; right: 18px; bottom: 18px; display: none; border-radius: 50%; width: 42px; height: 42px;
  border: 1px solid var(--border); background: var(--bg-soft); color: var(--fg); cursor: pointer; font-size: 18px;
}
@media (max-width: 900px) {
  #menuBtn { display: inline-block; }
  nav.toc { position: fixed; left: 0; top: 49px; z-index: 9; transform: translateX(-100%); transition: transform .2s; width: 85vw; max-width: 320px; }
  nav.toc.open { transform: none; box-shadow: 0 0 24px rgba(0,0,0,.25); }
  main { padding: 16px 16px 60px; }
}
@media print {
  header.top, nav.toc, #toTop { display: none !important; }
  main { padding: 0; max-width: none; }
  main h2 { page-break-before: always; }
  .diagram { page-break-inside: avoid; }
}
</style>
</head>
<body>
<header class="top">
  <button id="menuBtn" aria-label="Toggle contents">☰</button>
  <span class="title">Court Scheduler Service: Application Guide</span>
  <button id="themeBtn" aria-label="Toggle theme">◐ Theme</button>
  <button onclick="window.print()" aria-label="Print">⎙ Print</button>
</header>
<div class="layout">
  <nav class="toc" id="toc">
    <input id="tocFilter" type="search" placeholder="Filter sections…" aria-label="Filter sections">
    <ol id="tocList"></ol>
  </nav>
  <main id="content"><p>Loading…</p></main>
</div>
<button id="toTop" aria-label="Back to top">↑</button>

<script type="text/markdown" id="md-source">
__MARKDOWN__
</script>
<script src="https://cdn.jsdelivr.net/npm/marked@12.0.2/marked.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/mermaid@10.9.1/dist/mermaid.min.js"></script>
<script>
(function () {
  const root = document.documentElement;
  let stored = null;
  try { stored = localStorage.getItem('csg-theme'); } catch (e) {}
  if (stored) root.setAttribute('data-theme', stored);

  const isDark = () => {
    const t = root.getAttribute('data-theme');
    if (t) return t === 'dark';
    return window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
  };

  const escapeHtml = s => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  const slug = text => text.trim().toLowerCase()
      .replace(/<[^>]+>/g, '')
      .replace(/[^\p{L}\p{N}\s-]/gu, '')
      .replace(/\s/g, '-');

  const mdText = document.getElementById('md-source').textContent.replace(/<\\\/script/g, '</script');
  const diagrams = [];

  const renderer = new marked.Renderer();
  const baseCode = renderer.code.bind(renderer);
  renderer.code = function (code, lang, escaped) {
    if ((lang || '').trim() === 'mermaid') {
      diagrams.push(code);
      return '<div class="diagram"><pre class="mermaid" data-idx="' + (diagrams.length - 1) + '">' +
             escapeHtml(code) + '</pre></div>';
    }
    return baseCode(code, lang, escaped);
  };
  renderer.table = function (header, body) {
    return '<div class="table-wrap"><table><thead>' + header + '</thead><tbody>' + body + '</tbody></table></div>';
  };

  const content = document.getElementById('content');
  content.innerHTML = marked.parse(mdText, { renderer: renderer, gfm: true });
  content.insertAdjacentHTML('beforeend',
    '<p class="footer">Generated from <code>doc/COURT_SCHEDULER_GUIDE.md</code>. Edit the Markdown, then run <code>python3 doc/build_html.py</code> to regenerate this file.</p>');

  // Heading ids (GitHub-style) and TOC.
  const tocList = document.getElementById('tocList');
  const used = {};
  content.querySelectorAll('h1, h2, h3').forEach(h => {
    let id = slug(h.textContent);
    if (used[id] !== undefined) { used[id] += 1; id = id + '-' + used[id]; } else { used[id] = 0; }
    h.id = id;
    if (h.tagName === 'H1') return;
    const li = document.createElement('li');
    if (h.tagName === 'H3') li.className = 'sub';
    const a = document.createElement('a');
    a.href = '#' + id; a.textContent = h.textContent;
    li.appendChild(a); tocList.appendChild(li);
  });

  // TOC filter.
  document.getElementById('tocFilter').addEventListener('input', e => {
    const q = e.target.value.toLowerCase();
    tocList.querySelectorAll('li').forEach(li => {
      li.style.display = li.textContent.toLowerCase().includes(q) ? '' : 'none';
    });
  });

  // Active section highlighting.
  const links = {};
  tocList.querySelectorAll('a').forEach(a => { links[a.getAttribute('href').slice(1)] = a; });
  const obs = new IntersectionObserver(entries => {
    entries.forEach(en => {
      if (en.isIntersecting && links[en.target.id]) {
        Object.values(links).forEach(l => l.classList.remove('active'));
        links[en.target.id].classList.add('active');
      }
    });
  }, { rootMargin: '-60px 0px -70% 0px' });
  content.querySelectorAll('h2, h3').forEach(h => obs.observe(h));

  // Mobile menu.
  const toc = document.getElementById('toc');
  document.getElementById('menuBtn').addEventListener('click', () => toc.classList.toggle('open'));
  tocList.addEventListener('click', e => { if (e.target.tagName === 'A') toc.classList.remove('open'); });

  // Back to top.
  const toTop = document.getElementById('toTop');
  window.addEventListener('scroll', () => { toTop.style.display = window.scrollY > 600 ? 'block' : 'none'; });
  toTop.addEventListener('click', () => window.scrollTo({ top: 0 }));

  // Mermaid rendering (re-rendered on theme change).
  async function renderDiagrams() {
    content.querySelectorAll('pre.mermaid').forEach(el => {
      el.removeAttribute('data-processed');
      el.innerHTML = escapeHtml(diagrams[+el.dataset.idx]);
    });
    mermaid.initialize({ startOnLoad: false, theme: isDark() ? 'dark' : 'default', securityLevel: 'strict',
                         flowchart: { htmlLabels: true, useMaxWidth: true } });
    try { await mermaid.run({ querySelector: 'pre.mermaid' }); } catch (e) { console.error(e); }
  }
  renderDiagrams();

  document.getElementById('themeBtn').addEventListener('click', () => {
    const next = isDark() ? 'light' : 'dark';
    root.setAttribute('data-theme', next);
    try { localStorage.setItem('csg-theme', next); } catch (e) {}
    renderDiagrams();
  });

  if (location.hash) {
    const t = document.getElementById(decodeURIComponent(location.hash.slice(1)));
    if (t) setTimeout(() => t.scrollIntoView(), 50);
  }
})();
</script>
</body>
</html>
"""

out.write_text(TEMPLATE.replace("__MARKDOWN__", md_embedded), encoding="utf-8")
print(f"Wrote {out} ({out.stat().st_size // 1024} KB)")
