document.querySelectorAll('.sale-product-row').forEach(row => {
    const eligible = row.querySelector('[name="productIds"]');
    const featured = row.querySelector('[name="featuredIds"]');
    featured.addEventListener('change', () => { if (featured.checked) eligible.checked = true; });
    eligible.addEventListener('change', () => { if (!eligible.checked) featured.checked = false; });
});
