// Điểm khởi động chính của frontend.
document.addEventListener("DOMContentLoaded", startApp);

// Khởi động app: nạp login, gắn layout, sau đó kiểm tra cookie đăng nhập với backend.
async function startApp() {
    await renderLoginView();
    bindLayoutEvents();

    try {
        const data = await apiRequest("/api/auth/me", {
            method: "GET",
            suppressLogout: true
        });

        saveLoginSession(data);
        await enterApplication();
    } catch (error) {
        clearStoredSession();
        showLoginView();
    }
}
