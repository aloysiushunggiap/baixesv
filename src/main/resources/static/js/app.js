// Điểm khởi động chính của frontend.
document.addEventListener("DOMContentLoaded", startApp);

// Khởi động app: nạp login, gắn layout, sau đó kiểm tra cookie đăng nhập hiện tại.
async function startApp() {
    await renderLoginView();
    bindLayoutEvents();

    const restored = await restoreSessionFromCookie();
    if (!restored) {
        showLoginView();
        return;
    }

    startTokenExpirationWatcher();
    await enterApplication();
}
