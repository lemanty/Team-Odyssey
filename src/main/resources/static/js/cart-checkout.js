(() => {
    const notice = document.getElementById('cart-unsaved');
    if (!notice) return;

    const checkout = document.querySelector('[data-cart-checkout]');
    const forms = [...document.querySelectorAll('.cart-quantity, .cart-remove')];
    const quantities = [...document.querySelectorAll('.cart-quantity input[name=quantity]')];
    const itemId = form => form.elements.namedItem('productId').value;
    const token = forms[0].elements.namedItem('csrf').value;
    const draftKey = `odyssey:cart-quantities:${token}`;
    const normalNotice = notice.textContent;
    const changed = () => quantities.filter(input => input.value !== input.defaultValue);

    function refresh() {
        const dirty = changed().length > 0;
        notice.textContent = normalNotice;
        notice.hidden = !dirty;
        checkout?.setAttribute('aria-disabled', String(dirty));
    }

    // A cart POST redirects to a fresh server-rendered cart. Restore other rows'
    // drafts on that page, and keep the submitted row's input if the update failed.
    try {
        const saved = sessionStorage.getItem(draftKey);
        if (saved) {
            sessionStorage.removeItem(draftKey);
            const { submitted, edits } = JSON.parse(saved);
            const failed = document.querySelector('.cart-page > .cart-feedback.cart-error');
            for (const input of quantities) {
                const id = itemId(input.form);
                if (Object.hasOwn(edits, id) && (id !== submitted || failed)) {
                    input.value = edits[id];
                    input.dispatchEvent(new Event('input', { bubbles: true }));
                }
            }
        }
    } catch {
        // A cart page remains usable when session storage is disabled.
    }

    forms.forEach(form => form.addEventListener('submit', event => {
        const pending = changed();
        if (!pending.length) return;
        const submitted = itemId(form);
        const edits = Object.fromEntries(pending.map(input => [itemId(input.form), input.value]));
        try {
            sessionStorage.setItem(draftKey, JSON.stringify({ submitted, edits }));
        } catch {
            if (pending.some(input => input.form !== form)) {
                event.preventDefault();
                notice.textContent = notice.dataset.storageError;
                notice.hidden = false;
            }
        }
    }));

    quantities.forEach(input => input.addEventListener('input', refresh));
    window.addEventListener('pageshow', refresh);
    checkout?.addEventListener('click', event => {
        const pending = changed();
        if (pending.length) {
            event.preventDefault();
            refresh();
            pending[0].focus();
        }
    });
    refresh();
})();
