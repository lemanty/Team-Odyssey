(() => {
  const nav = document.querySelector('.site-header nav');
  if (!nav) return;
  const links = [...nav.children].filter(node => node.matches('a'));
  if (!links.length) return;
  const highlight = document.createElement('span');
  highlight.className = 'nav-highlight no-motion';
  highlight.setAttribute('aria-hidden', 'true');
  nav.prepend(highlight);
  nav.classList.add('nav-enhanced');
  let hovered = null;
  let focused = null;
  let returnTimer = null;
  function cancelReturn() {
    clearTimeout(returnTimer);
    returnTimer = null;
  }
  function scheduleReturn() {
    if (returnTimer !== null || !hovered) return;
    returnTimer = setTimeout(() => {
      returnTimer = null;
      hovered = null;
      draw();
    }, 500);
  }
  function current() {
    if (location.hash === '#our-story') {
      const story = links.find(link => {
        const url = new URL(link.href);
        return url.pathname === location.pathname && url.hash === '#our-story';
      });
      if (story) return story;
    }
    return links.find(link => link.getAttribute('aria-current') === 'page');
  }
  function draw() {
    const target = hovered || focused || current();
    if (!target) { highlight.style.opacity = '0'; return; }
    const box = target.getBoundingClientRect();
    const parent = nav.getBoundingClientRect();
    highlight.style.width = `${box.width + 16}px`;
    highlight.style.height = `${box.height}px`;
    highlight.style.transform = `translate(${box.left - parent.left - 8}px, ${box.top - parent.top}px)`;
    highlight.style.opacity = '1';
  }
  links.forEach(link => {
    link.addEventListener('pointerenter', event => {
      if (event.pointerType === 'touch') return;
      cancelReturn();
      hovered = link;
      draw();
    });
    link.addEventListener('pointerleave', scheduleReturn);
    link.addEventListener('focus', () => { cancelReturn(); hovered = null; focused = link; draw(); });
    link.addEventListener('blur', () => { focused = null; draw(); });
  });
  nav.addEventListener('pointerleave', scheduleReturn);
  const language = nav.querySelector('.home-language-switch');
  language?.addEventListener('pointerenter', scheduleReturn);
  window.addEventListener('hashchange', draw);
  const observer = new ResizeObserver(draw);
  observer.observe(nav);
  links.forEach(link => observer.observe(link));
  document.fonts?.ready.then(draw);
  draw();
  requestAnimationFrame(() => requestAnimationFrame(() => highlight.classList.remove('no-motion')));
})();
