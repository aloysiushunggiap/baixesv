package com.example.quanlibaixesv.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "monthly_reports")
@Data
public class MonthlyReport {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String cardId;
    private int month;
    private int year;
    private long totalMinutes;
    private long totalAmount;
}