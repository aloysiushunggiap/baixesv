// Khởi tạo giao diện mô phỏng phí: gắn nút và đặt thời gian mặc định.
function initSimulatePanel() {
    setDefaultDateTimes();

    const simulateButton = document.getElementById("simulateButton");
    if (simulateButton) {
        simulateButton.addEventListener("click", simulateFee);
    }
}

// Gán mặc định check-in trước hiện tại 2 giờ và check-out là hiện tại.
function setDefaultDateTimes() {
    const checkIn = document.getElementById("checkInTime");
    const checkOut = document.getElementById("checkOutTime");
    if (!checkIn || !checkOut || checkIn.value || checkOut.value) return;

    const now = new Date();
    const before = new Date(now.getTime() - 2 * 60 * 60 * 1000);
    checkIn.value = toDateTimeLocal(before);
    checkOut.value = toDateTimeLocal(now);
}

// Gọi backend mô phỏng phí theo thời gian vào/ra đã chọn.
async function simulateFee() {
    const message = document.getElementById("simulateMessage");
    const tbody = document.getElementById("simulateBody");
    const button = document.getElementById("simulateButton");
    const checkInTime = document.getElementById("checkInTime").value;
    const checkOutTime = document.getElementById("checkOutTime").value;

    if (!isAdmin()) {
        setMessage(message, "Chỉ admin được mô phỏng phí theo SecurityConfig hiện tại.", "error");
        return;
    }

    if (!checkInTime || !checkOutTime) {
        setMessage(message, "Vui lòng chọn thời gian vào và thời gian ra.", "error");
        return;
    }

    await withButtonLoading(button, "Đang tính...", async () => {
        try {
            const data = await apiRequest("/api/parking/simulate-fee", {
                method: "POST",
                body: { checkInTime, checkOutTime }
            });

            setMessage(message, data.message || "Tính phí thành công.", "success");
            tbody.innerHTML = `
              <tr>
                <td>${escapeHtml(formatDateTime(data.checkInTime))}</td>
                <td>${escapeHtml(formatDateTime(data.checkOutTime))}</td>
                <td>${data.durationMinutes} phút</td>
                <td>${formatMoney(data.amount)}</td>
              </tr>
            `;
        } catch (error) {
            setMessage(message, error.message || "Tính phí thất bại.", "error");
        }
    });
}
