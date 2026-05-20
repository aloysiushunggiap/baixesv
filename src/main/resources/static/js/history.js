// Khởi tạo giao diện lịch sử: gắn nút xem lịch sử và hiển thị cardId user.
function initHistoryPanel() {
    showCurrentUser();

    const loadHistoryButton = document.getElementById("loadHistoryButton");
    if (loadHistoryButton) {
        loadHistoryButton.addEventListener("click", loadHistory);
    }
}

// Tải lịch sử: Admin truyền cardId, User không được truyền cardId khác.
async function loadHistory() {
    const message = document.getElementById("historyMessage");
    const tbody = document.getElementById("historyBody");
    const button = document.getElementById("loadHistoryButton");
    let url = "/api/parking/history";

    if (isAdmin()) {
        const cardId = document.getElementById("historyCardId").value.trim();
        if (!cardId) {
            setMessage(message, "Admin cần nhập cardId để xem lịch sử.", "error");
            return;
        }
        url += `?cardId=${encodeURIComponent(cardId)}`;
    }

    await withButtonLoading(button, "Đang tải...", async () => {
        try {
            const data = await apiRequest(url, { method: "GET" });

            if (!Array.isArray(data) || data.length === 0) {
                tbody.innerHTML = `<tr><td colspan="5" class="empty-row">Không có dữ liệu lịch sử.</td></tr>`;
                setMessage(message, "Không có dữ liệu lịch sử.", "");
                return;
            }

            tbody.innerHTML = data.map(item => `
              <tr>
                <td>${escapeHtml(item.cardId)}</td>
                <td>${item.month}</td>
                <td>${item.year}</td>
                <td>${item.totalMinutes} phút</td>
                <td>${formatMoney(item.totalAmount)}</td>
              </tr>
            `).join("");

            setMessage(message, "Tải lịch sử thành công.", "success");
        } catch (error) {
            setMessage(message, error.message || "Không thể xem lịch sử.", "error");
        }
    });
}
