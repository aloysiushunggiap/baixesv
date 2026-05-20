// Khởi tạo giao diện sinh viên/thẻ và tài khoản admin.
function initStudentPanel() {
    const registerButton = document.getElementById("registerStudentButton");
    const deleteButton = document.getElementById("deleteCardButton");
    const registerAdminButton = document.getElementById("registerAdminButton");
    const deleteAdminButton = document.getElementById("deleteAdminButton");

    if (registerButton) registerButton.addEventListener("click", registerStudent);
    if (deleteButton) deleteButton.addEventListener("click", deleteCard);
    if (registerAdminButton) registerAdminButton.addEventListener("click", registerAdmin);
    if (deleteAdminButton) deleteAdminButton.addEventListener("click", deleteAdmin);
}

// Khởi tạo giao diện Yêu cầu quên mật khẩu cho Admin.
async function initPasswordRequestsPanel() {
    const loadButton = document.getElementById("loadPasswordRequestsButton");
    const statusFilter = document.getElementById("passwordRequestStatusFilter");

    if (loadButton) {
        loadButton.addEventListener("click", () => loadPasswordResetRequests(true));
    }

    if (statusFilter) {
        statusFilter.addEventListener("change", () => loadPasswordResetRequests(false));
    }

    await loadPasswordResetRequests(false);
}

// Đăng ký tài khoản sinh viên mới. Backend tự sinh cardId và cardSecret.
async function registerStudent() {
    const message = document.getElementById("studentMessage");
    const resultBox = document.getElementById("newStudentResult");
    const button = document.getElementById("registerStudentButton");

    if (!isAdmin()) {
        setMessage(message, "Chỉ admin được dùng chức năng này trên giao diện.", "error");
        return;
    }

    const studentId = document.getElementById("studentId").value.trim();
    const name = document.getElementById("studentName").value.trim();
    const licensePlate = document.getElementById("studentLicensePlate").value.trim();
    const username = document.getElementById("studentUsername").value.trim();
    const password = document.getElementById("studentPassword").value;

    if (!studentId || !name || !licensePlate || !username || !password) {
        setMessage(message, "Vui lòng nhập đủ thông tin sinh viên.", "error");
        return;
    }

    await withButtonLoading(button, "Đang đăng ký...", async () => {
        try {
            const data = await apiRequest("/api/auth/register", {
                method: "POST",
                body: {
                    role: "ROLE_USER",
                    studentId,
                    name,
                    licensePlate,
                    username,
                    password
                }
            });

            setMessage(message, data.message || "Đăng ký sinh viên thành công.", "success");
            resultBox.classList.remove("hidden");
            resultBox.innerHTML = `
              <strong>Thông tin sinh viên vừa tạo</strong><br>
              Username: ${escapeHtml(data.username)}<br>
              Mã sinh viên: ${escapeHtml(data.studentId)}<br>
              Card ID: <strong>${escapeHtml(data.cardId)}</strong><br>
              Biển số: ${escapeHtml(data.licensePlate)}
            `;

            clearStudentForm();
        } catch (error) {
            setMessage(message, error.message || "Đăng ký sinh viên thất bại.", "error");
        }
    });
}

// Đăng ký tài khoản admin mới.
async function registerAdmin() {
    const message = document.getElementById("adminRegisterMessage");
    const resultBox = document.getElementById("newAdminResult");
    const button = document.getElementById("registerAdminButton");
    const username = document.getElementById("adminUsername").value.trim();
    const password = document.getElementById("adminPassword").value;
    const confirmPassword = document.getElementById("adminPasswordConfirm").value;

    setMessage(message, "", "");

    if (!isAdmin()) {
        setMessage(message, "Chỉ admin được đăng ký tài khoản admin.", "error");
        return;
    }

    if (!username || !password || !confirmPassword) {
        setMessage(message, "Vui lòng nhập đủ username và mật khẩu admin.", "error");
        return;
    }

    if (password !== confirmPassword) {
        setMessage(message, "Mật khẩu nhập lại không khớp.", "error");
        return;
    }

    await withButtonLoading(button, "Đang đăng ký...", async () => {
        try {
            const data = await apiRequest("/api/auth/register", {
                method: "POST",
                body: { role: "ROLE_ADMIN", username, password }
            });

            setMessage(message, data.message || "Đăng ký admin thành công.", "success");
            showToast(data.message || "Đăng ký admin thành công.", "success");
            resultBox.classList.remove("hidden");
            resultBox.innerHTML = `
              <strong>Thông tin admin vừa tạo</strong><br>
              Username: ${escapeHtml(data.username)}<br>
              Role: ${escapeHtml(data.role)}
            `;

            clearAdminRegisterForm();
        } catch (error) {
            setMessage(message, error.message || "Đăng ký admin thất bại.", "error");
        }
    });
}

// Xóa dữ liệu trong form đăng ký sinh viên sau khi tạo thành công.
function clearStudentForm() {
    document.getElementById("studentId").value = "";
    document.getElementById("studentName").value = "";
    document.getElementById("studentLicensePlate").value = "";
    document.getElementById("studentUsername").value = "";
    document.getElementById("studentPassword").value = "";
}

// Xóa dữ liệu trong form đăng ký admin sau khi tạo thành công.
function clearAdminRegisterForm() {
    document.getElementById("adminUsername").value = "";
    document.getElementById("adminPassword").value = "";
    document.getElementById("adminPasswordConfirm").value = "";
}

// Xóa thẻ sinh viên.
async function deleteCard() {
    const message = document.getElementById("deleteCardMessage");
    const input = document.getElementById("deleteCardId");
    const button = document.getElementById("deleteCardButton");
    const cardId = input.value.trim();

    setMessage(message, "", "");

    if (!isAdmin()) {
        setMessage(message, "Chỉ admin được xóa thẻ.", "error");
        return;
    }

    if (!cardId) {
        setMessage(message, "Vui lòng nhập cardId cần xóa.", "error");
        input.focus();
        return;
    }

    if (!confirm(`Bạn có chắc muốn xóa thẻ ${cardId} không?`)) {
        return;
    }

    await withButtonLoading(button, "Đang xóa...", async () => {
        try {
            const result = await apiRequest(`/api/parking/delete-card?cardId=${encodeURIComponent(cardId)}`, {
                method: "DELETE"
            });

            const successMessage = getApiMessage(result, "Đã xóa thẻ và toàn bộ dữ liệu liên quan.");

            // Hỗ trợ backend cũ: nếu backend trả chuỗi lỗi nhưng HTTP 200 thì vẫn hiển thị lỗi.
            if (isTextErrorMessage(successMessage)) {
                throw new Error(successMessage);
            }

            setMessage(message, successMessage, "success");
            showToast(successMessage, "success");
            input.value = "";
            clearDeletedCardRelatedUI(cardId);
        } catch (error) {
            setMessage(message, error.message || "Xóa thẻ thất bại.", "error");
            showToast(error.message || "Xóa thẻ thất bại.", "error");
        }
    });
}

// Xóa tài khoản admin theo username.
async function deleteAdmin() {
    const message = document.getElementById("deleteAdminMessage");
    const input = document.getElementById("deleteAdminUsername");
    const button = document.getElementById("deleteAdminButton");
    const username = input.value.trim();

    setMessage(message, "", "");

    if (!isAdmin()) {
        setMessage(message, "Chỉ admin được xóa tài khoản admin.", "error");
        return;
    }

    if (!username) {
        setMessage(message, "Vui lòng nhập username admin cần xóa.", "error");
        input.focus();
        return;
    }

    if (!confirm(`Bạn có chắc muốn xóa tài khoản admin ${username} không?`)) {
        return;
    }

    await withButtonLoading(button, "Đang xóa...", async () => {
        try {
            const result = await apiRequest(`/api/account/admin/${encodeURIComponent(username)}`, {
                method: "DELETE"
            });

            const successMessage = getApiMessage(result, "Đã xóa tài khoản admin.");
            setMessage(message, successMessage, "success");
            showToast(successMessage, "success");
            input.value = "";
        } catch (error) {
            setMessage(message, error.message || "Xóa admin thất bại.", "error");
            showToast(error.message || "Xóa admin thất bại.", "error");
        }
    });
}

// Tải danh sách yêu cầu quên mật khẩu cho Admin.
async function loadPasswordResetRequests(showSuccess = true) {
    const message = document.getElementById("passwordRequestsMessage");
    const tbody = document.getElementById("passwordRequestsBody");
    const button = document.getElementById("loadPasswordRequestsButton");
    const statusFilter = document.getElementById("passwordRequestStatusFilter");

    if (!tbody) return;

    if (!isAdmin()) {
        setMessage(message, "Chỉ admin được xem yêu cầu quên mật khẩu.", "error");
        return;
    }

    const status = statusFilter ? statusFilter.value : "PENDING";
    const query = status && status !== "ALL" ? `?status=${encodeURIComponent(status)}` : "";

    await withButtonLoading(button, "Đang tải...", async () => {
        try {
            const data = await apiRequest(`/api/password-reset/admin/requests${query}`, {
                method: "GET"
            });

            if (!Array.isArray(data) || data.length === 0) {
                tbody.innerHTML = `<tr><td colspan="11" class="empty-row">Không có yêu cầu nào.</td></tr>`;
                setMessage(message, "Không có yêu cầu nào.", "");
                return;
            }

            tbody.innerHTML = data.map(request => renderPasswordRequestRow(request)).join("");
            bindPasswordRequestTableActions();
            setMessage(message, "Tải danh sách yêu cầu thành công.", "success");
            if (showSuccess) showToast("Đã tải danh sách yêu cầu.", "success");
        } catch (error) {
            tbody.innerHTML = `<tr><td colspan="11" class="empty-row">${escapeHtml(error.message || "Không thể tải yêu cầu.")}</td></tr>`;
            setMessage(message, error.message || "Không thể tải yêu cầu.", "error");
        }
    });
}

function renderPasswordRequestRow(request) {
    const status = request.status || "PENDING";
    const statusMeta = getPasswordResetStatusMeta(status);
    const canDecide = status === "PENDING";

    return `
      <tr>
        <td>${escapeHtml(request.id)}</td>
        <td>${escapeHtml(request.studentId)}</td>
        <td>${escapeHtml(request.name)}</td>
        <td>${escapeHtml(request.licensePlate)}</td>
        <td>${escapeHtml(request.username)}</td>
        <td><span class="badge ${statusMeta.className}">${statusMeta.label}</span></td>
        <td>${escapeHtml(formatDateTime(request.requestedAt))}</td>
        <td>${escapeHtml(request.processedBy || "-")}</td>
        <td>${escapeHtml(formatDateTime(request.processedAt) || "-")}</td>
        <td>${escapeHtml(request.rejectReason || "-")}</td>
        <td>
          ${canDecide ? `
            <div class="action-row">
              <button type="button" class="btn btn-success" data-approve-reset-id="${escapeHtml(request.id)}">Đồng ý</button>
              <button type="button" class="btn btn-danger" data-reject-reset-id="${escapeHtml(request.id)}">Từ chối</button>
            </div>
          ` : "-"}
        </td>
      </tr>
    `;
}

function getPasswordResetStatusMeta(status) {
    switch (status) {
        case "APPROVED":
            return { label: "Đã đồng ý", className: "green" };
        case "REJECTED":
            return { label: "Đã từ chối", className: "gray" };
        case "PENDING":
        default:
            return { label: "Đang chờ", className: "blue" };
    }
}

function bindPasswordRequestTableActions() {
    document.querySelectorAll("[data-approve-reset-id]").forEach(button => {
        button.addEventListener("click", () => decidePasswordResetRequest(button.dataset.approveResetId, true));
    });

    document.querySelectorAll("[data-reject-reset-id]").forEach(button => {
        button.addEventListener("click", () => decidePasswordResetRequest(button.dataset.rejectResetId, false));
    });
}

async function decidePasswordResetRequest(id, approved) {
    const message = document.getElementById("passwordRequestsMessage");

    if (!isAdmin()) {
        setMessage(message, "Chỉ admin được xử lý yêu cầu.", "error");
        return;
    }

    let reason = "";

    if (approved) {
        if (!confirm(`Đồng ý yêu cầu #${id}? Mật khẩu mới sẽ có hiệu lực và toàn bộ phiên cũ của user sẽ bị đăng xuất.`)) {
            return;
        }
    } else {
        reason = prompt("Nhập lý do từ chối yêu cầu:", "Thông tin không hợp lệ") || "";
        if (!reason.trim()) {
            showToast("Cần nhập lý do từ chối.", "error");
            return;
        }
    }

    try {
        const data = await apiRequest(`/api/password-reset/admin/requests/${encodeURIComponent(id)}/decision`, {
            method: "PUT",
            body: { approved, reason }
        });

        const successMessage = data.message || (approved ? "Đã đồng ý yêu cầu." : "Đã từ chối yêu cầu.");
        setMessage(message, successMessage, "success");
        showToast(successMessage, "success");
        await loadPasswordResetRequests(false);
    } catch (error) {
        setMessage(message, error.message || "Xử lý yêu cầu thất bại.", "error");
        showToast(error.message || "Xử lý yêu cầu thất bại.", "error");
    }
}

// Dọn các vùng giao diện có thể còn hiển thị dữ liệu của thẻ vừa xóa.
function clearDeletedCardRelatedUI(cardId) {
    const resultBox = document.getElementById("newStudentResult");
    if (resultBox && resultBox.innerText.includes(cardId)) {
        resultBox.classList.add("hidden");
        resultBox.innerHTML = "";
    }
}
