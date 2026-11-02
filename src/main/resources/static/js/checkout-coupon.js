(() => {
    const select = document.getElementById('couponCode');
    const preview = document.getElementById('coupon-preview');
    if (!select || !preview) return;
    select.addEventListener('change', () => select.form.requestSubmit(preview));
})();
