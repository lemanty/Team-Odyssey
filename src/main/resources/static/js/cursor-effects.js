(() => {
    const motion = matchMedia('(prefers-reduced-motion: reduce)');
    const mouse = matchMedia('(hover: hover) and (pointer: fine)');
    const controls = 'button,a.button,a.cart-button,a.order-button,a.member-link,a.offer-action,[role="button"]';
    const glow = document.createElement('div');
    glow.className = 'cursor-glow';
    glow.setAttribute('aria-hidden', 'true');
    document.body.append(glow);
    let frame = 0, visible = false, x = 0, y = 0, targetX = 0, targetY = 0, card = null;
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
        visible = false;
        glow.classList.remove('is-visible');
        resetCard();
        ripples.forEach(node => node.remove());
        ripples.clear();
    }
    function draw() {
        x += (targetX - x) * .18;
        y += (targetY - y) * .18;
        glow.style.transform = `translate3d(${x - 48}px,${y - 48}px,0)`;
        frame = Math.abs(targetX - x) + Math.abs(targetY - y) > .15
            ? requestAnimationFrame(draw) : 0;
    }
    document.addEventListener('pointermove', event => {
        if (!enabled() || event.pointerType !== 'mouse') { stop(); return; }
        targetX = event.clientX;
        targetY = event.clientY;
        if (!visible) {
            x = targetX; y = targetY; visible = true;
            glow.classList.add('is-visible');
        }
        if (!frame) frame = requestAnimationFrame(draw);
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
