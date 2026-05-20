package com.example.quanlibaixesv.controller;

import com.example.quanlibaixesv.dto.FeeCheckRequestDto;
import com.example.quanlibaixesv.dto.SwipeResponseDto;
import com.example.quanlibaixesv.model.MonthlyReport;
import com.example.quanlibaixesv.model.ParkingLog;
import com.example.quanlibaixesv.model.Student;
import com.example.quanlibaixesv.repository.MonthlyReportRepository;
import com.example.quanlibaixesv.repository.ParkingLogRepository;
import com.example.quanlibaixesv.repository.StudentRepository;
import com.example.quanlibaixesv.service.ParkingFeeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/parking")
public class ParkingController {

    @Autowired
    private StudentRepository studentRepo;

    @Autowired
    private ParkingLogRepository logRepo;

    @Autowired
    private MonthlyReportRepository reportRepo;

    @Autowired
    private ParkingFeeService parkingFeeService;

    @PostMapping("/swipe")
    @Transactional
    public SwipeResponseDto swipe(@RequestParam String cardId, Authentication authentication) {
        String normalizedCardId = normalizeCardId(cardId);
        Student student = studentRepo.findByCardId(normalizedCardId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thẻ: " + normalizedCardId));

        if (!student.isEnabled()) {
            throw new RuntimeException("Thẻ đang bị khóa hoặc tài khoản không còn hoạt động.");
        }

        LocalDateTime now = LocalDateTime.now();
        ParkingLog openLog = logRepo.findByCardIdAndCheckOutTimeIsNull(normalizedCardId).orElse(null);

        // Admin dùng màn hình quẹt thẻ để test nên không được tạo/sửa ParkingLog
        // và không cập nhật MonthlyReport của thẻ.
        if (hasRole(authentication, "ROLE_ADMIN")) {
            return simulateAdminSwipe(normalizedCardId, openLog, now);
        }

        if (openLog == null) {
            ParkingLog log = new ParkingLog();
            log.setCardId(normalizedCardId);
            log.setCheckInTime(now);
            log.setDurationMinutes(0);
            log.setEntryFee(ParkingFeeService.MIN_ENTRY_FEE);
            log.setAmount(ParkingFeeService.MIN_ENTRY_FEE);
            logRepo.save(log);

            return new SwipeResponseDto(
                    "Check-in thành công.",
                    normalizedCardId,
                    "CHECK_IN",
                    0,
                    ParkingFeeService.MIN_ENTRY_FEE
            );
        }

        openLog.setCheckOutTime(now);
        long durationMinutes = Math.max(0, ChronoUnit.MINUTES.between(openLog.getCheckInTime(), now));
        long totalAmount = parkingFeeService.calculateTotalFee(openLog.getCheckInTime(), now);

        openLog.setDurationMinutes(durationMinutes);
        openLog.setAmount(totalAmount);
        logRepo.save(openLog);
        updateMonthlyReport(openLog);

        return new SwipeResponseDto(
                "Check-out thành công. Đã tính phí gửi xe.",
                normalizedCardId,
                "CHECK_OUT",
                durationMinutes,
                totalAmount
        );
    }

    @PostMapping("/simulate-fee")
    public Map<String, Object> simulateFee(@RequestBody FeeCheckRequestDto request) {
        if (request.getCheckInTime() == null || request.getCheckOutTime() == null) {
            throw new RuntimeException("checkInTime và checkOutTime không được để trống.");
        }

        if (!request.getCheckOutTime().isAfter(request.getCheckInTime())) {
            throw new RuntimeException("Thời gian ra phải sau thời gian vào.");
        }

        long durationMinutes = Math.max(0, ChronoUnit.MINUTES.between(
                request.getCheckInTime(),
                request.getCheckOutTime()
        ));
        long amount = parkingFeeService.calculateTotalFee(request.getCheckInTime(), request.getCheckOutTime());

        return Map.of(
                "message", "Tính phí thành công.",
                "checkInTime", request.getCheckInTime(),
                "checkOutTime", request.getCheckOutTime(),
                "durationMinutes", durationMinutes,
                "amount", amount
        );
    }

    @GetMapping("/history")
    public List<MonthlyReport> getHistory(@RequestParam(required = false) String cardId,
                                          Authentication authentication) {
        String targetCardId;

        if (hasRole(authentication, "ROLE_ADMIN")) {
            targetCardId = normalizeCardId(cardId);
        } else {
            if (authentication == null || authentication.getName() == null) {
                throw new RuntimeException("Bạn chưa đăng nhập.");
            }

            Student student = studentRepo.findByUsername(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy sinh viên đang đăng nhập."));
            targetCardId = student.getCardId();
        }

        return reportRepo.findByCardIdOrderByYearDescMonthDesc(targetCardId);
    }

    @DeleteMapping("/delete-card")
    @Transactional
    public Map<String, Object> deleteCard(@RequestParam String cardId) {
        String normalizedCardId = normalizeCardId(cardId);

        Student student = studentRepo.findByCardId(normalizedCardId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thẻ để xóa: " + normalizedCardId));

        if (logRepo.findByCardIdAndCheckOutTimeIsNull(normalizedCardId).isPresent()) {
            throw new RuntimeException("Không thể xóa thẻ vì xe vẫn đang ở trong bãi.");
        }

        logRepo.deleteByCardId(normalizedCardId);
        reportRepo.deleteByCardId(normalizedCardId);
        studentRepo.delete(student);

        return Map.of(
                "message", "Đã xóa thẻ và toàn bộ dữ liệu liên quan.",
                "cardId", normalizedCardId
        );
    }


    private SwipeResponseDto simulateAdminSwipe(String cardId, ParkingLog openLog, LocalDateTime now) {
        if (openLog == null) {
            return new SwipeResponseDto(
                    "Admin test check-in thành công.",
                    cardId,
                    "CHECK_IN",
                    0,
                    ParkingFeeService.MIN_ENTRY_FEE
            );
        }

        long durationMinutes = Math.max(0, ChronoUnit.MINUTES.between(openLog.getCheckInTime(), now));
        long totalAmount = parkingFeeService.calculateTotalFee(openLog.getCheckInTime(), now);

        return new SwipeResponseDto(
                "Admin test check-out thành công.",
                cardId,
                "CHECK_OUT",
                durationMinutes,
                totalAmount
        );
    }

    private void updateMonthlyReport(ParkingLog savedLog) {
        if (savedLog.getCheckOutTime() == null) {
            return;
        }

        int month = savedLog.getCheckOutTime().getMonthValue();
        int year = savedLog.getCheckOutTime().getYear();
        String cardId = savedLog.getCardId();

        Long totalMinutes = logRepo.calculateMonthlyMinutes(cardId, month, year);
        Long totalAmount = logRepo.calculateMonthlyAmount(cardId, month, year);

        MonthlyReport report = reportRepo.findByCardIdAndMonthAndYear(cardId, month, year)
                .orElse(new MonthlyReport());

        report.setCardId(cardId);
        report.setMonth(month);
        report.setYear(year);
        report.setTotalMinutes(totalMinutes != null ? totalMinutes : 0);
        report.setTotalAmount(totalAmount != null ? totalAmount : 0);

        reportRepo.save(report);
    }

    private String normalizeCardId(String cardId) {
        if (cardId == null || cardId.isBlank()) {
            throw new RuntimeException("cardId không được để trống.");
        }
        return cardId.trim();
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication != null
                && authentication.getAuthorities() != null
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> role.equals(authority.getAuthority()));
    }
}
