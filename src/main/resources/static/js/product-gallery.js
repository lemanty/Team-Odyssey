/* Progressive enhancement: without JavaScript the garment image stays visible. */
document.querySelectorAll('[data-gallery]').forEach(function setupGallery(gallery) {
    const zh = document.documentElement.lang === 'zh-CN';
    const label = (en, cn) => zh ? cn : en;
    const slides = Array.from(gallery.querySelectorAll('[data-slide]'));
    if (slides.length < 2) return;
    const stage = gallery.querySelector('.gallery-stage');
    const tabs = Array.from(gallery.querySelectorAll('[data-photo]'));
    const play = gallery.querySelector('[data-play]');
    const status = gallery.querySelector('[data-gallery-status]');
    const motion = window.matchMedia('(prefers-reduced-motion: reduce)');
    let index = 0;
    let paused = motion.matches;
    let hovered = false;
    let focused = false;
    let timer;
    let startX = null;
    let startY = null;

    function schedule() {
        clearTimeout(timer);
        if (!paused && !hovered && !focused && !document.hidden) {
            timer = setTimeout(function advance() { show(index + 1, false); }, 5500);
        }
    }

    function show(next, manual) {
        index = (next + slides.length) % slides.length;
        slides.forEach(function updateSlide(slide, position) {
            const active = position === index;
            slide.classList.toggle('is-active', active);
            slide.setAttribute('aria-hidden', String(!active));
        });
        tabs.forEach(function updateTab(tab, position) {
            tab.setAttribute('aria-pressed', String(position === index));
        });
        if (manual) status.textContent = index === 0 ? label('Garment photograph', '服装实物图') : label('Styled on model', '模特穿搭图');
        schedule();
    }

    function updatePlay() {
        play.textContent = paused ? label('Play', '播放') : label('Pause', '暂停');
        play.setAttribute('aria-label', paused ? label('Start automatic slideshow', '开始自动轮播') : label('Pause automatic slideshow', '暂停自动轮播'));
        schedule();
    }

    gallery.querySelectorAll('[data-gallery-controls]').forEach(function reveal(control) { control.hidden = false; });
    gallery.querySelector('[data-prev]').addEventListener('click', function () { show(index - 1, true); });
    gallery.querySelector('[data-next]').addEventListener('click', function () { show(index + 1, true); });
    tabs.forEach(function bindTab(tab) { tab.addEventListener('click', function () { show(Number(tab.dataset.photo), true); }); });
    play.addEventListener('click', function () { paused = !paused; updatePlay(); });
    stage.addEventListener('keydown', function (event) {
        if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {
            event.preventDefault();
            show(index + (event.key === 'ArrowRight' ? 1 : -1), true);
        }
    });
    gallery.addEventListener('mouseenter', function () { hovered = true; schedule(); });
    gallery.addEventListener('mouseleave', function () { hovered = false; schedule(); });
    gallery.addEventListener('focusin', function () { focused = true; schedule(); });
    gallery.addEventListener('focusout', function (event) {
        focused = gallery.contains(event.relatedTarget);
        schedule();
    });
    stage.addEventListener('pointerdown', function (event) {
        if (event.pointerType === 'mouse') return;
        startX = event.clientX; startY = event.clientY;
    });
    stage.addEventListener('pointerup', function (event) {
        if (startX === null) return;
        const dx = event.clientX - startX;
        const dy = event.clientY - startY;
        if (Math.abs(dx) > 45 && Math.abs(dx) > Math.abs(dy)) show(index + (dx < 0 ? 1 : -1), true);
        startX = null; startY = null;
    });
    stage.addEventListener('pointercancel', function () { startX = null; startY = null; });
    document.addEventListener('visibilitychange', schedule);
    motion.addEventListener('change', function () { paused = motion.matches; updatePlay(); });
    gallery.querySelectorAll('img').forEach(function handleFailure(image) {
        image.addEventListener('error', function () {
            image.hidden = true;
            const note = document.createElement('p');
            note.className = 'gallery-image-error';
            note.textContent = label('Photograph temporarily unavailable', '照片暂时无法加载');
            image.parentElement.appendChild(note);
        }, { once: true });
    });
    updatePlay();
});
