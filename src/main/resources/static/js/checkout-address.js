(() => {
    const mode = document.getElementById('fulfillment');
    const saved = document.getElementById('addressId');
    const details = document.getElementById('delivery-details');
    const manual = document.getElementById('manual-address');
    const input = document.getElementById('address');
    if (!mode || !details || !manual || !input) return;
    function update() {
        const delivery = mode.value === 'DELIVERY';
        const useManual = !saved || !saved.value;
        details.hidden = !delivery;
        manual.hidden = !useManual;
        input.required = delivery && useManual;
    }
    mode.addEventListener('change', update);
    saved?.addEventListener('change', update);
    update();
})();
