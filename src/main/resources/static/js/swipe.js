// Khởi tạo giao diện quẹt thẻ: gắn nút bấm và hiển thị cardId user.
function initSwipePanel() {
    showCurrentUser();

    const swipeButton = document.getElementById("swipeButton");
    if (swipeButton) {
        swipeButton.addEventListener("click", swipeCardTest);
    }
}

// Test quẹt thẻ: Admin dùng cardId nhập tay, User dùng cardId trong token.
async function swipeCardTest() {
    const message = document.getElementById("swipeMessage");
    const tbody = document.getElementById("swipeResultBody");
    const button = document.getElementById("swipeButton");
    let cardId = "";

    if (isAdmin()) {
        cardId = document.getElementById("swipeCardId").value.trim();
    } else {
        cardId = state.cardId || localStorage.getItem("cardId") || "";
    }

    if (!cardId) {
        setMessage(message, isAdmin() ? "Admin cần nhập cardId trước khi test." : "Không tìm thấy cardId của user trong token.", "error");
        return;
    }

    await withButtonLoading(button, "Đang quẹt...", async () => {
        try {
            const data = await apiRequest(`/api/parking/swipe?cardId=${encodeURIComponent(cardId)}`, {
                method: "POST"
            });

            setMessage(message, data.message || "Quẹt thẻ thành công.", "success");
            tbody.innerHTML = `
              <tr>
                <td>${escapeHtml(data.cardId)}</td>
                <td><span class="badge ${data.action === "CHECK_IN" ? "green" : "blue"}">${escapeHtml(data.action)}</span></td>
                <td>${data.durationMinutes} phút</td>
                <td>${formatMoney(data.amount)}</td>
                <td>${escapeHtml(data.message)}</td>
              </tr>
            `;
        } catch (error) {
            setMessage(message, error.message || "Quẹt thẻ thất bại.", "error");
        }
    });
}
