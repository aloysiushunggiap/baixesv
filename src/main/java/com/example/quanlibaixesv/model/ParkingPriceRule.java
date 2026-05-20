package com.example.quanlibaixesv.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalTime;

@Entity
@Table(name = "parking_price_rules")
@Data
public class ParkingPriceRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalTime startTime;
    private LocalTime endTime;

    private long pricePerHour;

    private boolean weekday;
    private boolean weekend;

    private boolean active = true;
}