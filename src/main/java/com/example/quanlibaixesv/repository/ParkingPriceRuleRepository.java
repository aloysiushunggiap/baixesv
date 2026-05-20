package com.example.quanlibaixesv.repository;

import com.example.quanlibaixesv.model.ParkingPriceRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ParkingPriceRuleRepository extends JpaRepository<ParkingPriceRule, Long> {
    List<ParkingPriceRule> findByActiveTrueOrderByStartTimeAsc();
}