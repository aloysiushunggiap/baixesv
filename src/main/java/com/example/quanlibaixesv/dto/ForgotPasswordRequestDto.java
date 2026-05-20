package com.example.quanlibaixesv.dto;

import lombok.Data;

@Data
public class ForgotPasswordRequestDto {
    private String name;
    private String studentId;
    private String licensePlate;
    private String newPassword;
}
