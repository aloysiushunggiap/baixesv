// Lưu trạng thái đăng nhập dùng chung cho toàn bộ giao diện.
const state = {
    token: localStorage.getItem("token") || "",
    username: localStorage.getItem("username") || "",
    role: localStorage.getItem("role") || "",
    cardId: localStorage.getItem("cardId") || "",
    sessionId: localStorage.getItem("sessionId") || "",
    expiresAt: localStorage.getItem("expiresAt") || "",
    tokenExpirationTimer: null,
    activePanel: "dashboardPanel"
};

// Danh sách giao diện con: mỗi panel có file HTML riêng và hàm khởi tạo riêng.
const PANEL_CONFIGS = {
    dashboardPanel: { title: "Tổng quan", view: "/views/dashboard.html", init: "initDashboardPanel" },
    swipePanel: { title: "Quẹt thẻ test", view: "/views/swipe.html", init: "initSwipePanel" },
    pricingPanel: { title: "Bảng giá", view: "/views/pricing.html", init: "initPricingPanel" },
    simulatePanel: { title: "Mô phỏng phí", view: "/views/simulate.html", init: "initSimulatePanel", adminOnly: true },
    historyPanel: { title: "Lịch sử gửi xe", view: "/views/history.html", init: "initHistoryPanel" },
    studentPanel: { title: "Sinh viên / thẻ", view: "/views/students.html", init: "initStudentPanel", adminOnly: true },
    requestsPanel: { title: "Yêu cầu", view: "/views/students.html", init: "initPasswordRequestsPanel", adminOnly: true },
    sessionsPanel: { title: "Phiên đăng nhập", view: "/views/sessions.html", init: "initSessionsPanel", adminOnly: true },
    accountPanel: { title: "Đổi mật khẩu", view: "/views/account.html", init: "initAccountPanel" }
};

// Nạp file HTML giao diện con từ thư mục /views.
async function loadHtml(url) {
    const response = await fetch(url, { cache: "no-store" });
    if (!response.ok) {
        throw new Error("Không tải được giao diện: " + url);
    }
    return response.text();
}

function getToken() {
    return state.token || localStorage.getItem("token") || "";
}

function getRole() {
    return state.role || localStorage.getItem("role") || "";
}

function isAdmin() {
    return getRole() === "ROLE_ADMIN";
}

function isUser() {
    return getRole() === "ROLE_USER";
}

function getAuthHeaders() {
    return {
        "Content-Type": "application/json",
        "Accept": "application/json, text/plain, */*",
        "Authorization": "Bearer " + getToken()
    };
}

// Gọi API dùng chung: không kiểm tra idle timeout ở frontend nữa.
// Nếu token hết exp hoặc sessionId bị xóa, backend trả 401 và frontend logout.
async function apiRequest(url, options = {}) {
    const method = options.method || "GET";
    const requiresAuth = options.auth !== false;
    const headers = requiresAuth
        ? getAuthHeaders()
        : { "Content-Type": "application/json", "Accept": "application/json, text/plain, */*" };

    const fetchOptions = { method, headers };

    if (options.body !== undefined) {
        fetchOptions.body = JSON.stringify(options.body);
    }

    const response = await fetch(API_BASE_URL + url, fetchOptions);
    const contentType = response.headers.get("content-type") || "";
    let data;

    if (contentType.includes("application/json")) {
        data = await response.json();
    } else {
        data = await response.text();
    }

    if (!response.ok) {
        const message = getApiMessage(data, "Yêu cầu thất bại.");

        if (response.status === 401) {
            forceLogout("Phiên đăng nhập đã hết hạn hoặc đã bị đăng xuất. Vui lòng đăng nhập lại.", "error");
        }

        throw new Error(message);
    }

    return data;
}

function getApiMessage(data, fallback) {
    if (typeof data === "object" && data !== null) {
        return data.message || data.error || fallback;
    }
    return data || fallback;
}

function isTextErrorMessage(message) {
    const normalized = String(message || "").trim().toLowerCase();
    return normalized.startsWith("không") || normalized.startsWith("loi") || normalized.startsWith("lỗi");
}

// Frontend hẹn giờ theo exp của token để tự quay về login khi token hết hạn.
// Backend vẫn là nơi quyết định cuối cùng: request có token hết hạn sẽ bị trả 401.
function startTokenExpirationWatcher() {
    clearTokenExpirationWatcher();

    if (!getToken()) return;

    const expiresMs = getTokenExpirationTimeMs();
    if (!expiresMs) return;

    const remainingMs = expiresMs - Date.now();
    if (remainingMs <= 0) {
        forceLogout("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.", "error");
        return;
    }

    state.tokenExpirationTimer = setTimeout(() => {
        forceLogout("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.", "error");
    }, remainingMs);
}

function clearTokenExpirationWatcher() {
    if (state.tokenExpirationTimer) {
        clearTimeout(state.tokenExpirationTimer);
        state.tokenExpirationTimer = null;
    }
}

function getTokenExpirationTimeMs() {
    const expiresAt = state.expiresAt || localStorage.getItem("expiresAt") || "";
    if (expiresAt) {
        const parsed = Date.parse(expiresAt);
        if (!Number.isNaN(parsed)) return parsed;
    }

    const decoded = decodeJwtPayload(getToken());
    if (decoded.exp) {
        return Number(decoded.exp) * 1000;
    }

    return 0;
}

function clearStoredSession() {
    localStorage.removeItem("token");
    localStorage.removeItem("username");
    localStorage.removeItem("role");
    localStorage.removeItem("cardId");
    localStorage.removeItem("sessionId");
    localStorage.removeItem("expiresAt");

    state.token = "";
    state.username = "";
    state.role = "";
    state.cardId = "";
    state.sessionId = "";
    state.expiresAt = "";
    state.activePanel = "dashboardPanel";

    clearTokenExpirationWatcher();
}

function forceLogout(message = "Đã đăng xuất.", type = "success") {
    if (typeof logout === "function") {
        logout(message, type);
        return;
    }

    clearStoredSession();
    if (typeof showLoginView === "function") showLoginView();
    showToast(message, type);
}

function decodeJwtPayload(token) {
    try {
        const payload = token.split(".")[1];
        if (!payload) return {};

        const base64 = payload.replace(/-/g, "+").replace(/_/g, "/");
        const padded = base64.padEnd(base64.length + (4 - base64.length % 4) % 4, "=");
        const json = atob(padded);
        const decoded = decodeURIComponent(Array.from(json).map(char => {
            return "%" + ("00" + char.charCodeAt(0).toString(16)).slice(-2);
        }).join(""));
        return JSON.parse(decoded);
    } catch (error) {
        return {};
    }
}

function setText(id, value) {
    const element = document.getElementById(id);
    if (element) element.innerText = value;
}

function setMessage(element, text, type) {
    if (!element) return;
    element.innerText = text || "";
    element.classList.remove("success", "error");
    if (type) element.classList.add(type);
}

function showToast(text, type = "") {
    const toast = document.getElementById("toast");
    if (!toast) return;

    toast.innerText = text;
    toast.className = "toast";
    if (type) toast.classList.add(type);

    clearTimeout(showToast.timer);
    showToast.timer = setTimeout(() => {
        toast.classList.add("hidden");
    }, 2600);
}

async function withButtonLoading(button, loadingText, callback) {
    if (!button) return callback();

    const oldText = button.innerText;
    button.disabled = true;
    button.innerText = loadingText;
    try {
        return await callback();
    } finally {
        button.disabled = false;
        button.innerText = oldText;
    }
}

function formatTime(value) {
    if (!value) return "";
    return String(value).slice(0, 5);
}

function formatDateTime(value) {
    if (!value) return "";
    return String(value).replace("T", " ").slice(0, 16);
}

function formatMoney(amount) {
    const number = Number(amount || 0);
    return number.toLocaleString("vi-VN") + "đ";
}

function toDateTimeLocal(date) {
    const pad = value => String(value).padStart(2, "0");
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function escapeHtml(value) {
    return String(value ?? "")
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}
