(() => {
    const password = document.getElementById('password');
    const confirmation = document.getElementById('confirmPassword');
    const zh = document.documentElement.lang.startsWith('zh');
    function validate() {
        const tooLong = new TextEncoder().encode(password.value).length > 72;
        password.setCustomValidity(tooLong ? (zh ? '密码最多可用 72 个 UTF-8 字节，请缩短密码。' : 'Shorten your password to 72 UTF-8 bytes or fewer.') : '');
        const mismatch = confirmation.value && password.value !== confirmation.value;
        confirmation.setCustomValidity(mismatch ? (zh ? '两次密码不一致。' : 'The passwords do not match.') : '');
    }
    password.form.addEventListener('input', validate);
    password.form.addEventListener('submit', event => {
        validate();
        if (!password.form.reportValidity()) event.preventDefault();
    });
})();
