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

    // Extend the photograph's edge colours into its container. The photo itself is untouched.
    function matchPhotoBackground(img) {
        if (!img.naturalWidth || !img.naturalHeight) return;
        try {
            const canvas = document.createElement('canvas');
            canvas.width = 24;
            canvas.height = 24;
            const ctx = canvas.getContext('2d', { willReadFrequently: true });
            if (!ctx) return;
            ctx.drawImage(img, 0, 0, 24, 24);
            const stops = [0, 6, 12, 18, 23].map(y => {
                const left = ctx.getImageData(0, y, 1, 1).data;
                const right = ctx.getImageData(23, y, 1, 1).data;
                // Transparent or non-readable photographs retain the neutral fallback.
                if (left[3] < 250 || right[3] < 250) throw new Error('Transparent edge');
                const rgb = [0, 1, 2].map(c => Math.round((left[c] + right[c]) / 2));
                return `rgb(${rgb.join(',')}) ${Math.round(y / 23 * 100)}%`;
            });
            img.parentElement.style.background = `linear-gradient(to bottom, ${stops.join(',')})`;
        } catch (_) {
            // Cross-origin images without CORS keep the CSS fallback, without affecting the card.
        }
    }
    document.querySelectorAll('.sale-card-art img').forEach(img => {
        img.addEventListener('load', () => matchPhotoBackground(img));
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
