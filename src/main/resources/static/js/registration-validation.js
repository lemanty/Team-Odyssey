(() => {
    const password = document.getElementById('password');
    const confirmation = document.getElementById('confirmPassword');
    const zh = document.documentElement.lang.startsWith('zh');
    function validate() {
        const mismatch = confirmation.value && password.value !== confirmation.value;
        confirmation.setCustomValidity(mismatch ? (zh ? '两次密码不一致。' : 'The passwords do not match.') : '');
    }
    password.form.addEventListener('input', validate);
    password.form.addEventListener('submit', event => {
        validate();
        if (!password.form.reportValidity()) event.preventDefault();
    });
})();
