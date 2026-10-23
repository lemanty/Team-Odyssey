(() => {
 if(!document.body.classList.contains('ops-design'))return;
 const zh=document.documentElement.lang.startsWith('zh');
 const el=(tag,text,cls)=>{const n=document.createElement(tag);if(text!==undefined)n.textContent=text;if(cls)n.className=cls;return n;};
 // Move original forms, retaining CSRF tokens, expected roles and delete confirmation.
 const accountPanel=document.querySelector('[data-account-inspector]');
 if(accountPanel){
  let previous;
  const rows=[...document.querySelectorAll('.users-table tbody tr')];
  const select=row=>{
   if(previous){const cell=previous.cells[3];while(accountPanel.querySelector('[data-account-controls]')?.firstChild)cell.append(accountPanel.querySelector('[data-account-controls]').firstChild);}
   accountPanel.replaceChildren();
   const title=el('h2',row.cells[0].querySelector('strong').textContent);
   accountPanel.append(title,el('p',row.cells[0].querySelector('.users-email').textContent),el('small',row.cells[1].textContent),el('h3',zh?'角色设置':'Role settings'));
   const controls=el('div');controls.dataset.accountControls='';while(row.cells[3].firstChild)controls.append(row.cells[3].firstChild);accountPanel.append(controls);
   accountPanel.append(el('p',zh?'员工：库存、订单、售后及客服。管理员：商品、账号、储值和活动管理，不参与客服。':'Staff: stock, orders, returns and support. Admin: products, accounts, wallets and offers; no support access.'));
   rows.forEach(r=>{r.classList.toggle('is-selected',r===row);r.querySelector('button[data-account-select]').setAttribute('aria-pressed',String(r===row));});previous=row;accountPanel.hidden=false;
  };
  rows.forEach(row=>{const b=row.querySelector('[data-account-select]');b.hidden=false;b.addEventListener('click',()=>select(row));});
  if(rows.length){document.querySelector('.account-master').classList.add('enhanced');select(rows[0]);}
 }
 document.querySelectorAll('.coupon-create').forEach(form=>{
  const kind=form.elements.namedItem('kind'), value=form.elements.namedItem('value'), minimum=form.elements.namedItem('minimum');
  const hint=el('p');hint.id='coupon-amount-rule';hint.className='coupon-amount-rule';hint.setAttribute('aria-live','polite');value.closest('label').append(hint);value.setAttribute('aria-describedby',hint.id);
  const validate=()=>{
   const fixed=kind.value==='FIXED';
   const invalid=fixed&&value.value!==''&&minimum.value!==''&&Number(value.value)>Number(minimum.value);
   value.setCustomValidity(invalid?(zh?'固定优惠金额不能大于使用门槛金额。':'Fixed discount cannot exceed the minimum spend.'):'');
   hint.textContent=fixed?(zh?'固定优惠金额须小于或等于使用门槛。例如：满 €100 减 €20。':'Fixed discount must not exceed minimum spend. Example: €20 off €100.'):(zh?'填写减免百分比：20 表示减免 20%，即打 8 折。':'Enter percentage off: 20 means 20% off.');
   hint.classList.toggle('is-invalid',invalid);value.setAttribute('aria-invalid',String(invalid));
  };[kind,value,minimum].forEach(input=>input.addEventListener('input',validate));kind.addEventListener('change',validate);validate();
 });
 const offerPanel=document.querySelector('[data-offer-inspector]');
 if(offerPanel){
  const shell=offerPanel.parentElement,create=shell.querySelector('.offer-create');let previous;
  const select=card=>{
   if(previous){const actions=offerPanel.querySelector('.fulfillment-heading');if(actions)previous.append(actions);}
   offerPanel.replaceChildren(card.querySelector('.coupon-ticket').cloneNode(true));
   const action=card.querySelector('.fulfillment-heading');if(action)offerPanel.append(action);
   offerPanel.append(el('p',zh?'历史优惠规则保留不改。调整优惠条件请创建新券；可在此启用或停用。':'Existing terms remain unchanged. Create a new offer to change terms; enable or disable this offer here.'));
   const add=el('button',zh?'+ 创建新活动':'+ New offer','offer-action');add.type='button';add.addEventListener('click',()=>{offerPanel.hidden=true;create.hidden=false;create.open=true;create.querySelector('input')?.focus();});offerPanel.append(add);
   shell.querySelectorAll('.offer-management-card').forEach(c=>c.classList.toggle('is-selected',c===card));create.hidden=true;offerPanel.hidden=false;previous=card;
  };
  const cards=[...shell.querySelectorAll('.offer-management-card')];cards.forEach(c=>{const b=c.querySelector('[data-offer-select]');b.hidden=false;b.addEventListener('click',()=>select(c));});
  shell.classList.add('enhanced');if(cards.length&&!create.open)select(cards[0]);
 }
 // Safe live preview from form controls; no HTML interpolation.
 document.querySelectorAll('.inventory-editor').forEach(form=>{
  const preview=form.querySelector('.editor-preview');if(!preview)return;let objectUrl;
  const update=()=>{
   const value=name=>form.elements.namedItem(name)?.value||'';
   preview.querySelector('[data-product-preview-title]').textContent=value(zh?'nameZh':'name')||(zh?'商品名称':'Product name');
   preview.querySelector('[data-product-preview-description]').textContent=value(zh?'descriptionZh':'description');
   preview.querySelector('[data-product-preview-price]').textContent='€'+(Number.isFinite(Number(value('price')))?Number(value('price')).toFixed(2):'0.00');
   preview.querySelector('[data-product-preview-state]').textContent=form.querySelector('[name=published]')?.checked?(zh?'上架中':'Published'):(zh?'草稿 / 未上架':'Draft / unpublished');
   const file=form.querySelector('[name=mainPhoto]')?.files?.[0];if(objectUrl){URL.revokeObjectURL(objectUrl);objectUrl=null;}
   let source=value('image');if(file&&['image/png','image/jpeg'].includes(file.type)){objectUrl=URL.createObjectURL(file);source=objectUrl;}
   const img=preview.querySelector('img');const safe=source.startsWith('/')&&!source.startsWith('//')||source.startsWith('blob:');img.hidden=!safe;preview.querySelector('[data-product-preview-empty]').hidden=safe;if(safe)img.src=source;else img.removeAttribute('src');
  };form.addEventListener('input',update);form.addEventListener('change',update);update();
 });
 const inspector=document.querySelector('[data-order-inspector]');
 if(inspector){
  let controller;
  const links=[...document.querySelectorAll('[data-order-open]')];
  const load=async link=>{
   controller?.abort();controller=new AbortController();const signal=controller.signal;inspector.hidden=false;inspector.replaceChildren(el('p',zh?'正在加载订单…':'Loading order…'));inspector.setAttribute('aria-busy','true');
   try{
    const response=await fetch(link.href,{signal});if(!response.ok||response.redirected)throw Error();
    const doc=new DOMParser().parseFromString(await response.text(),'text/html'),grid=doc.querySelector('.order-grid');if(!grid)throw Error();
    inspector.replaceChildren(el('h2',zh?'订单处理':'Order processing'));
    const full=el('a',zh?'打开完整订单 →':'Full order →');full.href=link.href;inspector.append(full,grid);
    links.forEach(l=>l.closest('.staff-order-card').classList.toggle('is-selected',l===link));
   }catch(e){if(e.name==='AbortError')return;const fallback=el('a',zh?'无法加载，点击打开订单详情':'Unable to load. Open order details');fallback.href=link.href;inspector.replaceChildren(fallback);}
   finally{if(!signal.aborted)inspector.removeAttribute('aria-busy');}
  };
  links.forEach(l=>l.addEventListener('click',event=>{if(event.ctrlKey||event.metaKey||event.shiftKey||event.altKey)return;event.preventDefault();load(l);}));if(links.length)load(links[0]);
 }
 const returnForm=document.querySelector('.return-application .return-form');
 if(returnForm){
  const steps=[...returnForm.querySelectorAll('[data-return-step]')],nav=[...document.querySelectorAll('.return-steps li')],buttons=returnForm.querySelector('.return-step-buttons');let current=0;
  const show=index=>{current=index;steps.forEach((s,i)=>s.hidden=i!==index);nav.forEach((n,i)=>{n.classList.toggle('active',i===index);if(i===index)n.setAttribute('aria-current','step');else n.removeAttribute('aria-current');});buttons.hidden=false;buttons.querySelector('[data-step-back]').disabled=index===0;buttons.querySelector('[data-step-next]').hidden=index===steps.length-1;const summary=returnForm.querySelector('[data-return-confirm]');summary.textContent=returnForm.querySelector('[name=itemKey]').selectedOptions[0]?.textContent+(zh?'\n本次退货数量：':'\nReturn quantity: ')+returnForm.querySelector('[name=quantity]').value+'\n'+returnForm.querySelector('[name=reason]').value;};
  buttons.querySelector('[data-step-next]').addEventListener('click',()=>{for(const input of steps[current].querySelectorAll('input,select,textarea'))if(!input.reportValidity())return;show(Math.min(current+1,steps.length-1));});
  buttons.querySelector('[data-step-back]').addEventListener('click',()=>show(Math.max(current-1,0)));
  returnForm.addEventListener('invalid',event=>{const i=steps.findIndex(s=>s.contains(event.target));if(i>=0)show(i);},true);
  returnForm.addEventListener('submit',event=>{if(current<steps.length-1){event.preventDefault();buttons.querySelector('[data-step-next]').click();}});show(0);
 }
 const offerTabs=document.querySelector('[data-offer-tabs]');
 if(offerTabs){offerTabs.hidden=false;const cards=[...document.querySelectorAll('[data-offer-state]')];offerTabs.querySelectorAll('button').forEach(button=>button.addEventListener('click',()=>{cards.forEach(c=>c.hidden=button.dataset.offerFilter!=='all'&&c.dataset.offerState!==button.dataset.offerFilter);offerTabs.querySelectorAll('button').forEach(b=>b.setAttribute('aria-pressed',String(b===button)));document.querySelector('[data-offer-empty]').hidden=cards.some(c=>!c.hidden);}));offerTabs.querySelector('button').setAttribute('aria-pressed','true');}
 // Keep linked-order controls associated with the original composer form when shown above chat.
 const composer=document.querySelector('.support-composer'),chat=document.querySelector('.support-conversation');
 if(composer&&chat){
  const picker=composer.querySelector('.support-order-picker');if(picker){composer.id='support-compose-form';picker.querySelectorAll('select').forEach(s=>s.setAttribute('form',composer.id));picker.classList.add('support-top-orders');chat.prepend(picker);}
 }
})();
