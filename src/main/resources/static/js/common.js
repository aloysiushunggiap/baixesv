// Lưu trạng thái đăng nhập dùng chung cho toàn bộ giao diện.
const state = {
    token: localStorage.getItem("token") || "",
    username: localStorage.getItem("username") || "",
    role: localStorage.getItem("role") || "",
    cardId: localStorage.getItem("cardId") || "",
    activePanel: "dashboardPanel",
    lastActivityAt: Number(localStorage.getItem("lastActivityAt") || 0),
    idleTimer: null,
    idleEventsBound: false
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

// Lấy token đang lưu trong state hoặc localStorage.
function getToken() {
    return state.token || localStorage.getItem("token") || "";
}

// Lấy role hiện tại để ẩn/hiện chức năng.
function getRole() {
    return state.role || localStorage.getItem("role") || "";
}

// Kiểm tra tài khoản hiện tại có phải Admin không.
function isAdmin() {
    return getRole() === "ROLE_ADMIN";
}

// Kiểm tra tài khoản hiện tại có phải User sinh viên không.
function isUser() {
    return getRole() === "ROLE_USER";
}

// Tạo header mặc định cho API cần đăng nhập.
function getAuthHeaders() {
    return {
        "Content-Type": "application/json",
        "Accept": "application/json, text/plain, */*",
        "Authorization": "Bearer " + getToken()
    };
}

// Gọi API dùng chung: tự parse JSON/text và ném lỗi theo message backend trả về.
async function apiRequest(url, options = {}) {
    const method = options.method || "GET";
    const requiresAuth = options.auth !== false;
    const headers = requiresAuth
        ? getAuthHeaders()
        : { "Content-Type": "application/json", "Accept": "application/json, text/plain, */*" };

    if (requiresAuth && isIdleSessionExpired()) {
        forceLogout("Bạn đã không thao tác trong 5 phút. Vui lòng đăng nhập lại.", "error");
        throw new Error("Phiên đăng nhập đã hết hạn do không thao tác trong 5 phút.");
    }

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

    if (requiresAuth) {
        markSessionActivity();
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

// Khởi tạo cơ chế tự logout sau 5 phút không thao tác ở frontend.
function initIdleLogout() {
    if (state.idleEventsBound) {
        scheduleIdleLogoutTimer();
        return;
    }

    ["click", "mousemove", "keydown", "scroll", "touchstart"].forEach(eventName => {
        document.addEventListener(eventName, handleUserActivityForIdleLogout, true);
    });

    state.idleEventsBound = true;
    scheduleIdleLogoutTimer();
}

// Ghi nhận người dùng vừa thao tác. Nếu đã quá 5 phút thì logout thay vì gia hạn.
function handleUserActivityForIdleLogout() {
    if (!getToken()) return;

    if (isIdleSessionExpired()) {
        forceLogout("Bạn đã không thao tác trong 5 phút. Vui lòng đăng nhập lại.", "error");
        return;
    }

    markSessionActivity();
}

// Cập nhật mốc hoạt động cuối cùng và hẹn lại timer logout.
function markSessionActivity() {
    if (!getToken()) return;

    const now = Date.now();

    // Tránh ghi localStorage quá nhiều khi mousemove liên tục.
    if (state.lastActivityAt && now - state.lastActivityAt < 1000) {
        scheduleIdleLogoutTimer();
        return;
    }

    state.lastActivityAt = now;
    localStorage.setItem("lastActivityAt", String(now));
    scheduleIdleLogoutTimer();
}

function resetSessionActivityClock() {
    if (!getToken()) return;

    state.lastActivityAt = Date.now();
    localStorage.setItem("lastActivityAt", String(state.lastActivityAt));
    scheduleIdleLogoutTimer();
}

function getIdleTimeoutMs() {
    if (typeof SESSION_IDLE_TIMEOUT_MS !== "undefined") {
        return SESSION_IDLE_TIMEOUT_MS;
    }
    return 5 * 60 * 1000;
}

function isIdleSessionExpired() {
    if (!getToken()) return false;

    const lastActivity = state.lastActivityAt || Number(localStorage.getItem("lastActivityAt") || 0);
    if (!lastActivity) return false;

    return Date.now() - lastActivity >= getIdleTimeoutMs();
}

function scheduleIdleLogoutTimer() {
    clearIdleLogoutTimer();

    if (!getToken()) return;

    const lastActivity = state.lastActivityAt || Number(localStorage.getItem("lastActivityAt") || Date.now());
    const remainingMs = getIdleTimeoutMs() - (Date.now() - lastActivity);

    if (remainingMs <= 0) {
        forceLogout("Bạn đã không thao tác trong 5 phút. Vui lòng đăng nhập lại.", "error");
        return;
    }

    state.idleTimer = setTimeout(() => {
        forceLogout("Bạn đã không thao tác trong 5 phút. Vui lòng đăng nhập lại.", "error");
    }, remainingMs);
}

function clearIdleLogoutTimer() {
    if (state.idleTimer) {
        clearTimeout(state.idleTimer);
        state.idleTimer = null;
    }
}

// Xóa toàn bộ thông tin đăng nhập khỏi frontend.
function clearStoredSession() {
    localStorage.removeItem("token");
    localStorage.removeItem("username");
    localStorage.removeItem("role");
    localStorage.removeItem("cardId");
    localStorage.removeItem("lastActivityAt");

    state.token = "";
    state.username = "";
    state.role = "";
    state.cardId = "";
    state.activePanel = "dashboardPanel";
    state.lastActivityAt = 0;

    clearIdleLogoutTimer();
}

// Logout dùng chung khi token hết hạn, bị backend vô hiệu hoặc frontend idle quá 5 phút.
function forceLogout(message = "Đã đăng xuất.", type = "success") {
    if (typeof logout === "function") {
        logout(message, type);
        return;
    }

    clearStoredSession();
    if (typeof showLoginView === "function") showLoginView();
    showToast(message, type);
}

// Giải mã payload JWT để lấy cardId của user.
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

// Gán text an toàn cho một element theo id.
function setText(id, value) {
    const element = document.getElementById(id);
    if (element) element.innerText = value;
}

// Hiển thị message dưới form, đồng thời gỡ class cũ trước khi thêm class mới.
function setMessage(element, text, type) {
    if (!element) return;
    element.innerText = text || "";
    element.classList.remove("success", "error");
    if (type) element.classList.add(type);
}

// Hiển thị toast ngắn ở góc màn hình.
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

// Đổi trạng thái nút khi đang gọi API để tránh bấm nhiều lần.
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

// Chuyển LocalTime/ISO time về dạng HH:mm để đưa vào input type=time.
function formatTime(value) {
    if (!value) return "";
    return String(value).slice(0, 5);
}

// Chuyển LocalDateTime từ backend về dạng dễ đọc.
function formatDateTime(value) {
    if (!value) return "";
    return String(value).replace("T", " ").slice(0, 16);
}

// Định dạng tiền theo kiểu Việt Nam.
function formatMoney(amount) {
    const number = Number(amount || 0);
    return number.toLocaleString("vi-VN") + "đ";
}

// Chuyển Date JavaScript sang format dùng cho input datetime-local.
function toDateTimeLocal(date) {
    const pad = value => String(value).padStart(2, "0");
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

// Escape chuỗi trước khi đưa vào innerHTML để tránh chèn HTML/script ngoài ý muốn.
function escapeHtml(value) {
    return String(value ?? "")
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}
