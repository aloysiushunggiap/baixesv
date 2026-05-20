package com.example.quanlibaixesv.controller;

import com.example.quanlibaixesv.dto.PriceRuleRequestDto;
import com.example.quanlibaixesv.model.ParkingPriceRule;
import com.example.quanlibaixesv.repository.ParkingPriceRuleRepository;
import com.example.quanlibaixesv.service.PricingRuleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pricing")
public class PricingController {

    @Autowired
    private ParkingPriceRuleRepository priceRuleRepo;

    @Autowired
    private PricingRuleService pricingRuleService;

    @PostMapping
    public ParkingPriceRule createRule(@RequestBody PriceRuleRequestDto dto) {
        pricingRuleService.validateNoOverlap(dto, null);

        ParkingPriceRule rule = new ParkingPriceRule();
        rule.setStartTime(dto.getStartTime());
        rule.setEndTime(dto.getEndTime());
        rule.setPricePerHour(dto.getPricePerHour());
        rule.setWeekday(dto.isWeekday());
        rule.setWeekend(dto.isWeekend());
        rule.setActive(dto.isActive());

        return priceRuleRepo.save(rule);
    }

    @PutMapping("/{id}")
    public ParkingPriceRule updateRule(@PathVariable Long id, @RequestBody PriceRuleRequestDto dto) {
        ParkingPriceRule rule = priceRuleRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy khung giá với id = " + id));

        pricingRuleService.validateNoOverlap(dto, id);

        rule.setStartTime(dto.getStartTime());
        rule.setEndTime(dto.getEndTime());
        rule.setPricePerHour(dto.getPricePerHour());
        rule.setWeekday(dto.isWeekday());
        rule.setWeekend(dto.isWeekend());
        rule.setActive(dto.isActive());

        return priceRuleRepo.save(rule);
    }

    @GetMapping
    public List<ParkingPriceRule> getAllRules() {
        return priceRuleRepo.findAll();
    }

    @GetMapping("/{id}")
    public ParkingPriceRule getRuleById(@PathVariable Long id) {
        return priceRuleRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy khung giá với id = " + id));
    }

    @DeleteMapping("/{id}")
    public String deleteRule(@PathVariable Long id) {
        if (!priceRuleRepo.existsById(id)) {
            return "Không tìm thấy khung giá để xóa!";
        }

        priceRuleRepo.deleteById(id);
        return "Đã xóa khung giá id = " + id;
    }
}