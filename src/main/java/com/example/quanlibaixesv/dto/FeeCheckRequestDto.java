package com.example.quanlibaixesv.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FeeCheckRequestDto {
    private LocalDateTime checkInTime;
    private LocalDateTime checkOutTime;
}