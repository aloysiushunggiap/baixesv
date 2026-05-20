package com.example.quanlibaixesv.controller;

import com.example.quanlibaixesv.dto.ForgotPasswordRequestDto;
import com.example.quanlibaixesv.dto.PasswordResetDecisionDto;
import com.example.quanlibaixesv.dto.PasswordResetResponseDto;
import com.example.quanlibaixesv.model.PasswordResetStatus;
import com.example.quanlibaixesv.service.PasswordResetService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/password-reset")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    // User quen mat khau go: ho ten, ma sinh vien, bien so xe, mat khau moi.
    // Endpoint nay khong can dang nhap.
    @PostMapping("/request")
    public PasswordResetResponseDto createRequest(@RequestBody ForgotPasswordRequestDto request) {
        return passwordResetService.createRequest(request);
    }

    // Man hinh Admin -> Yeu cau.
    // Co the goi: GET /api/password-reset/admin/requests?status=PENDING
    @GetMapping("/admin/requests")
    public List<PasswordResetResponseDto> getRequests(@RequestParam(required = false) PasswordResetStatus status,
                                                      Authentication authentication) {
        requireAdmin(authentication);
        return passwordResetService.getRequests(status);
    }

    // Admin bam Dong y / Tu choi.
    @PutMapping("/admin/requests/{id}/decision")
    public PasswordResetResponseDto decide(@PathVariable Long id,
                                           @RequestBody PasswordResetDecisionDto decision,
                                           Authentication authentication) {
        requireAdmin(authentication);
        return passwordResetService.decide(id, decision, authentication.getName());
    }

    private void requireAdmin(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null
                || authentication.getAuthorities().stream().noneMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))) {
            throw new RuntimeException("Chi admin duoc xu ly yeu cau quen mat khau.");
        }
    }
}
