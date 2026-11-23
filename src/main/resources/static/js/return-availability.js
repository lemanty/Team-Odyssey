(() => {
    const item = document.querySelector('select[name="itemKey"]');
    const quantity = document.querySelector('input[name="quantity"]');
    if (!item || !quantity) return;
    const update = () => {
        const remaining = Number(item.selectedOptions[0]?.dataset.remaining || 0);
        quantity.max = String(remaining);
        quantity.disabled = remaining === 0;
    };
    item.addEventListener('change', update);
    update();

    const evidence = item.form.elements.namedItem('image');
    const chinese = document.documentElement.lang.startsWith('zh');
    evidence.addEventListener('change', () => {
        const file = evidence.files[0];
        let error = '';
        if (file && file.size > 5 * 1024 * 1024) {
            error = chinese ? '请选择不超过 5 MB 的图片。' : 'Choose an image of 5 MB or less.';
        } else if (file && file.type && !['image/jpeg', 'image/png'].includes(file.type)) {
            error = chinese ? '请选择 JPG 或 PNG 图片。' : 'Choose a JPG or PNG image.';
        }
        evidence.setCustomValidity(error);
        if (error) evidence.reportValidity();
    });
})();
