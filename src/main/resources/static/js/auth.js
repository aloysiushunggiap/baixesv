// Nạp giao diện đăng nhập và gắn sự kiện submit cho form.
async function renderLoginView() {
    const loginView = document.getElementById("loginView");
    loginView.innerHTML = await loadHtml("/views/login.html");

    const loginForm = document.getElementById("loginForm");
    if (loginForm) {
        loginForm.addEventListener("submit", login);
    }

    const showForgotPasswordButton = document.getElementById("showForgotPasswordButton");
    if (showForgotPasswordButton) {
        showForgotPasswordButton.addEventListener("click", showForgotPasswordForm);
    }

    const backToLoginButton = document.getElementById("backToLoginButton");
    if (backToLoginButton) {
        backToLoginButton.addEventListener("click", showLoginFormOnly);
    }

    const forgotPasswordForm = document.getElementById("forgotPasswordForm");
    if (forgotPasswordForm) {
        forgotPasswordForm.addEventListener("submit", sendForgotPasswordRequest);
    }

    showLoginFormOnly();
}

// Chỉ hiển thị form đăng nhập, ẩn toàn bộ form quên mật khẩu.
function showLoginFormOnly() {
    const loginForm = document.getElementById("loginForm");
    const forgotPasswordBox = document.getElementById("forgotPasswordBox");
    const loginMessage = document.getElementById("loginMessage");
    const forgotPasswordMessage = document.getElementById("forgotPasswordMessage");

    if (loginForm) loginForm.classList.remove("hidden");
    if (forgotPasswordBox) forgotPasswordBox.classList.add("hidden");

    setMessage(loginMessage, "", "");
    setMessage(forgotPasswordMessage, "", "");
}

// Ẩn form đăng nhập và chỉ hiển thị form gửi yêu cầu quên mật khẩu.
function showForgotPasswordForm() {
    const loginForm = document.getElementById("loginForm");
    const forgotPasswordBox = document.getElementById("forgotPasswordBox");
    const forgotFullName = document.getElementById("forgotFullName");
    const loginMessage = document.getElementById("loginMessage");

    if (loginForm) loginForm.classList.add("hidden");
    if (forgotPasswordBox) forgotPasswordBox.classList.remove("hidden");

    setMessage(loginMessage, "", "");

    if (forgotFullName) {
        forgotFullName.focus();
    }
}

// Xử lý đăng nhập: gọi /api/auth/login, lưu token và mở dashboard.
async function login(event) {
    event.preventDefault();

    const username = document.getElementById("username").value.trim();
    const password = document.getElementById("password").value;
    const message = document.getElementById("loginMessage");
    const button = document.getElementById("loginButton");

    setMessage(message, "", "");

    if (!username || !password) {
        setMessage(message, "Vui lòng nhập username và password.", "error");
        return;
    }

    await withButtonLoading(button, "Đang đăng nhập...", async () => {
        try {
            const data = await apiRequest("/api/auth/login", {
                method: "POST",
                body: { username, password },
                auth: false
            });

            saveLoginSession(data);
            document.getElementById("password").value = "";
            setMessage(message, "Đăng nhập thành công.", "success");
            showToast("Đăng nhập thành công.", "success");
            await enterApplication();
        } catch (error) {
            setMessage(message, error.message || "Đăng nhập thất bại.", "error");
        }
    });
}

// Gửi yêu cầu quên mật khẩu để admin duyệt.
async function sendForgotPasswordRequest(event) {
    event.preventDefault();

    const message = document.getElementById("forgotPasswordMessage");
    const button = document.getElementById("sendForgotPasswordButton");
    const nameInput = document.getElementById("forgotFullName");
    const studentIdInput = document.getElementById("forgotStudentId");
    const licensePlateInput = document.getElementById("forgotLicensePlate");
    const newPasswordInput = document.getElementById("forgotNewPassword");
    const confirmPasswordInput = document.getElementById("forgotConfirmPassword");

    const name = nameInput ? nameInput.value.trim() : "";
    const studentId = studentIdInput ? studentIdInput.value.trim() : "";
    const licensePlate = licensePlateInput ? licensePlateInput.value.trim() : "";
    const newPassword = newPasswordInput ? newPasswordInput.value : "";
    const confirmPassword = confirmPasswordInput ? confirmPasswordInput.value : "";

    setMessage(message, "", "");

    if (!name || !studentId || !licensePlate || !newPassword || !confirmPassword) {
        setMessage(message, "Vui lòng nhập đầy đủ thông tin quên mật khẩu.", "error");
        return;
    }

    if (!/^\d{8}$/.test(studentId)) {
        setMessage(message, "Mã sinh viên phải gồm đúng 8 chữ số.", "error");
        return;
    }

    if (newPassword.length < 6) {
        setMessage(message, "Mật khẩu mới phải có ít nhất 6 ký tự.", "error");
        return;
    }

    if (newPassword.includes(" ")) {
        setMessage(message, "Mật khẩu mới không nên có dấu cách.", "error");
        return;
    }

    if (newPassword !== confirmPassword) {
        setMessage(message, "Mật khẩu mới nhập lại không khớp.", "error");
        return;
    }

    await withButtonLoading(button, "Đang gửi...", async () => {
        try {
            const data = await apiRequest("/api/password-reset/request", {
                method: "POST",
                auth: false,
                body: { name, studentId, licensePlate, newPassword }
            });

            setMessage(message, data.message || "Đã gửi yêu cầu cho admin. Vui lòng chờ phê duyệt.", "success");
            showToast("Đã gửi yêu cầu quên mật khẩu cho admin.", "success");
            clearForgotPasswordForm();
        } catch (error) {
            setMessage(message, error.message || "Gửi yêu cầu quên mật khẩu thất bại.", "error");
        }
    });
}

function clearForgotPasswordForm() {
    const forgotPasswordForm = document.getElementById("forgotPasswordForm");
    if (forgotPasswordForm) {
        forgotPasswordForm.reset();
    }
}

// Lưu thông tin đăng nhập vào state và localStorage.
function saveLoginSession(data) {
    state.token = data.token;
    state.username = data.username;
    state.role = data.role;

    const decoded = decodeJwtPayload(data.token);
    state.cardId = decoded.cardId || "";

    localStorage.setItem("token", state.token);
    localStorage.setItem("username", state.username);
    localStorage.setItem("role", state.role);
    localStorage.setItem("cardId", state.cardId);

    resetSessionActivityClock();
}

// Đăng xuất: xóa localStorage, quay lại màn hình login và xóa panel đang mở.
function logout(message = "Đã đăng xuất.", type = "success") {
    clearStoredSession();

    const panelHost = document.getElementById("panelHost");
    if (panelHost) panelHost.innerHTML = "";

    showLoginView();
    showToast(message, type);
}

// Khôi phục cardId từ JWT nếu localStorage chưa có.
function hydrateSessionFromToken() {
    if (!state.token) return;

    const decoded = decodeJwtPayload(state.token);
    if (!state.cardId && decoded.cardId) {
        state.cardId = decoded.cardId;
        localStorage.setItem("cardId", state.cardId);
    }
}