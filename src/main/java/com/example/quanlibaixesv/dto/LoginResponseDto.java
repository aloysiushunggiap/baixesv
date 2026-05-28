package com.example.quanlibaixesv.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class LoginResponseDto {
    // Token không trả ra frontend nữa vì đã nằm trong HttpOnly Cookie.
    // Giữ field này = null để không làm hỏng cấu trúc response cũ nếu frontend có đọc.
    private String token;

    private String username;
    private String role;
    private String sessionId;

    // Thời điểm Access Token hết hạn.
    private LocalDateTime expiresAt;

    // Chỉ user sinh viên có cardId.
    private String cardId;

    // Thời điểm Refresh Token hết hạn.
    private LocalDateTime refreshExpiresAt;
}
