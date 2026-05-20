package com.example.quanlibaixesv.dto;

import lombok.Data;

import java.time.LocalTime;

@Data
public class PriceRuleRequestDto {
    private LocalTime startTime;
    private LocalTime endTime;
    private long pricePerHour;

    private boolean weekday;
    private boolean weekend;

    private boolean active = true;
}