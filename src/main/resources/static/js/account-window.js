/* Each click creates a public selector; authentication stays in a separate HttpOnly cookie. */
(() => {
    const link = document.querySelector('[data-new-account-window]');
    if (!link) return;
    link.addEventListener('click', () => {
        const id = Array.from(crypto.getRandomValues(new Uint8Array(16)), byte => byte.toString(16).padStart(2, '0')).join('');
        link.href = '/login?window=' + id;
    });
})();
