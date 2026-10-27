(() => {
    const motion = matchMedia('(prefers-reduced-motion: reduce)');
    const mouse = matchMedia('(hover: hover) and (pointer: fine)');
    const controls = 'button,a.button,a.cart-button,a.order-button,a.member-link,a.offer-action,[role="button"]';
    const trail = document.createElement('canvas');
    trail.className = 'cursor-trail';
    trail.setAttribute('aria-hidden', 'true');
    document.body.append(trail);
    const context = trail.getContext('2d');
    const lifetime = 300, maxLength = 145;
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
        if (points.length < 2) return;
        // Smooth the recorded path, without delaying the newest cursor position.
        const spine = [points[0]];
        for (let i = 1; i < points.length - 1; i++) {
            const start = spine[spine.length - 1], control = points[i], next = points[i + 1];
            const end = {x:(control.x+next.x)/2, y:(control.y+next.y)/2,
                time:(control.time+next.time)/2};
            for (let step = 1; step <= 3; step++) {
                const t = step/3, u = 1-t;
                spine.push({x:u*u*start.x+2*u*t*control.x+t*t*end.x,
                    y:u*u*start.y+2*u*t*control.y+t*t*end.y,
                    time:start.time+(end.time-start.time)*t});
            }
        }
        spine.push(points[points.length-1]);
        const ribbon = [];
        let distance = 0;
        for (let i = spine.length-1; i >= 0; i--) {
            const point = spine[i], newer = spine[Math.min(i+1,spine.length-1)];
            distance += Math.hypot(point.x-newer.x,point.y-newer.y);
            if (distance >= maxLength) break;
            const older = spine[Math.max(0,i-1)];
            const dx = newer.x-older.x, dy = newer.y-older.y;
            const length = Math.hypot(dx,dy) || 1;
            const age = Math.max(0,1-(now-point.time)/lifetime);
            const freshness = age*age*(3-2*age);
            ribbon.push({x:point.x,y:point.y,nx:-dy/length,ny:dx/length,
                width:2.6*freshness*Math.pow(1-distance/maxLength,.8)});
        }
        if (ribbon.length < 2) return;
        const head = ribbon[0], tail = ribbon[ribbon.length-1];
        const fade = Math.max(0,1-(now-points[points.length-1].time)/lifetime);
        // Three continuous ribbons replace dozens of expensive blurred strokes.
        for (const [scale, color, alpha] of [[2.1,'135,198,160',.08],[1,'93,151,122',.42],[.3,'216,241,220',.75]]) {
            const gradient = context.createLinearGradient(head.x,head.y,tail.x,tail.y);
            gradient.addColorStop(0, `rgba(${color},${alpha*fade})`);
            gradient.addColorStop(1, `rgba(${color},0)`);
            context.fillStyle = gradient;
            context.beginPath();
            ribbon.forEach((point,i) => {
                const x = point.x+point.nx*point.width*scale;
                const y = point.y+point.ny*point.width*scale;
                if (i === 0) context.moveTo(x,y); else context.lineTo(x,y);
            });
            for (let i = ribbon.length-1; i >= 0; i--) {
                const point = ribbon[i];
                context.lineTo(point.x-point.nx*point.width*scale,point.y-point.ny*point.width*scale);
            }
            context.closePath();
            context.fill();
        }
    }
    function draw(now) {
        paint(now);
        frame = points.length ? requestAnimationFrame(draw) : 0;
    }
    document.addEventListener('pointermove', event => {
        if (!enabled() || event.pointerType !== 'mouse') { stop(); return; }
        const now = performance.now();
        const samples = event.getCoalescedEvents?.() || [];
        for (const sample of samples.length ? samples : [event]) {
            const previous = points[points.length-1];
            const distance = previous ? Math.hypot(sample.clientX-previous.x,sample.clientY-previous.y) : 0;
            if (previous && distance < .7) continue;
            if (distance > 400) points = [];
            points.push({x:sample.clientX,y:sample.clientY,
                time:Math.min(now, Math.max(now-lifetime, sample.timeStamp))});
        }
        points = points.slice(-96);
        // Exactly one paint per display refresh, including on 120/144 Hz screens.
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
