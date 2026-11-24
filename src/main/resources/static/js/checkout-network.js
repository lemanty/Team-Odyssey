(() => {
    const form = document.getElementById('selected-checkout-form');
    if (!form) return;
    const notice = document.getElementById('checkout-network-status');
    const refresh = () => {
        notice.hidden = navigator.onLine;
        for (const control of form.elements) {
            if (control.type === 'submit') control.disabled = !navigator.onLine;
        }
    };
    let submitted = false;
    window.addEventListener('pageshow', event => {
        if (event.persisted && submitted) window.location.reload();
    });
    form.addEventListener('submit', event => {
        if (!navigator.onLine) {
            event.preventDefault();
            refresh();
        } else {
            submitted = !event.submitter?.hasAttribute('formaction');
        }
    });
    window.addEventListener('online', refresh);
    window.addEventListener('offline', refresh);
    refresh();
})();
