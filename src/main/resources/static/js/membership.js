(() => {
  const form = document.getElementById('membership-estimator');
  if (!form) return;
  const amount = document.getElementById('member-amount');
  const tier = document.getElementById('member-tier');
  const total = document.getElementById('member-total');
  const saving = document.getElementById('member-saving');
  const invalid = document.getElementById('member-invalid');
  function update() {
    const valid = amount.checkValidity() && /^\d+(\.\d{1,2})?$/.test(amount.value);
    invalid.hidden = valid;
    amount.setAttribute('aria-invalid', String(!valid));
    if (!valid) { total.textContent = saving.textContent = '—'; return; }
    const cents = Math.round(Number(amount.value) * 100);
    const payable = Math.round(cents * Number(tier.value) / 100);
    total.textContent = '€' + (payable / 100).toFixed(2);
    saving.textContent = '€' + ((cents - payable) / 100).toFixed(2);
  }
  form.addEventListener('input', update);
  form.addEventListener('submit', event => { event.preventDefault(); update(); });
  update();
})();
