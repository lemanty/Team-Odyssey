/* Refresh only inbox and message history, never the customer's draft or send token. */
(() => {
    const root = document.querySelector('[data-support-base]');
    if (!root) return;
    const zh = document.documentElement.lang === 'zh-CN';
    const connection = root.querySelector('[data-connection]');
    const base = root.dataset.supportBase;
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
            const response = await fetch(base + '/read', {
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
            const response = await fetch(base + '/updates' + location.search, {credentials:'same-origin',cache:'no-store'});
            if (!response.ok || response.redirected) throw new Error('Session or connection unavailable');
            const documentNext = new DOMParser().parseFromString(await response.text(), 'text/html');
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
