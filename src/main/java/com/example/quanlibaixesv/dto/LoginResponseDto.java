package com.example.quanlibaixesv.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class LoginResponseDto {
    // Khi dùng cookie HttpOnly, Access Token không cần trả về frontend nữa.
    // Giữ field này để không phá cấu trúc response cũ nếu test cũ còn đọc.
    private String token;

    private String username;
    private String role;
    private String sessionId;

    // Thời điểm hết hạn Access Token hiện tại.
    private LocalDateTime expiresAt;

    private String cardId;

    // Thời điểm hết hạn Refresh Token.
    private LocalDateTime refreshExpiresAt;
}
