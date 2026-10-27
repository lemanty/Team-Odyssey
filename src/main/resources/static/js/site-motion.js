(() => {
    const preference = matchMedia('(prefers-reduced-motion: reduce)');
    if (!Element.prototype.animate || !('IntersectionObserver' in window)) return;
    const running = new Map();
    const seen = new WeakSet();
    function stop() {
        running.forEach(animation => animation.cancel());
        running.clear();
    }
    function enter(element, delay = 0, distance = 12) {
        if (preference.matches || document.hidden || element.contains(document.activeElement)) return;
        running.get(element)?.cancel();
        const animation = element.animate([
            {opacity:0, translate:`0 ${distance}px`},
            {opacity:1, translate:'0 0'}
        ], {duration:380, delay, easing:'cubic-bezier(.22,.7,.25,1)', fill:'backwards'});
        running.set(element, animation);
        animation.onfinish = () => { if (running.get(element) === animation) running.delete(element); };
    }
    // Only initial, independent sections. Catalogue and address editor retain their own motion.
    const candidates = [...document.querySelectorAll([
        '.hero-copy','.hero-art','.category-card','.story-heading','.story-art','.story-copy',
        '.catalogue-editorial','.catalogue-filters','.detail-copy','.product-gallery',
        '.account-welcome','.account-tile','.account-settings','.account-workspace',
        '.membership-hero','.member-tier','.membership-calculator','.member-rules',
        '.auth-art','.auth-panel','.cart-line','.cart-summary',
        '.order-card:not(.address-editor)','.address-card','.support-page > h1',
        '.account-page > h1','.order-page > h1'
    ].join(','))].filter(element => !element.closest('[data-hero-motion]') && !element.parentElement.closest(
        '.order-card,.auth-panel,.category-card,.account-tile,.membership-calculator,.support-messages'));
    const observer = new IntersectionObserver(entries => {
        const visible = entries.filter(e => e.isIntersecting).sort((a,b) =>
            Math.abs(a.boundingClientRect.top-b.boundingClientRect.top)>4
                ? a.boundingClientRect.top-b.boundingClientRect.top
                : a.boundingClientRect.left-b.boundingClientRect.left);
        visible.forEach(({target}, index) => {
            observer.unobserve(target);
            if (seen.has(target)) return;
            seen.add(target);
            enter(target, Math.min(index*45,135));
        });
    }, {threshold:0, rootMargin:'0px 0px -12px 0px'});
    if (!preference.matches) candidates.forEach(element => observer.observe(element));
    // Focusing a control should never wait for a decorative reveal.
    document.addEventListener('focusin', event => {
        running.forEach((animation, element) => {
            if (element.contains(event.target)) { animation.cancel(); running.delete(element); }
        });
    });
    document.querySelectorAll('.member-rules details').forEach(details => {
        details.addEventListener('toggle', () => {
            if (details.open) [...details.children].filter(e => e.tagName !== 'SUMMARY').forEach(e => enter(e,0,4));
        });
    });
    preference.addEventListener('change', () => {
        if (preference.matches) { observer.disconnect(); stop(); }
    });
    window.addEventListener('pagehide', () => { observer.disconnect(); stop(); });
    document.addEventListener('visibilitychange', () => { if (document.hidden) stop(); });
    window.addEventListener('beforeprint', () => { observer.disconnect(); stop(); });
})();
