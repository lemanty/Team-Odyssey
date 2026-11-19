(() => {
    const checkout = document.querySelector('[data-cart-checkout]');
    const notice = document.getElementById('cart-unsaved');
    if (!checkout || !notice) return;
    const quantities = [...document.querySelectorAll('.cart-quantity input[name=quantity]')];
    const changed = () => quantities.filter(input => input.value !== input.defaultValue);
    function refresh() {
        const dirty = changed().length > 0;
        notice.hidden = !dirty;
        checkout.setAttribute('aria-disabled', String(dirty));
    }
    quantities.forEach(input => input.addEventListener('input', refresh));
    window.addEventListener('pageshow', refresh);
    checkout.addEventListener('click', event => {
        const pending = changed();
        if (pending.length) {
            event.preventDefault();
            refresh();
            pending[0].focus();
        }
    });
    refresh();
})();
