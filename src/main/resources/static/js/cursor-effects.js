(() => {
    const motion = matchMedia('(prefers-reduced-motion: reduce)');
    const mouse = matchMedia('(hover: hover) and (pointer: fine)');
    const controls = 'button,a.button,a.cart-button,a.order-button,a.member-link,a.offer-action,[role="button"]';
    const trail = document.createElement('canvas');
    trail.className = 'cursor-trail';
    trail.setAttribute('aria-hidden', 'true');
    document.body.append(trail);
    const context = trail.getContext('2d');
    const lifetime = 240, maxLength = 130;
    let frame = 0, card = null, points = [];
    function resize() {
        const ratio = Math.min(window.devicePixelRatio || 1, 2);
        trail.width = Math.round(innerWidth * ratio);
        trail.height = Math.round(innerHeight * ratio);
        context?.setTransform(ratio, 0, 0, ratio, 0, 0);
        points = [];
    }
    resize();
    window.addEventListener('resize', resize, {passive:true});
    const ripples = new Set();
    const enabled = () => mouse.matches && !motion.matches && !document.hidden;

    function resetCard() {
        if (!card) return;
        card.classList.remove('cursor-card-active');
        card.style.removeProperty('--cursor-x');
        card.style.removeProperty('--cursor-y');
        card = null;
    }
    function stop() {
        cancelAnimationFrame(frame);
        frame = 0;
        points = [];
        context?.clearRect(0, 0, innerWidth, innerHeight);
        resetCard();
        ripples.forEach(node => node.remove());
        ripples.clear();
    }
    function paint(now) {
        if (!context) return;
        points = points.filter(point => now - point.time < lifetime);
        context.clearRect(0, 0, innerWidth, innerHeight);
        context.lineCap = 'round';
        let distance = 0;
        // Draw from the exact cursor position back through its recent path.
        for (let i = points.length - 1; i > 0; i--) {
            const head = points[i], tail = points[i - 1];
            const length = Math.hypot(head.x - tail.x, head.y - tail.y);
            distance += length;
            if (distance > maxLength) { points = points.slice(i); break; }
            const age = Math.max(0, 1 - (now - tail.time) / lifetime);
            const taper = age * Math.pow(1 - distance / maxLength, .7);
            context.beginPath();
            context.moveTo(tail.x, tail.y);
            context.lineTo(head.x, head.y);
            context.lineWidth = Math.max(.15, 5 * taper);
            context.strokeStyle = `rgba(93,151,122,${.48 * taper})`;
            context.shadowColor = 'rgba(135,198,160,.3)';
            context.shadowBlur = 5 * taper;
            context.stroke();
            context.shadowBlur = 0;
            context.lineWidth = Math.max(.1, 1.4 * taper);
            context.strokeStyle = `rgba(205,235,211,${.85 * taper})`;
            context.stroke();
        }
    }
    function draw(now) {
        paint(now);
        frame = points.length ? requestAnimationFrame(draw) : 0;
    }
    document.addEventListener('pointermove', event => {
        if (!enabled() || event.pointerType !== 'mouse') { stop(); return; }
        const now = performance.now();
        const previous = points[points.length - 1];
        const distance = previous ? Math.hypot(event.clientX - previous.x, event.clientY - previous.y) : 0;
        // Small samples keep the taper smooth even during fast mouse movements.
        const steps = Math.max(1, Math.min(64, Math.ceil(distance / 3)));
        if (previous && distance > 400) points = [];
        for (let step = 1; step <= steps; step++) {
            const part = step / steps;
            points.push({
                x: previous && distance <= 400 ? previous.x + (event.clientX - previous.x) * part : event.clientX,
                y: previous && distance <= 400 ? previous.y + (event.clientY - previous.y) * part : event.clientY,
                time: previous && distance <= 400 ? previous.time + (now - previous.time) * part : now
            });
        }
        points = points.slice(-160);
        paint(now);
        if (!frame && context) frame = requestAnimationFrame(draw);
        const next = event.target.closest('.product-card');
        if (card !== next) { resetCard(); card = next; }
        if (card) {
            const bounds = card.getBoundingClientRect();
            card.classList.add('cursor-card-active');
            card.style.setProperty('--cursor-x', `${((event.clientX-bounds.left)/bounds.width-.5)*5}px`);
            card.style.setProperty('--cursor-y', `${((event.clientY-bounds.top)/bounds.height-.5)*5}px`);
        }
    }, {passive:true});
    document.addEventListener('pointerdown', event => {
        if (!enabled() || event.pointerType !== 'mouse' || event.button !== 0) return;
        const button = event.target.closest(controls);
        if (!button || button.matches(':disabled,[aria-disabled="true"]') || button.closest('[inert]')) return;
        const bounds = button.getBoundingClientRect();
        const layer = document.createElement('div');
        layer.className = 'cursor-ripple-clip';
        layer.setAttribute('aria-hidden', 'true');
        Object.assign(layer.style, {
            left:`${bounds.left}px`, top:`${bounds.top}px`,
            width:`${bounds.width}px`, height:`${bounds.height}px`,
            borderRadius:getComputedStyle(button).borderRadius
        });
        const wave = document.createElement('span');
        const size = Math.hypot(bounds.width, bounds.height)*2;
        Object.assign(wave.style, {
            width:`${size}px`, height:`${size}px`,
            left:`${event.clientX-bounds.left-size/2}px`,
            top:`${event.clientY-bounds.top-size/2}px`
        });
        layer.append(wave);
        document.body.append(layer);
        ripples.add(layer);
        setTimeout(() => { layer.remove(); ripples.delete(layer); }, 600);
    }, {passive:true});
    document.documentElement.addEventListener('pointerleave', stop);
    window.addEventListener('blur', stop);
    window.addEventListener('scroll', stop, {passive:true, capture:true});
    window.addEventListener('pagehide', stop);
    window.addEventListener('beforeprint', stop);
    document.addEventListener('visibilitychange', stop);
    motion.addEventListener('change', stop);
    mouse.addEventListener('change', stop);
})();
