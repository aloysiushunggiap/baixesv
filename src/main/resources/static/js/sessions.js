// Khởi tạo giao diện quản lý phiên đăng nhập cho Admin.
async function initSessionsPanel() {
    const loadButton = document.getElementById("loadSessionsButton");
    if (loadButton) {
        loadButton.addEventListener("click", () => loadActiveSessions(true));
    }

    await loadActiveSessions(false);
}

async function loadActiveSessions(showSuccess = true) {
    const message = document.getElementById("sessionsMessage");
    const tbody = document.getElementById("sessionsBody");
    const button = document.getElementById("loadSessionsButton");

    if (!tbody) return;

    if (!isAdmin()) {
        setMessage(message, "Chỉ admin được xem phiên đăng nhập.", "error");
        return;
    }

    await withButtonLoading(button, "Đang tải...", async () => {
        try {
            const data = await apiRequest("/api/sessions/active", { method: "GET" });

            if (!Array.isArray(data) || data.length === 0) {
                tbody.innerHTML = `<tr><td colspan="8" class="empty-row">Không có phiên đăng nhập còn hạn.</td></tr>`;
                setMessage(message, "Không có phiên đăng nhập còn hạn.", "");
                return;
            }

            tbody.innerHTML = data.map(session => `
              <tr>
                <td>${escapeHtml(session.sessionId)}</td>
                <td>${escapeHtml(session.username)}</td>
                <td>${escapeHtml(session.role)}</td>
                <td>${escapeHtml(session.cardId || "-")}</td>
                <td>${escapeHtml(formatDateTime(session.issuedAt))}</td>
                <td>${escapeHtml(formatDateTime(session.expiresAt))}</td>
                <td>${escapeHtml(formatDateTime(session.refreshExpiresAt))}</td>
                <td>
                  <button type="button" class="btn btn-danger" data-delete-session-id="${escapeHtml(session.sessionId)}">Xóa phiên</button>
                </td>
              </tr>
            `).join("");

            bindSessionActions();
            setMessage(message, "Tải danh sách phiên thành công.", "success");
            if (showSuccess) showToast("Đã tải danh sách phiên đăng nhập.", "success");
        } catch (error) {
            tbody.innerHTML = `<tr><td colspan="8" class="empty-row">${escapeHtml(error.message || "Không thể tải phiên đăng nhập.")}</td></tr>`;
            setMessage(message, error.message || "Không thể tải phiên đăng nhập.", "error");
        }
    });
}

function bindSessionActions() {
    document.querySelectorAll("[data-delete-session-id]").forEach(button => {
        button.addEventListener("click", () => deleteSession(button.dataset.deleteSessionId));
    });
}

async function deleteSession(sessionId) {
    const message = document.getElementById("sessionsMessage");

    if (!confirm(`Bạn có chắc muốn xóa phiên ${sessionId} không? Phiên tương ứng sẽ bị logout.`)) {
        return;
    }

    try {
        const result = await apiRequest(`/api/sessions/${encodeURIComponent(sessionId)}`, {
            method: "DELETE"
        });

        const successMessage = getApiMessage(result, "Đã xóa phiên đăng nhập.");
        setMessage(message, successMessage, "success");
        showToast(successMessage, "success");
        await loadActiveSessions(false);
    } catch (error) {
        setMessage(message, error.message || "Xóa phiên thất bại.", "error");
        showToast(error.message || "Xóa phiên thất bại.", "error");
    }
}
