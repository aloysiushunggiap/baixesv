// Lưu trạng thái đăng nhập dùng chung cho toàn bộ giao diện.
// JWT không lưu trong localStorage nữa. Token nằm trong cookie HttpOnly do backend set.
const state = {
    token: "",
    username: localStorage.getItem("username") || "",
    role: localStorage.getItem("role") || "",
    cardId: localStorage.getItem("cardId") || "",
    sessionId: localStorage.getItem("sessionId") || "",
    expiresAt: localStorage.getItem("expiresAt") || "",
    refreshExpiresAt: localStorage.getItem("refreshExpiresAt") || "",
    activePanel: "dashboardPanel"
};

let refreshRequestPromise = null;
let accessTokenTimer = null;
let refreshTokenTimer = null;
let authVisibilityBound = false;
let isLoggingOut = false;

// Danh sách giao diện con: mỗi panel có file HTML riêng và hàm khởi tạo riêng.
const PANEL_CONFIGS = {
    dashboardPanel: {
        title: "Tổng quan",
        view: "/views/dashboard.html",
        init: "initDashboardPanel"
    },
    swipePanel: {
        title: "Quẹt thẻ test",
        view: "/views/swipe.html",
        init: "initSwipePanel"
    },
    pricingPanel: {
        title: "Bảng giá",
        view: "/views/pricing.html",
        init: "initPricingPanel"
    },
    simulatePanel: {
        title: "Mô phỏng tính phí",
        view: "/views/simulate.html",
        init: "initSimulatePanel",
        adminOnly: true
    },
    historyPanel: {
        title: "Lịch sử gửi xe",
        view: "/views/history.html",
        init: "initHistoryPanel"
    },
    studentPanel: {
        title: "Sinh viên / thẻ",
        view: "/views/students.html",
        init: "initStudentPanel",
        adminOnly: true
    },
    requestsPanel: {
        title: "Yêu cầu",
        view: "/views/requests.html",
        init: "initPasswordRequestsPanel",
        adminOnly: true
    },
    sessionsPanel: {
        title: "Phiên đăng nhập",
        view: "/views/sessions.html",
        init: "initSessionsPanel",
        adminOnly: true
    },
    accountPanel: {
        title: "Đổi mật khẩu",
        view: "/views/account.html",
        init: "initAccountPanel"
    }
};

// Nạp file HTML giao diện con từ thư mục /views.
async function loadHtml(url) {
    const response = await fetch(url, {
        cache: "no-store",
        credentials: "include"
    });

    if (!response.ok) {
        throw new Error("Không tải được giao diện: " + url);
    }

    return response.text();
}

// Giữ hàm này để không làm hỏng code cũ.
// Frontend không đọc JWT nữa.
function getToken() {
    return "";
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

function getDefaultHeaders() {
    return {
        "Content-Type": "application/json",
        "Accept": "application/json, text/plain, */*"
    };
}

// =====================
// AUTH TIMER / SYNC UI
// =====================

function parseServerDateTime(value) {
    if (!value) return null;

    const text = String(value).trim();
    const date = new Date(text);

    if (Number.isNaN(date.getTime())) {
        return null;
    }

    return date;
}

function millisUntil(value) {
    const date = parseServerDateTime(value);

    if (!date) {
        return null;
    }

    return date.getTime() - Date.now();
}

function clearAuthTimers() {
    if (accessTokenTimer) {
        clearTimeout(accessTokenTimer);
        accessTokenTimer = null;
    }

    if (refreshTokenTimer) {
        clearTimeout(refreshTokenTimer);
        refreshTokenTimer = null;
    }
}

function scheduleAuthTimers() {
    clearAuthTimers();

    if (!state.username || !state.refreshExpiresAt) {
        return;
    }

    const refreshMs = millisUntil(state.refreshExpiresAt);
    const accessMs = millisUntil(state.expiresAt);

    // Nếu Refresh Token đã hết hạn thì logout ngay khỏi giao diện.
    if (refreshMs !== null && refreshMs <= 0) {
        forceLogout("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.", "error");
        return;
    }

    // Đến hạn Refresh Token thì logout khỏi app, kể cả user không thao tác gì.
    if (refreshMs !== null) {
        refreshTokenTimer = setTimeout(() => {
            forceLogout("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.", "error");
        }, refreshMs);
    }

    // Gần hết Access Token thì tự refresh trước.
    // Nếu refresh thất bại nghĩa là RT cũng không còn hợp lệ -> logout.
    if (accessMs !== null) {
        const refreshBeforeMs = Math.max(0, accessMs - 3000);

        accessTokenTimer = setTimeout(async () => {
            try {
                await refreshAccessToken();
            } catch (error) {
                forceLogout("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.", "error");
            }
        }, refreshBeforeMs);
    }

    bindAuthVisibilityEvents();
}

function bindAuthVisibilityEvents() {
    if (authVisibilityBound) return;

    authVisibilityBound = true;

    const checkSessionWhenBack = async () => {
        if (!state.username) return;

        const refreshMs = millisUntil(state.refreshExpiresAt);

        if (refreshMs !== null && refreshMs <= 0) {
            forceLogout("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.", "error");
            return;
        }

        const accessMs = millisUntil(state.expiresAt);

        if (accessMs !== null && accessMs <= 0) {
            try {
                await refreshAccessToken();
            } catch (error) {
                forceLogout("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.", "error");
            }
        }
    };

    window.addEventListener("focus", checkSessionWhenBack);

    document.addEventListener("visibilitychange", () => {
        if (!document.hidden) {
            checkSessionWhenBack();
        }
    });
}

function startTokenExpirationWatcher() {
    scheduleAuthTimers();
}

// =====================
// RESTORE SESSION
// =====================

async function restoreSessionFromCookie() {
    const hasLocalSession =
        state.username ||
        localStorage.getItem("username") ||
        localStorage.getItem("sessionId") ||
        localStorage.getItem("refreshExpiresAt");

    if (!hasLocalSession) {
        clearStoredSession();
        return false;
    }

    const currentRefreshExpiresAt =
        state.refreshExpiresAt ||
        localStorage.getItem("refreshExpiresAt");

    const refreshMs = millisUntil(currentRefreshExpiresAt);

    // Nếu frontend biết RT đã hết hạn thì không cần gọi API nữa.
    if (refreshMs !== null && refreshMs <= 0) {
        clearStoredSession();
        return false;
    }

    try {
        /*
         * Thử gọi /me bằng Access Token trước.
         * Nếu AT hết hạn, apiRequest() sẽ tự gọi /api/auth/refresh,
         * sau đó gọi lại /me.
         */
        const data = await apiRequest("/api/auth/me", {
            method: "GET",
            suppressLogout: true
        });

        saveLoginSession(data);
        return true;
    } catch (error) {
        /*
         * Fallback:
         * Nếu /me lỗi, thử refresh trực tiếp.
         * Trường hợp này xử lý khi AT đã hết hạn nhưng RT vẫn còn hợp lệ.
         */
        try {
            await refreshAccessToken();

            const data = await apiRequest("/api/auth/me", {
                method: "GET",
                skipRefresh: true,
                suppressLogout: true
            });

            saveLoginSession(data);
            return true;
        } catch (refreshError) {
            clearStoredSession();
            return false;
        }
    }
}

// ==========
// API COMMON
// ==========

// Gọi API dùng chung.
// Nếu Access Token hết hạn, hàm này tự gọi /api/auth/refresh rồi gọi lại request cũ.
async function apiRequest(url, options = {}) {
    return rawApiRequest(url, options);
}

async function rawApiRequest(url, options = {}) {
    const method = options.method || "GET";
    const headers = options.headers || getDefaultHeaders();

    const fetchOptions = {
        method,
        headers,
        credentials: "include"
    };

    if (options.body !== undefined) {
        fetchOptions.body = JSON.stringify(options.body);
    }

    const response = await fetch(API_BASE_URL + url, fetchOptions);
    const data = await parseApiResponse(response);

    if (!response.ok) {
        const message = getApiMessage(data, "Yêu cầu thất bại.");

        if (response.status === 401 && shouldTryRefresh(url, options)) {
            try {
                await refreshAccessToken();

                return await rawApiRequest(url, {
                    ...options,
                    skipRefresh: true
                });
            } catch (refreshError) {
                if (!options.suppressLogout) {
                    forceLogout("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.", "error");
                }

                throw refreshError;
            }
        }

        if (response.status === 401 && !options.suppressLogout) {
            forceLogout("Phiên đăng nhập đã hết hạn hoặc đã bị đăng xuất. Vui lòng đăng nhập lại.", "error");
        }

        throw new Error(message);
    }

    return data;
}

function shouldTryRefresh(url, options = {}) {
    if (options.skipRefresh) return false;

    if (url === "/api/auth/login") return false;
    if (url === "/api/auth/refresh") return false;
    if (url === "/api/auth/logout") return false;
    if (url === "/api/password-reset/request") return false;

    return true;
}

async function refreshAccessToken() {
    if (refreshRequestPromise) {
        return refreshRequestPromise;
    }

    refreshRequestPromise = (async () => {
        const response = await fetch(API_BASE_URL + "/api/auth/refresh", {
            method: "POST",
            credentials: "include",
            headers: getDefaultHeaders()
        });

        const data = await parseApiResponse(response);

        if (!response.ok) {
            const message = getApiMessage(data, "Refresh Token đã hết hạn.");
            throw new Error(message);
        }

        saveLoginSession(data);
        return data;
    })();

    try {
        return await refreshRequestPromise;
    } finally {
        refreshRequestPromise = null;
    }
}

async function parseApiResponse(response) {
    const contentType = response.headers.get("content-type") || "";

    if (contentType.includes("application/json")) {
        return response.json();
    }

    const text = await response.text();

    if (!text) {
        return null;
    }

    return text;
}

function getApiMessage(data, fallback) {
    if (typeof data === "object" && data !== null) {
        return data.message || data.error || fallback;
    }

    return data || fallback;
}

function isTextErrorMessage(message) {
    const normalized = String(message || "").trim().toLowerCase();

    return normalized.startsWith("không")
        || normalized.startsWith("loi")
        || normalized.startsWith("lỗi");
}

function clearStoredSession() {
    clearAuthTimers();

    // Xóa cả token cũ nếu trình duyệt còn lưu từ phiên bản localStorage trước đây.
    localStorage.removeItem("token");
    localStorage.removeItem("username");
    localStorage.removeItem("role");
    localStorage.removeItem("cardId");
    localStorage.removeItem("sessionId");
    localStorage.removeItem("expiresAt");
    localStorage.removeItem("refreshExpiresAt");

    state.token = "";
    state.username = "";
    state.role = "";
    state.cardId = "";
    state.sessionId = "";
    state.expiresAt = "";
    state.refreshExpiresAt = "";
    state.activePanel = "dashboardPanel";
}

async function clearServerCookie() {
    try {
        await fetch(API_BASE_URL + "/api/auth/logout", {
            method: "POST",
            credentials: "include",
            headers: getDefaultHeaders()
        });
    } catch (error) {
        // Nếu backend tạm thời không phản hồi, frontend vẫn xóa trạng thái cục bộ.
    }
}

function forceLogout(message = "Đã đăng xuất.", type = "success") {
    if (isLoggingOut) return;

    isLoggingOut = true;

    clearAuthTimers();
    clearServerCookie();
    clearStoredSession();

    const panelHost = document.getElementById("panelHost");

    if (panelHost) {
        panelHost.innerHTML = "";
    }

    if (typeof showLoginView === "function") {
        showLoginView();
    }

    showToast(message, type);

    setTimeout(() => {
        isLoggingOut = false;
    }, 500);
}

// Gán thông tin đăng nhập trả về từ backend vào state/localStorage.
function saveLoginSession(data) {
    state.token = "";
    state.username = data.username || "";
    state.role = data.role || "";
    state.cardId = data.cardId || "";
    state.sessionId = data.sessionId || "";
    state.expiresAt = data.expiresAt || "";
    state.refreshExpiresAt = data.refreshExpiresAt || "";

    localStorage.removeItem("token");
    localStorage.setItem("username", state.username);
    localStorage.setItem("role", state.role);
    localStorage.setItem("cardId", state.cardId);
    localStorage.setItem("sessionId", state.sessionId);
    localStorage.setItem("expiresAt", state.expiresAt);
    localStorage.setItem("refreshExpiresAt", state.refreshExpiresAt);

    scheduleAuthTimers();
}

// Giữ hàm này để tránh lỗi với code cũ.
// Không dùng nữa vì JWT nằm trong HttpOnly cookie.
function decodeJwtPayload(token) {
    try {
        const payload = token.split(".")[1];

        if (!payload) {
            return {};
        }

        const base64 = payload.replace(/-/g, "+").replace(/_/g, "/");
        const padded = base64.padEnd(
            base64.length + (4 - base64.length % 4) % 4,
            "="
        );

        const json = atob(padded);
        const decoded = decodeURIComponent(
            Array.from(json).map(char => {
                return "%" + ("00" + char.charCodeAt(0).toString(16)).slice(-2);
            }).join("")
        );

        return JSON.parse(decoded);
    } catch (error) {
        return {};
    }
}

function setText(id, value) {
    const element = document.getElementById(id);

    if (element) {
        element.innerText = value;
    }
}

function setMessage(element, text, type) {
    if (!element) return;

    element.innerText = text || "";
    element.classList.remove("success", "error");

    if (type) {
        element.classList.add(type);
    }
}

function showToast(text, type = "") {
    const toast = document.getElementById("toast");

    if (!toast) return;

    toast.innerText = text;
    toast.className = "toast";

    if (type) {
        toast.classList.add(type);
    }

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