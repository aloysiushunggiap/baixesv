// Hiển thị màn hình đăng nhập và ẩn app chính.
function showLoginView() {
    document.getElementById("loginView").classList.remove("hidden");
    document.getElementById("appView").classList.add("hidden");
}

// Hiển thị app chính và ẩn màn hình đăng nhập.
function showAppView() {
    document.getElementById("loginView").classList.add("hidden");
    document.getElementById("appView").classList.remove("hidden");
}

// Gắn sự kiện cho menu trái và nút đăng xuất.
function bindLayoutEvents() {
    document.querySelectorAll(".nav-item").forEach(button => {
        button.addEventListener("click", () => openPanel(button.dataset.panel));
    });

    const logoutButton = document.getElementById("logoutButton");
    if (logoutButton) {
        logoutButton.addEventListener("click", () => logout());
    }
}

// Mở một giao diện con bằng cách nạp file HTML riêng trong /views.
async function openPanel(panelId) {
    const requestedConfig = PANEL_CONFIGS[panelId] || PANEL_CONFIGS.dashboardPanel;

    if (requestedConfig.adminOnly && !isAdmin()) {
        showToast("Bạn không có quyền mở chức năng này.", "error");
        panelId = "dashboardPanel";
    }

    const finalConfig = PANEL_CONFIGS[panelId] || PANEL_CONFIGS.dashboardPanel;
    const panelHost = document.getElementById("panelHost");

    try {
        panelHost.innerHTML = await loadHtml(finalConfig.view);
        showOnlyRequestedPanel(panelId);
        state.activePanel = panelId;
        setText("pageTitle", finalConfig.title);
        updateActiveMenu(panelId);
        applyRoleUI();
        showCurrentUser();

        const initFunction = window[finalConfig.init];
        if (typeof initFunction === "function") {
            await initFunction();
        }
    } catch (error) {
        panelHost.innerHTML = `<div class="card"><p class="message error">${escapeHtml(error.message)}</p></div>`;
    }
}

// Nếu một file HTML chứa nhiều panel, chỉ hiện panel đang được mở.
function showOnlyRequestedPanel(panelId) {
    const panelHost = document.getElementById("panelHost");
    if (!panelHost) return;

    const panels = panelHost.querySelectorAll(".panel");
    if (panels.length <= 1) return;

    panels.forEach(panel => {
        panel.classList.toggle("hidden", panel.id !== panelId);
    });
}

// Đánh dấu menu đang được chọn.
function updateActiveMenu(panelId) {
    document.querySelectorAll(".nav-item").forEach(button => {
        button.classList.toggle("active", button.dataset.panel === panelId);
    });
}

// Ẩn/hiện các thành phần theo role hiện tại.
function applyRoleUI() {
    const admin = isAdmin();
    const user = isUser();

    document.querySelectorAll(".admin-only").forEach(element => {
        element.classList.toggle("hidden", !admin);
    });

    document.querySelectorAll(".user-only").forEach(element => {
        element.classList.toggle("hidden", !user);
    });

    document.querySelectorAll(".admin-only-table").forEach(element => {
        element.classList.toggle("hidden", !admin);
    });
}

// Cập nhật thông tin user ở topbar, sidebar và các panel có liên quan.
function showCurrentUser() {
    const username = state.username || localStorage.getItem("username") || "-";
    const role = state.role || localStorage.getItem("role") || "-";
    const cardId = state.cardId || localStorage.getItem("cardId") || "-";

    setText("currentUsername", username);
    setText("currentRole", role);
    setText("sidebarRole", role);
    setText("welcomeText", "Xin chào, " + username);
    setText("dashboardUsername", username);
    setText("dashboardRole", role);
    setText("dashboardCardId", cardId || "-");
    setText("userSwipeCardId", cardId || "Không tìm thấy cardId trong token");
    setText("historyUserCardId", cardId || "Không tìm thấy cardId trong token");
}

// Đi vào app sau khi đã xác thực cookie hợp lệ với backend.
async function enterApplication() {
    showAppView();
    applyRoleUI();
    showCurrentUser();
    await openPanel("dashboardPanel");
}
