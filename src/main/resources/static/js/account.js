// Khởi tạo giao diện đổi mật khẩu cho cả Admin và User.
function initAccountPanel() {
    showCurrentUser();

    setText("changePasswordCurrentUsername", state.username || localStorage.getItem("username") || "-");

    const usernameInput = document.getElementById("changePasswordUsername");
    if (usernameInput && isAdmin()) {
        usernameInput.value = state.username || localStorage.getItem("username") || "";
    }

    const changePasswordButton = document.getElementById("changePasswordButton");
    if (changePasswordButton) {
        changePasswordButton.addEventListener("click", changePassword);
    }
}

// User đổi mật khẩu của chính mình. Admin reset mật khẩu cho username được nhập.
async function changePassword() {
    const message = document.getElementById("changePasswordMessage");
    const button = document.getElementById("changePasswordButton");
    const newPasswordInput = document.getElementById("newPassword");
    const confirmInput = document.getElementById("confirmNewPassword");
    const oldPasswordInput = document.getElementById("oldPassword");
    const usernameInput = document.getElementById("changePasswordUsername");

    const currentUsername = state.username || localStorage.getItem("username") || "";
    const username = isAdmin()
        ? (usernameInput ? usernameInput.value.trim() : "")
        : currentUsername;
    const oldPassword = isUser() && oldPasswordInput ? oldPasswordInput.value : "";
    const newPassword = newPasswordInput ? newPasswordInput.value : "";
    const confirmNewPassword = confirmInput ? confirmInput.value : "";

    setMessage(message, "", "");

    if (!username) {
        setMessage(message, "Vui lòng nhập username cần đổi mật khẩu.", "error");
        return;
    }

    if (isUser() && !oldPassword) {
        setMessage(message, "User cần nhập mật khẩu cũ.", "error");
        return;
    }

    if (!newPassword || !confirmNewPassword) {
        setMessage(message, "Vui lòng nhập và xác nhận mật khẩu mới.", "error");
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

    if (newPassword !== confirmNewPassword) {
        setMessage(message, "Mật khẩu mới nhập lại không khớp.", "error");
        return;
    }

    await withButtonLoading(button, "Đang đổi...", async () => {
        try {
            const data = await apiRequest("/api/account/change-password", {
                method: "PUT",
                body: { username, oldPassword, newPassword }
            });

            const successMessage = data.message || "Đổi mật khẩu thành công.";
            setMessage(message, successMessage, "success");
            showToast(successMessage, "success");
            clearChangePasswordForm();

            // Nếu đổi mật khẩu của chính tài khoản đang đăng nhập thì token hiện tại sẽ bị vô hiệu.
            if (!isAdmin() || username === currentUsername) {
                setTimeout(() => {
                    logout("Mật khẩu đã thay đổi. Vui lòng đăng nhập lại bằng mật khẩu mới.", "success");
                }, 900);
            }
        } catch (error) {
            setMessage(message, error.message || "Đổi mật khẩu thất bại.", "error");
        }
    });
}

// Dọn password khỏi form sau khi đổi thành công.
function clearChangePasswordForm() {
    const oldPasswordInput = document.getElementById("oldPassword");
    const newPasswordInput = document.getElementById("newPassword");
    const confirmInput = document.getElementById("confirmNewPassword");

    if (oldPasswordInput) oldPasswordInput.value = "";
    if (newPasswordInput) newPasswordInput.value = "";
    if (confirmInput) confirmInput.value = "";
}
