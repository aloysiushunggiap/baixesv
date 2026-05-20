// Khởi tạo giao diện bảng giá: gắn các nút và tải danh sách hiện tại.
async function initPricingPanel() {
    const resetButton = document.getElementById("resetPricingButton");
    const saveButton = document.getElementById("savePricingButton");
    const loadButton = document.getElementById("loadPricingButton");

    if (resetButton) resetButton.addEventListener("click", resetPricingForm);
    if (saveButton) saveButton.addEventListener("click", savePricingRule);
    if (loadButton) loadButton.addEventListener("click", () => loadPricingRules(true));

    await loadPricingRules(false);
}

// Tải toàn bộ bảng giá từ backend và render ra bảng.
async function loadPricingRules(showSuccess = true) {
    const tbody = document.getElementById("pricingBody");
    if (!tbody) return;

    try {
        const data = await apiRequest("/api/pricing", { method: "GET" });

        if (!Array.isArray(data) || data.length === 0) {
            tbody.innerHTML = `<tr><td colspan="8" class="empty-row">Chưa có khung giá nào.</td></tr>`;
            return;
        }

        tbody.innerHTML = data.map(rule => `
          <tr>
            <td>${rule.id}</td>
            <td>${escapeHtml(formatTime(rule.startTime))}</td>
            <td>${escapeHtml(formatTime(rule.endTime))}</td>
            <td>${formatMoney(rule.pricePerHour)}</td>
            <td>${rule.weekday ? "Có" : "Không"}</td>
            <td>${rule.weekend ? "Có" : "Không"}</td>
            <td><span class="badge ${rule.active ? "green" : "gray"}">${rule.active ? "Đang áp dụng" : "Tạm tắt"}</span></td>
            <td class="admin-only-table ${isAdmin() ? "" : "hidden"}">
              <div class="action-row">
                <button type="button" class="btn btn-warning" data-edit-rule='${escapeHtml(JSON.stringify(rule))}'>Sửa</button>
                <button type="button" class="btn btn-danger" data-delete-rule-id="${rule.id}">Xóa</button>
              </div>
            </td>
          </tr>
        `).join("");

        bindPricingTableActions();
        applyRoleUI();
        if (showSuccess) showToast("Đã tải bảng giá.", "success");
    } catch (error) {
        tbody.innerHTML = `<tr><td colspan="8" class="empty-row">${escapeHtml(error.message)}</td></tr>`;
    }
}

// Gắn sự kiện Sửa/Xóa cho từng dòng bảng giá sau khi render HTML.
function bindPricingTableActions() {
    document.querySelectorAll("[data-edit-rule]").forEach(button => {
        button.addEventListener("click", () => fillPricingForm(JSON.parse(button.dataset.editRule)));
    });

    document.querySelectorAll("[data-delete-rule-id]").forEach(button => {
        button.addEventListener("click", () => deletePricingRule(button.dataset.deleteRuleId));
    });
}

// Lưu khung giá: nếu có id thì cập nhật, không có id thì thêm mới.
async function savePricingRule() {
    const message = document.getElementById("pricingMessage");
    const id = document.getElementById("priceRuleId").value;
    const startTime = document.getElementById("priceStartTime").value;
    const endTime = document.getElementById("priceEndTime").value;
    const pricePerHour = Number(document.getElementById("pricePerHour").value);
    const weekday = document.getElementById("priceWeekday").checked;
    const weekend = document.getElementById("priceWeekend").checked;
    const active = document.getElementById("priceActive").checked;
    const button = document.getElementById("savePricingButton");

    if (!isAdmin()) {
        setMessage(message, "Chỉ admin được lưu bảng giá.", "error");
        return;
    }

    if (!startTime || !endTime || Number.isNaN(pricePerHour)) {
        setMessage(message, "Vui lòng nhập đủ giờ bắt đầu, giờ kết thúc và giá.", "error");
        return;
    }

    if (!weekday && !weekend) {
        setMessage(message, "Phải chọn ngày thường hoặc cuối tuần.", "error");
        return;
    }

    await withButtonLoading(button, "Đang lưu...", async () => {
        try {
            const body = { startTime, endTime, pricePerHour, weekday, weekend, active };
            const url = id ? `/api/pricing/${id}` : "/api/pricing";
            const method = id ? "PUT" : "POST";

            await apiRequest(url, { method, body });
            setMessage(message, id ? "Cập nhật khung giá thành công." : "Thêm khung giá thành công.", "success");
            resetPricingForm();
            await loadPricingRules(false);
        } catch (error) {
            setMessage(message, error.message || "Lưu khung giá thất bại.", "error");
        }
    });
}

// Đưa dữ liệu một rule lên form để sửa.
function fillPricingForm(rule) {
    document.getElementById("priceRuleId").value = rule.id;
    document.getElementById("priceStartTime").value = formatTime(rule.startTime);
    document.getElementById("priceEndTime").value = formatTime(rule.endTime);
    document.getElementById("pricePerHour").value = rule.pricePerHour;
    document.getElementById("priceWeekday").checked = !!rule.weekday;
    document.getElementById("priceWeekend").checked = !!rule.weekend;
    document.getElementById("priceActive").checked = !!rule.active;
    setText("pricingFormTitle", "Sửa khung giá ID " + rule.id);
    setMessage(document.getElementById("pricingMessage"), "", "");
}

// Reset form về trạng thái thêm mới.
function resetPricingForm() {
    document.getElementById("priceRuleId").value = "";
    document.getElementById("priceStartTime").value = "";
    document.getElementById("priceEndTime").value = "";
    document.getElementById("pricePerHour").value = "";
    document.getElementById("priceWeekday").checked = false;
    document.getElementById("priceWeekend").checked = false;
    document.getElementById("priceActive").checked = true;
    setText("pricingFormTitle", "Thêm khung giá");
    setMessage(document.getElementById("pricingMessage"), "", "");
}

// Xóa một khung giá theo id và tải lại danh sách.
async function deletePricingRule(id) {
    if (!isAdmin()) {
        showToast("Chỉ admin được xóa bảng giá.", "error");
        return;
    }

    if (!confirm("Bạn có chắc muốn xóa khung giá này không?")) {
        return;
    }

    try {
        const result = await apiRequest(`/api/pricing/${id}`, { method: "DELETE" });
        const message = getApiMessage(result, "Đã xóa khung giá.");
        showToast(message, isTextErrorMessage(message) ? "error" : "success");
        await loadPricingRules(false);
    } catch (error) {
        showToast(error.message || "Xóa thất bại.", "error");
    }
}
