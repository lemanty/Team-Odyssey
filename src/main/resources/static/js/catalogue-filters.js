(() => {
  const range = document.getElementById('price-range');
  const fields = document.getElementById('custom-price-fields');
  if (!range || !fields) return;
  const update = () => {
    const custom = range.value === 'custom';
    fields.hidden = !custom;
    fields.disabled = !custom;
  };
  range.addEventListener('change', update);
  update();
})();
