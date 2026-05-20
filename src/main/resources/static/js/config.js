// Cấu hình gốc của frontend.
// Nếu frontend chạy cùng domain với Spring Boot thì giữ rỗng.
// Nếu tách frontend riêng, đổi thành ví dụ: "http://localhost:8080".
const API_BASE_URL = "";

// Thời gian tự đăng xuất khi không thao tác: 5 phút.
const SESSION_IDLE_TIMEOUT_MS = 5 * 60 * 1000;
