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
