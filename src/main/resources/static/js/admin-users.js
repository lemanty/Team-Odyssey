document.addEventListener('submit', event => {
    const form = event.target.closest('[data-confirm-delete]');
    if (form && !window.confirm(form.dataset.confirmMessage)) event.preventDefault();
});
