package com.example.quanlibaixesv.service;

import com.example.quanlibaixesv.model.MonthlyReport;
import com.example.quanlibaixesv.repository.MonthlyReportRepository;
import com.example.quanlibaixesv.repository.ParkingLogRepository;
import com.example.quanlibaixesv.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class AutoReportService {

    @Autowired
    private StudentRepository studentRepo;

    @Autowired
    private ParkingLogRepository logRepo;

    @Autowired
    private MonthlyReportRepository reportRepo;

    // Chạy lúc 23:59 ngày cuối tháng
    @Scheduled(cron = "0 59 23 L * ?")
    public void processEndOfMonth() {
        int m = LocalDate.now().getMonthValue();
        int y = LocalDate.now().getYear();

        studentRepo.findAll().forEach(sv -> {
            Long totalMinutes = logRepo.calculateMonthlyMinutes(sv.getCardId(), m, y);
            Long totalAmount = logRepo.calculateMonthlyAmount(sv.getCardId(), m, y);

            MonthlyReport report = reportRepo.findByCardIdAndMonthAndYear(sv.getCardId(), m, y)
                    .orElse(new MonthlyReport());

            report.setCardId(sv.getCardId());
            report.setMonth(m);
            report.setYear(y);
            report.setTotalMinutes(totalMinutes != null ? totalMinutes : 0);
            report.setTotalAmount(totalAmount != null ? totalAmount : 0);

            reportRepo.save(report);
        });
    }
}