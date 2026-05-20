package com.example.quanlibaixesv.service;

import com.example.quanlibaixesv.dto.PriceRuleRequestDto;
import com.example.quanlibaixesv.model.ParkingPriceRule;
import com.example.quanlibaixesv.repository.ParkingPriceRuleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class PricingRuleService {

    @Autowired
    private ParkingPriceRuleRepository priceRuleRepo;

    public void validateNoOverlap(PriceRuleRequestDto dto, Long excludeId) {
        validateInput(dto);

        List<ParkingPriceRule> rules = priceRuleRepo.findByActiveTrueOrderByStartTimeAsc();

        for (ParkingPriceRule rule : rules) {
            if (excludeId != null && rule.getId().equals(excludeId)) {
                continue;
            }

            if (!shareSameDayType(dto, rule)) {
                continue;
            }

            if (isTimeOverlapping(
                    dto.getStartTime(), dto.getEndTime(),
                    rule.getStartTime(), rule.getEndTime()
            )) {
                throw new RuntimeException(
                        "Khung giờ bị trùng với rule đã có. Rule hiện tại: id=" + rule.getId()
                                + ", start=" + rule.getStartTime()
                                + ", end=" + rule.getEndTime()
                );
            }
        }
    }

    private void validateInput(PriceRuleRequestDto dto) {
        if (dto.getStartTime() == null || dto.getEndTime() == null) {
            throw new RuntimeException("startTime và endTime không được để trống.");
        }

        if (dto.getPricePerHour() < 0) {
            throw new RuntimeException("pricePerHour không được âm.");
        }

        if (!dto.isWeekday() && !dto.isWeekend()) {
            throw new RuntimeException("Rule phải áp dụng cho ít nhất weekday hoặc weekend.");
        }

        if (dto.getStartTime().equals(dto.getEndTime())) {
            throw new RuntimeException("startTime và endTime không được giống nhau.");
        }
    }

    private boolean shareSameDayType(PriceRuleRequestDto dto, ParkingPriceRule rule) {
        boolean sameWeekday = dto.isWeekday() && rule.isWeekday();
        boolean sameWeekend = dto.isWeekend() && rule.isWeekend();
        return sameWeekday || sameWeekend;
    }

    private boolean isTimeOverlapping(LocalTime newStart, LocalTime newEnd,
                                      LocalTime oldStart, LocalTime oldEnd) {

        List<TimeRange> newRanges = splitRange(newStart, newEnd);
        List<TimeRange> oldRanges = splitRange(oldStart, oldEnd);

        for (TimeRange r1 : newRanges) {
            for (TimeRange r2 : oldRanges) {
                if (overlap(r1.startMinute, r1.endMinute, r2.startMinute, r2.endMinute)) {
                    return true;
                }
            }
        }

        return false;
    }

    private List<TimeRange> splitRange(LocalTime start, LocalTime end) {
        int startMinute = start.getHour() * 60 + start.getMinute();
        int endMinute = end.getHour() * 60 + end.getMinute();

        List<TimeRange> ranges = new ArrayList<>();

        // khung bình thường: ví dụ 05:00 -> 17:00
        if (endMinute > startMinute) {
            ranges.add(new TimeRange(startMinute, endMinute));
        }
        // khung qua đêm: ví dụ 22:00 -> 05:00
        else {
            ranges.add(new TimeRange(startMinute, 24 * 60));
            ranges.add(new TimeRange(0, endMinute));
        }

        return ranges;
    }

    private boolean overlap(int s1, int e1, int s2, int e2) {
        return s1 < e2 && s2 < e1;
    }

    private static class TimeRange {
        int startMinute;
        int endMinute;

        TimeRange(int startMinute, int endMinute) {
            this.startMinute = startMinute;
            this.endMinute = endMinute;
        }
    }
}