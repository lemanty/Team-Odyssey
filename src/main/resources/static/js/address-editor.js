(() => {
    const layout = document.querySelector('.address-layout');
    if (!layout) return;
    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
    let pending;
    let animation;

    // Load only the server-rendered editor, preserving validation, ownership and CSRF.
    layout.addEventListener('click', async (event) => {
        const link = event.target.closest('.address-edit-link, .address-edit-cancel');
        if (!link || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
        const url = new URL(link.href);
        if (url.origin !== location.origin) return;
        event.preventDefault();
        pending?.abort();
        const request = new AbortController();
        pending = request;
        let editor = layout.querySelector('.address-editor');
        editor.setAttribute('aria-busy', 'true');
        try {
            const response = await fetch(url, { signal: request.signal, credentials: 'same-origin' });
            if (!response.ok || response.redirected) throw new Error('Editor unavailable');
            const html = new DOMParser().parseFromString(await response.text(), 'text/html');
            const incoming = html.querySelector('.address-editor');
            if (!incoming) throw new Error('Editor unavailable');
            if (request.signal.aborted) return;
            animation?.cancel();
            const replacement = document.importNode(incoming, true);
            replacement.classList.remove('address-editor-enter');
            editor.replaceWith(replacement);
            editor = replacement;
            history.pushState(null, '', url);
            layout.querySelectorAll('.address-card').forEach(card => {
                card.classList.toggle('is-editing', card.contains(link) && link.matches('.address-edit-link'));
            });
            document.querySelector('.address-page .cart-error')?.remove();
            if (!reducedMotion.matches) {
                animation = editor.animate([
                    { opacity: 0, transform: 'translateX(-44px)' },
                    { opacity: 1, transform: 'translateX(0)' }
                ], { duration: 420, easing: 'cubic-bezier(.22, 1, .36, 1)' });
            }
            // Keep the address list stable; only scroll when the editor is off-screen.
            const rect = editor.getBoundingClientRect();
            if (rect.top >= window.innerHeight || rect.bottom <= 0) {
                editor.scrollIntoView({ behavior: reducedMotion.matches ? 'instant' : 'smooth', block: 'start' });
            }
            editor.querySelector('#recipient')?.focus({ preventScroll: true });
        } catch (error) {
            if (error.name !== 'AbortError') location.assign(url.href);
        } finally {
            if (pending === request) {
                editor.removeAttribute('aria-busy');
                pending = null;
            }
        }
    });
    // Back/forward must restore the form matching the URL, including account-window scope.
    window.addEventListener('popstate', () => location.reload());
})();
