(() => {
  document.querySelectorAll('[data-review-open]').forEach(button => {
    button.addEventListener('click', () => document.getElementById(button.dataset.reviewOpen)?.showModal());
  });
  document.querySelectorAll('.review-dialog').forEach(dialog => {
    dialog.querySelector('[data-review-close]').addEventListener('click', () => dialog.close());
    dialog.addEventListener('click', event => { if (event.target === dialog) {
      const box = dialog.getBoundingClientRect();
      if (event.clientX < box.left || event.clientX > box.right || event.clientY < box.top || event.clientY > box.bottom) dialog.close();
    }});
  });
})();
