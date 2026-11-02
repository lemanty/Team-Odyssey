(() => {
    document.querySelectorAll('input[type="search"]').forEach(input => {
        const form = input.form;
        if (!form || form.method.toLowerCase() !== 'get') return;
        let previous = input.value;
        let queued = false;
        function changed() {
            const cleared = previous !== '' && input.value === '';
            previous = input.value;
            if (!cleared || queued) return;
            queued = true;
            requestAnimationFrame(() => {
                queued = false;
                if (input.value !== '') return;
                const page = form.elements.namedItem('page');
                if (page) page.value = '0';
                form.requestSubmit();
            });
        }
        input.addEventListener('input', changed);
        input.addEventListener('search', changed);
    });
})();
