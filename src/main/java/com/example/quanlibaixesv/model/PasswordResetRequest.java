package com.example.quanlibaixesv.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "password_reset_requests")
@Data
public class PasswordResetRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String studentId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String licensePlate;

    @Column(nullable = false)
    private String username;

    // Khong luu mat khau moi dang ro. Chi luu password da encode.
    @Column(nullable = false, length = 255)
    private String newPasswordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PasswordResetStatus status = PasswordResetStatus.PENDING;

    @Column(nullable = false)
    private LocalDateTime requestedAt;

    private LocalDateTime processedAt;

    private String processedBy;

    private String rejectReason;
}
