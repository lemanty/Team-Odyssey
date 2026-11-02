(() => {
 const zh=document.documentElement.lang.startsWith('zh');
 document.querySelectorAll('[data-stock-tools]').forEach(panel=>{
  const content=panel.querySelector('[data-stock-content]');
  let busy=false;
  async function load(url,options={}) {
   if(busy)return;
   busy=true;content.setAttribute('aria-busy','true');
   const buttons=[...content.querySelectorAll('button')];buttons.forEach(b=>b.disabled=true);
   try {
    const response=await fetch(url,{...options,headers:{'X-Requested-With':'XMLHttpRequest'}});
    if(!response.ok || response.redirected)throw new Error();
    const scroll=content.querySelector('.stock-catalogue')?.scrollTop || 0;
    const html=await response.text();
    content.innerHTML=html;
    const list=content.querySelector('.stock-catalogue');if(list)list.scrollTop=scroll;
    content.querySelector('[data-stock-heading]')?.focus({preventScroll:true});
   } catch {
    let notice=content.querySelector('[data-load-error]');
    if(!notice){notice=document.createElement('p');notice.dataset.loadError='';notice.setAttribute('role','alert');content.prepend(notice);}
    notice.textContent=zh?'无法加载或保存，请刷新页面或重试。':'Unable to load or save. Refresh the page or retry.';
   } finally {busy=false;content.removeAttribute('aria-busy');buttons.forEach(b=>b.disabled=false);}
  }
  load(panel.dataset.url);
  content.addEventListener('submit',event=>{
   const form=event.target;if(!(form instanceof HTMLFormElement))return;
   event.preventDefault();const data=new URLSearchParams(new FormData(form));
   if(form.method.toLowerCase()==='post')load(form.action,{method:'POST',body:data});
   else {const url=new URL(form.action);data.forEach((v,k)=>url.searchParams.set(k,v));load(url);}
  });
  content.addEventListener('input',event=>{
   if(event.target.type==='search'&&event.target.value==='')event.target.form.requestSubmit();
  });
 });
})();
