package com.example.quanlibaixesv.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class LoginResponseDto {
    // Khi dùng cookie HttpOnly, token không cần trả về frontend nữa.
    // Giữ field này để không phá cấu trúc response cũ nếu frontend/test cũ còn đọc.
    private String token;

    private String username;
    private String role;
    private String sessionId;
    private LocalDateTime expiresAt;
    private String cardId;
}
