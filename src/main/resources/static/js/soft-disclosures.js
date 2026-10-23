(() => {
  const reduced = matchMedia('(prefers-reduced-motion: reduce)');
  document.querySelectorAll('details.soft-disclosure').forEach(details => {
    const summary = details.querySelector(':scope > summary');
    let animation;
    let desired = details.open;
    const finish = () => {
      animation?.cancel();
      animation = undefined;
      details.open = desired;
      details.style.height = '';
      details.style.overflow = '';
    };
    summary.addEventListener('click', event => {
      if (reduced.matches || !details.animate) return;
      event.preventDefault();
      const start = details.getBoundingClientRect().height;
      desired = animation ? !desired : !details.open;
      animation?.cancel();
      details.style.height = '';
      details.open = true;
      const end = desired ? details.getBoundingClientRect().height : summary.getBoundingClientRect().height + 1;
      details.style.overflow = 'hidden';
      animation = details.animate([{height:start+'px'},{height:end+'px'}], {
        duration:260,easing:'cubic-bezier(.22,.7,.25,1)'
      });
      animation.onfinish = finish;
    });
    reduced.addEventListener('change', () => { if (reduced.matches && animation) finish(); });
    window.addEventListener('beforeprint', () => { if (animation) finish(); });
  });
})();
