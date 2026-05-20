package com.example.quanlibaixesv.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_sessions")
@Data
public class UserSession {

    // sessionId dùng để định danh token đang đăng nhập.
    // Giá trị này cũng được lưu trong JWT claim "sid" và "jti".
    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String role;

    private String cardId;

    @Column(nullable = false)
    private long tokenVersion;

    @Column(nullable = false)
    private LocalDateTime issuedAt;

    // Thời điểm hết hạn của session được lấy đúng theo exp của JWT.
    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private boolean active = true;
}
