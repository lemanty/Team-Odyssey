(() => {
    const hero = document.querySelector('[data-hero-motion]');
    const art = hero?.querySelector('.hero-art');
    const original = hero?.querySelector('.hero-voyage');
    const canvas = hero?.querySelector('.hero-scene');
    if (!art || !original || !canvas || !window.matchMedia || !window.IntersectionObserver) return;
    const context = canvas.getContext('2d');
    if (!context || !window.Path2D) return;
    const preference = window.matchMedia('(prefers-reduced-motion: reduce)');
    const sea = new Image();
    let ready = false, inView = false, suspended = false, printing = false;
    let frame = 0, lastTime = null, elapsed = 0, width = 0, height = 0, ratio = 1;
    // All cycles divide 24 seconds so the scene loops without a jump.
    const loop = 24;
    const water = new Path2D('M500 510H904V690Q880 740 884 870L501 820L486 798L564 753L545 729L490 700L439 680L387 663L362 641L350 615L329 590L368 581L428 579L499 570Z');
    const boat = new Path2D('M744 532L746 535L747 542Q760 562 768 589L771 593L768 599L758 602L732 600L722 596L720 591L728 591L739 549L742 539L743 535Z');

    function resize() {
        const rect = art.getBoundingClientRect();
        width = rect.width; height = rect.height;
        ratio = Math.min(window.devicePixelRatio || 1, 1.5);
        canvas.width = Math.round(width * ratio);
        canvas.height = Math.round(height * ratio);
        if (ready && width && height) paint();
    }
    function paint() {
        if (!width || !height) return;
        const scale = Math.max(width / 1254, height / 1254);
        // Match the existing photograph's centre / 54% cover positioning.
        const offsetX = (width - 1254 * scale) / 2;
        const offsetY = (height - 1254 * scale) * .54;
        context.setTransform(ratio, 0, 0, ratio, 0, 0);
        context.clearRect(0, 0, width, height);
        context.translate(offsetX, offsetY);
        context.scale(scale, scale);
        context.drawImage(sea, 0, 0, 1254, 1254);

        // Refract only the water. The architecture, pottery and shoreline stay still.
        context.save();
        context.clip(water);
        for (let y = 510; y < 874; y += 4) {
            const depth = Math.min(1, (y - 510) / 200);
            const wave = Math.sin(y * .105 - elapsed * Math.PI / 3);
            const swell = Math.sin(y * .037 + elapsed * Math.PI / 4);
            const dx = (wave * 2.8 + swell * 1.8) * depth;
            const dy = Math.sin(y * .08 - elapsed * Math.PI / 6) * 1.5 * depth;
            context.drawImage(sea, 300, y + dy, 620, 4, 300 + dx, y, 620, 4);
        }
        context.restore();

        const travel = 32 * Math.sin(elapsed * Math.PI / 12);
        const bob = 2.2 * Math.sin(elapsed * Math.PI / 2);
        const roll = .018 * Math.sin(elapsed * Math.PI / 2);
        // Warm, rippling reflection follows the boat instead of remaining on the plate.
        context.save();
        context.clip(water);
        for (let i = 0; i < 20; i++) {
            const shimmer = Math.sin(i * 1.8 + elapsed * Math.PI / 3);
            context.fillStyle = `rgba(244,215,149,${(1-i/20)*.16})`;
            context.fillRect(740 + travel + shimmer*3 - i*.12, 603 + i*2.7,
                9 + shimmer*3 + i*.24, 1.5);
        }
        context.restore();
        context.save();
        context.translate(746 + travel, 596 + bob);
        context.rotate(roll);
        context.translate(-746, -596);
        context.clip(boat);
        context.drawImage(original, 0, 0, 1254, 1254);
        context.restore();
    }
    function tick(now) {
        frame = 0;
        if (lastTime !== null) elapsed = (elapsed + Math.min((now-lastTime)/1000, .05)) % loop;
        lastTime = now;
        paint();
        frame = requestAnimationFrame(tick);
    }
    function sync() {
        const running = ready && inView && !document.hidden && !preference.matches && !suspended && !printing;
        hero.dataset.motionState = running ? 'running' : 'paused';
        canvas.hidden = !ready || preference.matches || printing;
        if (running && !frame) {
            lastTime = null;
            frame = requestAnimationFrame(tick);
        } else if (!running) {
            cancelAnimationFrame(frame);
            frame = 0; lastTime = null;
        }
    }
    const observer = new IntersectionObserver(entries => {
        for (const entry of entries) if (entry.target === art) inView = entry.isIntersecting;
        sync();
    });
    observer.observe(art);
    if (window.ResizeObserver) new ResizeObserver(resize).observe(art);
    else window.addEventListener('resize', resize, {passive:true});
    preference.addEventListener('change', sync);
    document.addEventListener('visibilitychange', sync);
    window.addEventListener('pagehide', () => { suspended = true; sync(); });
    window.addEventListener('pageshow', () => { suspended = false; sync(); });
    window.addEventListener('beforeprint', () => { printing = true; sync(); });
    window.addEventListener('afterprint', () => { printing = false; sync(); });
    sea.src = canvas.dataset.seaSrc;
    Promise.all([sea.decode(), original.decode()]).then(() => {
        ready = true;
        hero.dataset.motionReady = 'true';
        resize();
        sync();
    }).catch(() => {
        // Missing artwork must never leave an empty hero.
        ready = false;
        sync();
    });
})();
