(() => {
  const range = document.getElementById('price-range');
  const fields = document.getElementById('custom-price-fields');
  if (!range || !fields) return;
  const update = () => {
    const custom = range.value === 'custom';
    fields.hidden = !custom;
    fields.disabled = !custom;
  };
  range.addEventListener('change', () => {
    update();
    if (!fields.hidden && !matchMedia('(prefers-reduced-motion: reduce)').matches && fields.animate) {
      fields.getAnimations().forEach(animation => animation.cancel());
      fields.animate([{opacity:0,translate:'0 -6px'},{opacity:1,translate:'0 0'}],
        {duration:220,easing:'cubic-bezier(.22,.7,.25,1)'});
    }
  });
  update();
})();
