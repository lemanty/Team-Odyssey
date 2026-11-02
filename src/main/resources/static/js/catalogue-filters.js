(() => {
  const panel = document.querySelector('.price-slider');
  const fields = document.getElementById('custom-price-fields');
  if (!panel || !fields) return;
  const min = fields.querySelector('[name=minPrice]');
  const max = fields.querySelector('[name=maxPrice]');
  const lower = document.getElementById('price-lower');
  const upper = document.getElementById('price-upper');
  const output = document.getElementById('price-range-output');
  const precise = fields.closest('details');
  const zh = document.documentElement.lang.startsWith('zh');
  const money = value => '€' + Number(value).toLocaleString(zh ? 'zh-CN' : 'en', {maximumFractionDigits: 2});
  const valid = input => input.value !== '' && Number.isFinite(input.valueAsNumber) && input.valueAsNumber >= 0;
  const ceiling = Math.max(100, Number(panel.dataset.priceMax) || 100);
  const render = () => {
    lower.max = upper.max = ceiling;
    lower.value = valid(min) ? min.value : 0;
    upper.value = valid(max) ? max.value : ceiling;
    panel.style.setProperty('--price-start', (Number(lower.value) / ceiling * 100) + '%');
    panel.style.setProperty('--price-end', (Number(upper.value) / ceiling * 100) + '%');
    output.textContent = !min.value && !max.value ? (zh ? '不限价格' : 'Any price') : money(min.value || 0) + ' – ' + (max.value ? money(max.value) : (zh ? '不限' : 'Any'));
    lower.setAttribute('aria-valuetext', money(lower.value));
    upper.setAttribute('aria-valuetext', max.value ? money(upper.value) : (zh ? '不限' : 'No maximum'));
    // Keep the lower thumb reachable when both handles meet at the upper edge.
    lower.style.zIndex = Number(lower.value) >= ceiling ? '3' : '1';
    document.getElementById('price-scale-max').textContent = money(ceiling);
  };
  [lower, upper].forEach(slider => slider.addEventListener('input', () => {
    if (slider === lower) lower.value = Math.min(Number(lower.value), Number(upper.value));
    else upper.value = Math.max(Number(upper.value), Number(lower.value));
    min.value = lower.value;
    max.value = upper.value;
    render();
  }));
  [min, max].forEach(input => input.addEventListener('input', () => render()));
  // Exact fields remain usable if JavaScript is unavailable.
  precise.open = !!document.querySelector('.filter-error');
  panel.hidden = false;
  render();
})();
