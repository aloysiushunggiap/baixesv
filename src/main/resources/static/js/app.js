// Điểm khởi động chính của frontend.
document.addEventListener("DOMContentLoaded", startApp);

// Khởi động app: nạp login, gắn layout, sau đó quyết định vào app hay ở login.
async function startApp() {
    await renderLoginView();
    bindLayoutEvents();

    if (!state.token) {
        showLoginView();
        return;
    }

    startTokenExpirationWatcher();
    await enterApplication();
}
