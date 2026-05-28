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
let authVisibilityBound = false;
let isLoggingOut = false;

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

function parseServerDateTime(value) {
    if (!value) return null;

    const date = new Date(String(value).trim());

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
}

/*
 * Chỉ canh Access Token.
 *
 * Không được đặt timer logout theo refreshExpiresAt.
 * Vì RT hết hạn không có nghĩa là AT hiện tại phải logout ngay.
 *
 * Ví dụ:
 * AT = 2 phút, RT = 3 phút
 * Phút 2 refresh thành công, AT mới sống tới phút 4
 * Phút 3 RT hết hạn
 * Phút 3-4 vẫn phải dùng được AT mới
 * Phút 4 AT hết hạn, refresh fail vì RT hết hạn, lúc đó mới logout
 */
function scheduleAuthTimers() {
    clearAuthTimers();

    if (!state.username || !state.expiresAt) {
        return;
    }

    const accessMs = millisUntil(state.expiresAt);

    if (accessMs !== null && accessMs <= 0) {
        refreshAccessToken(true).catch(() => {
            forceLogout("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.", "error");
        });
        return;
    }

    if (accessMs !== null) {
        const refreshBeforeMs = Math.max(0, accessMs - 3000);

        accessTokenTimer = setTimeout(async () => {
            try {
                await refreshAccessToken(true);
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

        const accessMs = millisUntil(state.expiresAt);

        if (accessMs !== null && accessMs <= 0) {
            try {
                await refreshAccessToken(true);
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

async function restoreSessionFromCookie() {
    try {
        const data = await apiRequest("/api/auth/me", {
            method: "GET",
            suppressLogout: true
        });

        saveLoginSession(data);
        return true;
    } catch (error) {
        try {
            await refreshAccessToken(true);

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
                await refreshAccessToken(options.silent401 === true);

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

async function refreshAccessToken(silent = false) {
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

            if (!silent) {
                showToast(message, "error");
            }

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
        // Frontend vẫn xóa trạng thái cục bộ nếu backend không phản hồi.
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

function saveLoginSession(data) {
    if (!data) return;

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