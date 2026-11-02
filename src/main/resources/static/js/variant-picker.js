document.querySelectorAll('[data-variant-picker]').forEach(picker => {
    const colours = [...picker.querySelectorAll('[data-variant-colour]')];
    const panels = [...picker.querySelectorAll('[data-size-panel]')];
    const prompt = picker.querySelector('[data-size-prompt]');
    function update(clearSelection) {
        const selected = colours.find(input => input.checked && !input.disabled);
        prompt.hidden = Boolean(selected);
        panels.forEach(panel => {
            const active = Boolean(selected && panel.dataset.colourIndex === selected.value);
            panel.hidden = !active;
            panel.querySelectorAll('input[name="variantId"]').forEach(input => {
                if (clearSelection || !active) input.checked = false;
                input.disabled = !active || input.dataset.outOfStock === 'true';
            });
        });
    }
    colours.forEach(input => input.addEventListener('change', () => update(true)));
    update(false);
    window.addEventListener('pageshow', () => update(false));
});
