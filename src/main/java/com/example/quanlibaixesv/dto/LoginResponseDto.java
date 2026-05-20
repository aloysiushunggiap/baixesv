package com.example.quanlibaixesv.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class LoginResponseDto {
    private String token;
    private String username;
    private String role;
    private String sessionId;
    private LocalDateTime expiresAt;
}
