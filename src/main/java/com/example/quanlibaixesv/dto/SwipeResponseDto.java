package com.example.quanlibaixesv.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SwipeResponseDto {
    private String message;
    private String cardId;
    private String action; // CHECK_IN hoặc CHECK_OUT
    private long durationMinutes;
    private long amount;
}