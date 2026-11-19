(() => {
    const table = document.querySelector('[data-report-table]');
    if (!table) return;
    const rows = [...table.tBodies[0].rows];
    const pageSize = 50;
    if (rows.length <= pageSize) return;
    const zh = document.documentElement.lang.startsWith('zh');
    const controls = document.createElement('nav');
    controls.setAttribute('aria-label', zh ? '结果分页' : 'Result pages');
    controls.className = 'report-pagination';
    const previous = document.createElement('button');
    const next = document.createElement('button');
    const status = document.createElement('span');
    status.setAttribute('aria-live', 'polite');
    previous.type = next.type = 'button';
    previous.textContent = zh ? '上一页' : 'Previous';
    next.textContent = zh ? '下一页' : 'Next';
    let page = 0;
    function render() {
        rows.forEach((row, index) => {
            row.hidden = index < page * pageSize || index >= (page + 1) * pageSize;
        });
        previous.disabled = page === 0;
        next.disabled = (page + 1) * pageSize >= rows.length;
        status.textContent = (page * pageSize + 1) + '–' + Math.min((page + 1) * pageSize, rows.length) + ' / ' + rows.length;
    }
    previous.addEventListener('click', () => { page--; render(); });
    next.addEventListener('click', () => { page++; render(); });
    controls.append(previous, status, next);
    table.parentElement.before(controls);
    render();
})();
