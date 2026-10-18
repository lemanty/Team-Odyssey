(() => {
  const cards = [...document.querySelectorAll('.catalogue .product-card')];
  const motion = window.matchMedia('(prefers-reduced-motion: reduce)');
  if (!cards.length || motion.matches || !('IntersectionObserver' in window) || !Element.prototype.animate) return;
  const animations = new Map();
  function show(card) {
    card.classList.remove('reveal-pending');
    animations.get(card)?.cancel();
    animations.delete(card);
  }
  const observer = new IntersectionObserver(entries => {
    const visible = entries.filter(entry => entry.isIntersecting)
      .map(entry => ({card: entry.target, rect: entry.boundingClientRect}))
      .sort((a, b) => Math.abs(a.rect.top - b.rect.top) > 4
        ? a.rect.top - b.rect.top : a.rect.left - b.rect.left);
    visible.forEach(({card}, index) => {
      observer.unobserve(card);
      card.classList.remove('reveal-pending');
      if (motion.matches || card.contains(document.activeElement)) return;
      const animation = card.animate([
        {opacity: 0, translate: '-6px 18px'},
        {opacity: 1, translate: '0 0'}
      ], {duration: 520, delay: Math.min(index * 65, 325),
          easing: 'cubic-bezier(.22,.7,.25,1)', fill: 'backwards'});
      animations.set(card, animation);
      animation.onfinish = () => animations.delete(card);
    });
  }, {threshold: 0, rootMargin: '0px 0px -20px 0px'});
  cards.forEach(card => {
    card.classList.add('reveal-pending');
    observer.observe(card);
    card.addEventListener('focusin', () => { observer.unobserve(card); show(card); });
  });
  motion.addEventListener('change', () => {
    if (!motion.matches) return;
    observer.disconnect();
    cards.forEach(show);
  });
  window.addEventListener('pageshow', event => {
    if (event.persisted) { observer.disconnect(); cards.forEach(show); }
  });
})();
