(() => {
    const carousel = document.querySelector('[data-sale-carousel]');
    const controls = carousel?.querySelector('[data-sale-controls]');
    if (controls) {
        const slides = [...carousel.querySelectorAll('[data-sale-slide]')];
        const position = controls.querySelector('[data-sale-position]');
        let index = 0;

        function move(step) {
            slides[index].hidden = true;
            index = (index + step + slides.length) % slides.length;
            slides[index].hidden = false;
            position.textContent = String(index + 1).padStart(2, '0');
        }

        controls.querySelector('[data-sale-prev]').addEventListener('click', () => move(-1));
        controls.querySelector('[data-sale-next]').addEventListener('click', () => move(1));
        controls.hidden = false;
    }

    // Extend each edge independently so directional lighting continues without a seam.
    // The original photograph remains the foreground image, unchanged.
    function matchPhotoBackground(img) {
        const host = img.parentElement;
        if (!img.naturalWidth || !img.naturalHeight || !host.clientWidth) return;
        try {
            const canvas = document.createElement('canvas');
            const scale = Math.min(window.devicePixelRatio || 1, 2);
            canvas.width = Math.round(host.clientWidth * scale);
            canvas.height = Math.round(host.clientHeight * scale);
            const ctx = canvas.getContext('2d');
            if (!ctx) return;
            const ratio = Math.min(canvas.width / img.naturalWidth, canvas.height / img.naturalHeight);
            const width = img.naturalWidth * ratio;
            const height = img.naturalHeight * ratio;
            const x = (canvas.width - width) / 2;
            const y = (canvas.height - height) / 2;
            if (x > 0) {
                ctx.drawImage(img, 0, 0, 1, img.naturalHeight, 0, y, x + 1, height);
                ctx.drawImage(img, img.naturalWidth - 1, 0, 1, img.naturalHeight,
                    x + width - 1, y, x + 1, height);
            }
            if (y > 0) {
                ctx.drawImage(img, 0, 0, img.naturalWidth, 1, x, 0, width, y + 1);
                ctx.drawImage(img, 0, img.naturalHeight - 1, img.naturalWidth, 1,
                    x, y + height - 1, width, y + 1);
            }
            ctx.drawImage(img, x, y, width, height);
            host.style.backgroundImage = `url("${canvas.toDataURL()}")`;
            host.style.backgroundSize = '100% 100%';
        } catch (_) {
            // External images without CORS retain the neutral CSS fallback.
        }
    }
    const photoObserver = new ResizeObserver(entries => {
        entries.forEach(({ target }) => matchPhotoBackground(target.querySelector('img')));
    });
    document.querySelectorAll('.sale-card-art img').forEach(img => {
        img.addEventListener('load', () => matchPhotoBackground(img));
        photoObserver.observe(img.parentElement);
        if (img.complete) matchPhotoBackground(img);
    });

    const deals = document.querySelector('[data-sale-deals]');
    const filters = deals.querySelector('[data-sale-filters]');
    const cards = [...deals.querySelectorAll('[data-sale-category]')];
    if (!cards.length) return;

    const buttons = [...filters.querySelectorAll('button')];
    const results = deals.querySelector('[data-sale-count]');
    const empty = deals.querySelector('[data-sale-empty]');
    buttons.forEach(button => button.addEventListener('click', () => {
        const filter = button.dataset.filter;
        const matching = cards.filter(card => {
            const visible = filter === 'all' || card.dataset.saleCategory === filter;
            card.hidden = !visible;
            return visible;
        });
        buttons.forEach(option => option.setAttribute('aria-pressed', String(option === button)));
        results.textContent = matching.length;
        empty.hidden = matching.length > 0;
    }));
    filters.hidden = false;
})();
