package com.example.quanlibaixesv.repository;

import com.example.quanlibaixesv.model.MonthlyReport;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MonthlyReportRepository extends JpaRepository<MonthlyReport, Long> {
    Optional<MonthlyReport> findByCardIdAndMonthAndYear(String cardId, int month, int year);
    List<MonthlyReport> findByCardIdOrderByYearDescMonthDesc(String cardId);

    @Transactional
    void deleteByCardId(String cardId);
}
