/* Refresh only inbox and message history, never the customer's draft or send token. */
(() => {
    const root = document.querySelector('[data-support-base]');
    if (!root) return;
    const zh = document.documentElement.lang === 'zh-CN';
    const connection = root.querySelector('[data-connection]');
    const base = root.dataset.supportBase;
    const scoped = value => {
        const url = new URL(value, location.href);
        const selected = new URL(location.href).searchParams.get('window');
        if (/^[a-f0-9]{32}$/.test(selected || '') && url.origin === location.origin) url.searchParams.set('window', selected);
        return url.href;
    };
    const file = document.getElementById('support-image');
    const preview = document.querySelector('[data-image-preview]');
    const uploadError = document.querySelector('[data-upload-error]');
    let previewUrl;
    if (file) {
        function clearPreview() {
            if (previewUrl) URL.revokeObjectURL(previewUrl);
            previewUrl = null; preview.hidden = true;
        }
        file.addEventListener('change', () => {
            clearPreview(); uploadError.textContent = '';
            const image = file.files[0]; if (!image) return;
            if (image.size > 5 * 1024 * 1024 || !['image/jpeg','image/png'].includes(image.type)) {
                uploadError.textContent = zh ? '请选择不超过5MB的JPG或PNG图片。' : 'Choose a JPG or PNG image up to 5 MB.';
                file.value = ''; return;
            }
            previewUrl = URL.createObjectURL(image);
            preview.querySelector('img').src = previewUrl; preview.hidden = false;
        });
        document.querySelector('[data-remove-image]').addEventListener('click', () => { file.value=''; clearPreview(); });
        const composer = document.querySelector('.support-composer');
        let sending = false;
        composer.addEventListener('submit', async event => {
            event.preventDefault();
            if (sending) return;
            if (!document.getElementById('support-body').value.trim() && !file.files.length && !document.getElementById('support-order').value) {
                uploadError.textContent = zh ? '请填写消息、添加图片或选择订单。' : 'Add a message, image or order.';
                return;
            }
            sending = true;
            const button = composer.querySelector('[type="submit"]');
            button.disabled = true;
            uploadError.textContent = '';
            try {
                const response = await fetch(scoped(composer.action), {
                    method: 'POST', credentials: 'same-origin',
                    headers: { Accept: 'application/json',
                        [document.querySelector('meta[name="csrf-header"]').content]: document.querySelector('meta[name="csrf-token"]').content },
                    body: new FormData(composer)
                });
                if (response.status === 403 || response.status === 401 || response.redirected) {
                    uploadError.textContent = zh ? '登录状态或安全校验已过期，消息尚未发送。请在新标签页重新登录，再回来重试；输入内容已保留。' : 'Your session or security token has expired. Nothing was sent. Sign in in another tab, then return and retry. Your draft is preserved.';
                    const link = document.createElement('a');
                    link.href = scoped('/login'); link.target = '_blank'; link.rel = 'noopener';
                    link.textContent = zh ? ' 点击重新登录 ↗' : ' Sign in ↗';
                    uploadError.append(link);
                    return;
                }
                if (!response.headers.get('Content-Type')?.includes('application/json')) throw new Error('Unexpected response');
                const result = await response.json();
                if (!response.ok) {
                    uploadError.textContent = result.error || (zh ? '发送失败，请稍后重试。' : 'Unable to send. Please retry.');
                    return;
                }
                location.assign(scoped(result.redirect));
            } catch (_) {
                uploadError.textContent = zh ? '暂时无法确认发送结果，请检查网络后重试。输入内容已保留，重试不会重复发送。' : 'Unable to confirm delivery. Check your connection and retry. Your draft is preserved; retries will not duplicate the message.';
            } finally {
                sending = false; button.disabled = false;
            }
        });
        window.addEventListener('pagehide', clearPreview);
    }
    let busy = false;
    let acknowledged = 0;
    let reading = false;
    const atBottom = box => box.scrollHeight - box.scrollTop - box.clientHeight < 50;
    async function acknowledge() {
        const chat = document.getElementById('support-chat');
        const box = chat.querySelector('.support-messages');
        const through = Number(chat.dataset.through);
        if (document.hidden || !chat.dataset.thread || !atBottom(box) || through <= acknowledged || reading) return;
        reading = true;
        try {
            const response = await fetch(scoped(base + '/read'), {
                method: 'POST', credentials: 'same-origin',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded',
                    [document.querySelector('meta[name="csrf-header"]').content]: document.querySelector('meta[name="csrf-token"]').content },
                body: new URLSearchParams({id: chat.dataset.thread, through: String(through)})
            });
            if (response.ok) acknowledged = through;
        } catch (_) { /* Retry when the next refresh succeeds. */ }
        finally { reading = false; }
    }
    function bindHistory() {
        document.querySelector('.support-messages').addEventListener('scroll', acknowledge, {passive:true});
    }
    bindHistory();
    const initial = document.querySelector('.support-messages');
    initial.scrollTop = initial.scrollHeight;
    acknowledge();
    async function refresh() {
        if (busy || document.hidden) return;
        busy = true;
        try {
            const response = await fetch(scoped(base + '/updates' + location.search), {credentials:'same-origin',cache:'no-store'});
            if (!response.ok || response.redirected) throw new Error('Session or connection unavailable');
            const documentNext = new DOMParser().parseFromString(await response.text(), 'text/html');
            // The composer keeps its draft and idempotency token, but CSRF follows the current session.
            const nextCsrf = documentNext.querySelector('.support-composer input[name="_csrf"]');
            if (nextCsrf) {
                document.querySelector('meta[name="csrf-token"]').content = nextCsrf.value;
                const currentCsrf = document.querySelector('.support-composer input[name="_csrf"]');
                if (currentCsrf) currentCsrf.value = nextCsrf.value;
            }
            const nextChat = documentNext.getElementById('support-chat');
            const chat = document.getElementById('support-chat');
            if (!nextChat) throw new Error('No conversation');
            const box = chat.querySelector('.support-messages');
            const bottom = atBottom(box), position = box.scrollTop;
            // Avoid stealing keyboard focus while browsing message links or resolving a thread.
            if (!chat.contains(document.activeElement) && chat.dataset.older !== 'true') {
                chat.replaceWith(nextChat);
                const nextBox = nextChat.querySelector('.support-messages');
                nextBox.scrollTop = bottom ? nextBox.scrollHeight : position;
                bindHistory();
            }
            const inbox = document.getElementById('support-inbox');
            const nextInbox = documentNext.getElementById('support-inbox');
            if (inbox && nextInbox && !inbox.contains(document.activeElement)) inbox.replaceWith(nextInbox);
            connection.textContent = zh ? '对话已更新 · 每8秒自动同步' : 'Up to date · Refreshing every 8 seconds';
            await acknowledge();
        } catch (_) {
            connection.textContent = zh ? '暂时无法更新，请检查网络或重新登录。输入中的消息已保留。' : 'Unable to update. Check your connection or sign in again. Your draft is preserved.';
        } finally { busy = false; }
    }
    setInterval(refresh, 8000);
    document.addEventListener('visibilitychange', () => { if (!document.hidden) refresh(); });
})();
