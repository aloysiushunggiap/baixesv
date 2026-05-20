package com.example.quanlibaixesv.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor

public class ChangePasswordResponseDto {
    private String message;
    private String username;
    private String newPassword;
}
