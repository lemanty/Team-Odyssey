(() => {
 const rows=document.querySelector('[data-options]');
 if (!rows) return;
 const stock=document.querySelector('#stock'),stockLabel=document.querySelector('[data-stock-label]');
 const updateStock=()=>{
  stock.readOnly=rows.children.length>0;
  if(stock.readOnly)stock.value=Array.from(rows.querySelectorAll('input[type=number]')).reduce((sum,input)=>sum+(Number(input.value)||0),0);
  const zh=document.documentElement.lang.startsWith('zh');
  stockLabel.textContent=stock.readOnly?(zh?'合计库存（自动计算）':'Total stock (calculated)'):(zh?'无规格商品库存':'Stock for products without options');
 };
 rows.addEventListener('input',updateStock);
 const renumber=()=>rows.querySelectorAll('.option-row').forEach((row,i)=>row.querySelectorAll('[name]').forEach(input=>input.name=input.name.replace(/options\[\d+\]/,`options[${i}]`)));
 document.querySelector('[data-add-option]').addEventListener('click',()=>{
  if(rows.children.length>=100)return;
  const fragment=document.querySelector('#option-template').content.cloneNode(true);
  fragment.querySelectorAll('[name]').forEach(input=>input.name=input.name.replace('INDEX',rows.children.length));
  rows.append(fragment); updateStock(); rows.lastElementChild.querySelector('input').focus();
 });
 rows.addEventListener('click',event=>{if(event.target.matches('[data-remove-option]')){event.target.closest('.option-row').remove();renumber();updateStock();}});
 document.querySelectorAll('[data-image-upload]').forEach(input=>input.addEventListener('change',()=>{
  input.setCustomValidity('');const file=input.files[0];if(!file)return;
  input.closest('label').querySelector('[data-file-name]').textContent=file.name;
  if(file.size>5*1024*1024){input.setCustomValidity(document.documentElement.lang.startsWith('zh')?'图片不能超过 5 MB':'Maximum image size: 5 MB');input.reportValidity();return;}
  const box=input.closest('.inventory-photo');let preview=box.querySelector('img');
  if(!preview){preview=document.createElement('img');preview.className='photo-preview';preview.alt='';box.prepend(preview);}
  if(preview.dataset.objectUrl)URL.revokeObjectURL(preview.dataset.objectUrl);
  preview.dataset.objectUrl=URL.createObjectURL(file);preview.src=preview.dataset.objectUrl;
 }));
})();
