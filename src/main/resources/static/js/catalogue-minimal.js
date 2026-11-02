(() => {
 const popover=document.querySelector('.filter-popover');
 document.addEventListener('click',event=>{if(popover?.open&&!popover.contains(event.target))popover.open=false;});
 document.addEventListener('keydown',event=>{if(event.key==='Escape'&&popover?.open){popover.open=false;popover.querySelector('summary').focus();}});
 document.querySelectorAll('.product-disclosure, .catalogue-demo').forEach(details=>{
  const summary=details.querySelector('summary');let animation;
  summary.addEventListener('click',event=>{
   if(matchMedia('(prefers-reduced-motion: reduce)').matches||!details.animate)return;
   event.preventDefault();if(animation)return;
   const start=details.offsetHeight,closing=details.open;
   details.open=true;
   const end=closing?summary.offsetHeight:details.offsetHeight;
   details.style.overflow='hidden';
   animation=details.animate([{height:`${start}px`},{height:`${end}px`}],{duration:240,easing:'cubic-bezier(.2,.7,.3,1)'});
   animation.onfinish=()=>{details.open=!closing;details.style.overflow='';animation=null;};
  });
 });
})();
