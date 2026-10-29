// Shared by every page: mobile contents menu and the in-view section highlight.
const btn = document.querySelector('.menu'), toc = document.getElementById('toc');
btn.addEventListener('click', () => {
  const open = toc.classList.toggle('open');
  btn.setAttribute('aria-expanded', String(open));
});
toc.addEventListener('click', e => { if (e.target.closest('a')) { toc.classList.remove('open'); btn.setAttribute('aria-expanded', 'false'); } });
// Highlight the section in view.
const links = new Map([...toc.querySelectorAll('a')].map(a => [a.hash.slice(1), a]));
const io = new IntersectionObserver(entries => {
  for (const e of entries) {
    if (!e.isIntersecting) continue;
    links.forEach(a => a.removeAttribute('aria-current'));
    links.get(e.target.id)?.setAttribute('aria-current', 'true');
  }
}, { rootMargin: '-30% 0px -65% 0px' });
document.querySelectorAll('main section[id]').forEach(s => io.observe(s));

// Theme toggle: flips between light and dark and remembers the choice per browser.
const themeBtn = document.querySelector('.theme');
themeBtn?.addEventListener('click', () => {
  const root = document.documentElement;
  const current = root.dataset.theme
    || (matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
  const next = current === 'dark' ? 'light' : 'dark';
  root.dataset.theme = next;
  try { localStorage.setItem('theme', next); } catch (e) { /* storage blocked: theme lasts this page */ }
});
