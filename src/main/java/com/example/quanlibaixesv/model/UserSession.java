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

    // sessionId: định danh cho một phiên đăng nhập.
    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String role;

    @Column(name = "card_id")
    private String cardId;

    @Column(nullable = false, name = "token_version")
    private long tokenVersion;

    @Column(nullable = false, name = "issued_at")
    private LocalDateTime issuedAt;


    @Column(nullable = false, name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(nullable = false, name = "refresh_token_hash", length = 255)
    private String refreshTokenHash;


    @Column(nullable = false, name = "refresh_expires_at")
    private LocalDateTime refreshExpiresAt;

    @Column(nullable = false)
    private boolean active = true;
}
