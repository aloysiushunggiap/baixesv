package com.example.quanlibaixesv.dto;

import com.example.quanlibaixesv.model.PasswordResetRequest;
import com.example.quanlibaixesv.model.PasswordResetStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class PasswordResetResponseDto {
    private String message;
    private Long id;
    private String studentId;
    private String name;
    private String licensePlate;
    private String username;
    private PasswordResetStatus status;
    private LocalDateTime requestedAt;
    private LocalDateTime processedAt;
    private String processedBy;
    private String rejectReason;

    public static PasswordResetResponseDto fromEntity(String message, PasswordResetRequest request) {
        return new PasswordResetResponseDto(
                message,
                request.getId(),
                request.getStudentId(),
                request.getName(),
                request.getLicensePlate(),
                request.getUsername(),
                request.getStatus(),
                request.getRequestedAt(),
                request.getProcessedAt(),
                request.getProcessedBy(),
                request.getRejectReason()
        );
    }
}
