(() => {
  if (!document.body.classList.contains('chosen-design')) return;
  const reduced = matchMedia('(prefers-reduced-motion: reduce)');
  document.querySelectorAll('.chosen-disclosure').forEach(details => {
    const summary = details.querySelector(':scope > summary');
    let animation, desired = details.open;
    const finish = () => { animation?.cancel(); animation = null; details.open = desired; details.style.height = ''; details.style.overflow = ''; };
    summary.addEventListener('click', event => {
      if (reduced.matches || !details.animate) return;
      event.preventDefault();
      const start = details.getBoundingClientRect().height;
      desired = animation ? !desired : !details.open;
      animation?.cancel(); details.style.height = ''; details.open = true;
      const end = desired ? details.getBoundingClientRect().height : summary.getBoundingClientRect().height + 1;
      details.style.overflow = 'hidden';
      animation = details.animate([{height:start+'px'},{height:end+'px'}],{duration:260,easing:'cubic-bezier(.22,.7,.25,1)'});
      animation.onfinish = finish;
    });
    reduced.addEventListener('change', () => { if (animation && reduced.matches) finish(); });
  });
  document.querySelectorAll('[data-quantity-step]').forEach(button => {
    button.hidden = false;
    const input = button.form.querySelector('[name=quantity]');
    const sync = () => button.disabled = !input.validity.valid || (Number(button.dataset.quantityStep)<0 ? Number(input.value)<=1 : Number(input.value)>=999);
    input.addEventListener('input',sync); sync();
    button.addEventListener('click', () => {
      input.value = Math.max(1,Math.min(999,Number(input.value)+Number(button.dataset.quantityStep)));
      button.form.requestSubmit();
    });
  });
  const preview = document.querySelector('[data-return-preview]');
  const selectRow = row => {
    if (!preview) return;
    const template = row?.querySelector('template[data-return-summary]');
    preview.replaceChildren(); preview.hidden = !template;
    document.querySelectorAll('[data-return-select]').forEach(button => { const active = button.closest('tr') === row; button.setAttribute('aria-pressed',String(active)); button.closest('tr').classList.toggle('is-selected',active); });
    if (template) { preview.append(template.content.cloneNode(true)); if (!reduced.matches && preview.animate) preview.animate([{opacity:.4,translate:'-6px 0'},{opacity:1,translate:'0 0'}],{duration:220,easing:'ease-out'}); }
  };
  document.querySelectorAll('[data-return-select]').forEach(button => {button.hidden=false;button.addEventListener('click',()=>selectRow(button.closest('tr')));});
  selectRow(document.querySelector('[data-filter-row=returns]'));
  document.querySelectorAll('[data-filter-for]').forEach(tabs => {
    tabs.hidden = false;
    const kind=tabs.dataset.filterFor;
    const rows=[...document.querySelectorAll(`[data-filter-row="${kind}"]`)];
    tabs.querySelectorAll('button').forEach(button=>button.addEventListener('click',()=>{
      const statuses=button.dataset.status.split(',');
      rows.forEach(row=>row.hidden=button.dataset.status!=='ALL'&&!statuses.includes(row.dataset.status));
      tabs.querySelectorAll('button').forEach(b=>b.setAttribute('aria-pressed',String(b===button)));
      const shown=rows.filter(row=>!row.hidden);
      const empty=document.querySelector(`[data-filter-empty="${kind}"]`);if(empty)empty.hidden=shown.length>0;
      if(kind==='returns')selectRow(shown[0]);
    }));
  });
  // Keep existing gallery colour synchronization reflected in its thumbnail strip.
  [['[data-garment-photo]','[data-thumbnail-garment]'],['[data-model-photo]','[data-thumbnail-model]']].forEach(([source,target])=>{
    const photo=document.querySelector(source), thumb=document.querySelector(target);
    if(photo&&thumb)new MutationObserver(()=>thumb.src=photo.src).observe(photo,{attributes:true,attributeFilter:['src']});
  });
})();
