package com.example.quanlibaixesv.repository;

import com.example.quanlibaixesv.model.ParkingLog;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ParkingLogRepository extends JpaRepository<ParkingLog, Long> {
    Optional<ParkingLog> findByCardIdAndCheckOutTimeIsNull(String cardId);

    @Query("SELECT SUM(p.durationMinutes) FROM ParkingLog p " +
            "WHERE p.cardId = :cid AND MONTH(p.checkOutTime) = :m AND YEAR(p.checkOutTime) = :y")
    Long calculateMonthlyMinutes(@Param("cid") String cardId, @Param("m") int month, @Param("y") int year);

    @Query("SELECT SUM(p.amount) FROM ParkingLog p " +
            "WHERE p.cardId = :cid AND MONTH(p.checkOutTime) = :m AND YEAR(p.checkOutTime) = :y")
    Long calculateMonthlyAmount(@Param("cid") String cardId, @Param("m") int month, @Param("y") int year);

    @Transactional
    void deleteByCardId(String cardId);
}