package com.example.quanlibaixesv.service;

import com.example.quanlibaixesv.model.ParkingPriceRule;
import com.example.quanlibaixesv.repository.ParkingPriceRuleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
public class ParkingFeeService {

    public static final long MIN_ENTRY_FEE = 2000;

    @Autowired
    private ParkingPriceRuleRepository priceRuleRepo;

    public long calculateTotalFee(LocalDateTime checkIn, LocalDateTime checkOut) {
        if (checkIn == null || checkOut == null || !checkOut.isAfter(checkIn)) {
            return MIN_ENTRY_FEE;
        }

        List<ParkingPriceRule> rules = priceRuleRepo.findByActiveTrueOrderByStartTimeAsc();
        if (rules.isEmpty()) {
            throw new RuntimeException("Chưa có cấu hình bảng giá.");
        }

        long variableFee = calculateVariableFee(checkIn, checkOut, rules);

        // Auto thu 2000
        return Math.max(variableFee, MIN_ENTRY_FEE);
    }

    private long calculateVariableFee(LocalDateTime checkIn, LocalDateTime checkOut, List<ParkingPriceRule> rules) {
        double total = 0.0;
        LocalDateTime cursor = checkIn;

        while (cursor.isBefore(checkOut)) {
            ParkingPriceRule matchedRule = findRuleByDateTime(cursor, rules);
            if (matchedRule == null) {
                throw new RuntimeException("Không tìm thấy khung giá cho thời điểm: " + cursor);
            }

            double pricePerMinute = matchedRule.getPricePerHour() / 60.0;
            total += pricePerMinute;

            cursor = cursor.plusMinutes(1);
        }

        return Math.round(total);
    }

    private ParkingPriceRule findRuleByDateTime(LocalDateTime dateTime, List<ParkingPriceRule> rules) {
        LocalTime time = dateTime.toLocalTime();
        boolean isWeekend = isWeekend(dateTime.getDayOfWeek());

        for (ParkingPriceRule rule : rules) {
            if (!matchDayType(rule, isWeekend)) {
                continue;
            }

            LocalTime start = rule.getStartTime();
            LocalTime end = rule.getEndTime();

            // Khung bình thường, ví dụ 05:00 -> 17:00
            if (end.isAfter(start)) {
                if ((!time.isBefore(start)) && time.isBefore(end)) {
                    return rule;
                }
            }
            // Khung qua đêm, ví dụ 22:00 -> 05:00
            else {
                if ((!time.isBefore(start)) || time.isBefore(end)) {
                    return rule;
                }
            }
        }

        return null;
    }

    private boolean matchDayType(ParkingPriceRule rule, boolean isWeekend) {
        if (isWeekend) {
            return rule.isWeekend();
        }
        return rule.isWeekday();
    }

    private boolean isWeekend(DayOfWeek dayOfWeek) {
        return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
    }
}