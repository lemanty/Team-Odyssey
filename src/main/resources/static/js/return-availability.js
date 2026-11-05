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
})();
