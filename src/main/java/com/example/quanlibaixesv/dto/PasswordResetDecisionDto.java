package com.example.quanlibaixesv.dto;

import lombok.Data;

@Data
public class PasswordResetDecisionDto {
    private Boolean approved;
    private String reason;
}
