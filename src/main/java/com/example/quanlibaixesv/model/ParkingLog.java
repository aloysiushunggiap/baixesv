package com.example.quanlibaixesv.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "parking_logs")
@Data
public class ParkingLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String cardId;
    private LocalDateTime checkInTime;
    private LocalDateTime checkOutTime;

    private long durationMinutes;

    // phí thu ngay khi vào bãi
    private long entryFee;

    // tổng tiền cuối cùng phải trả
    private long amount;
}