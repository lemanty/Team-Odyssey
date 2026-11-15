(() => {
  document.querySelectorAll('form.auth-form').forEach(form => {
    let pending = false;
    const buttons = [...form.querySelectorAll('button[type="submit"]')];
    const initial = buttons.map(button => button.disabled);
    form.addEventListener('submit', event => {
      if (event.defaultPrevented) return;
      if (pending) { event.preventDefault(); return; }
      pending = true;
      form.setAttribute('aria-busy', 'true');
      buttons.forEach(button => { button.disabled = true; });
    });
    window.addEventListener('pageshow', () => {
      pending = false;
      form.removeAttribute('aria-busy');
      buttons.forEach((button, index) => { button.disabled = initial[index]; });
    });
  });
})();
