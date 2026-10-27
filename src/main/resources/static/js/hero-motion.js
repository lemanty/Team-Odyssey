(() => {
    const hero = document.querySelector('[data-hero-motion]');
    const art = hero?.querySelector('.hero-art');
    const button = hero?.querySelector('.hero-motion-toggle');
    const label = button?.querySelector('[data-hero-motion-label]');
    if (!hero || !art || !button || !label || typeof window.matchMedia !== 'function'
            || !('IntersectionObserver' in window)) return;

    const preference = window.matchMedia('(prefers-reduced-motion: reduce)');
    // Older clients stay safely static rather than losing preference changes.
    if (typeof preference.addEventListener !== 'function') return;
    let inView = false;
    let userPaused = false;
    let pageSuspended = false;
    let printing = false;

    function sync() {
        const running = inView && !document.hidden && !preference.matches
            && !userPaused && !pageSuspended && !printing;
        hero.dataset.motionState = running ? 'running' : 'paused';
        button.hidden = preference.matches;
        button.dataset.paused = String(userPaused);
        label.textContent = userPaused ? button.dataset.labelPlay : button.dataset.labelPause;
    }

    const observer = new IntersectionObserver(entries => {
        entries.forEach(entry => {
            if (entry.target === art) inView = entry.isIntersecting && entry.intersectionRatio > 0;
        });
        sync();
    }, { threshold: 0 });

    button.addEventListener('click', () => { userPaused = !userPaused; sync(); });
    preference.addEventListener('change', sync);
    document.addEventListener('visibilitychange', sync);
    window.addEventListener('pagehide', () => { pageSuspended = true; sync(); });
    // Resume from the back/forward cache, retaining an explicit user pause.
    window.addEventListener('pageshow', () => { pageSuspended = false; sync(); });
    window.addEventListener('beforeprint', () => { printing = true; sync(); });
    window.addEventListener('afterprint', () => { printing = false; sync(); });
    observer.observe(art);
    hero.dataset.motionReady = 'true';
    sync();
})();
