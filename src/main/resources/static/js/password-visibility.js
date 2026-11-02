(() => {
    document.querySelectorAll('.password-toggle').forEach(button => {
        const input = document.getElementById(button.getAttribute('aria-controls'));
        if (!input) return;
        const hide = () => {
            input.type = 'password';
            button.setAttribute('aria-pressed', 'false');
            button.setAttribute('aria-label', button.dataset.showLabel);
            button.querySelector('.password-eye-slash').setAttribute('hidden', '');
        };
        button.hidden = false;
        button.addEventListener('click', () => {
            const show = input.type === 'password';
            input.type = show ? 'text' : 'password';
            button.setAttribute('aria-pressed', String(show));
            button.setAttribute('aria-label', show ? button.dataset.hideLabel : button.dataset.showLabel);
            button.querySelector('.password-eye-slash').toggleAttribute('hidden', !show);
        });
        input.form?.addEventListener('submit', hide);
        window.addEventListener('pagehide', hide);
    });
})();
