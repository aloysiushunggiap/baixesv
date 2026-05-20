package com.example.quanlibaixesv.controller;

import com.example.quanlibaixesv.service.LoginSessionService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final LoginSessionService loginSessionService;

    public SessionController(LoginSessionService loginSessionService) {
        this.loginSessionService = loginSessionService;
    }

    // Admin xem danh sách token/session còn hạn theo JWT exp.
    @GetMapping("/active")
    public List<Map<String, Object>> getActiveSessions(Authentication authentication) {
        requireAdmin(authentication);
        return loginSessionService.getActiveSessions();
    }

    // Admin xóa một session để ép token đó logout ngay.
    @DeleteMapping("/{sessionId}")
    public Map<String, Object> deleteSession(@PathVariable String sessionId,
                                             Authentication authentication) {
        requireAdmin(authentication);
        loginSessionService.deleteSession(sessionId);

        return Map.of(
                "message", "Đã xóa phiên đăng nhập.",
                "sessionId", sessionId
        );
    }

    private void requireAdmin(Authentication authentication) {
        if (authentication == null
                || authentication.getAuthorities() == null
                || authentication.getAuthorities().stream().noneMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))) {
            throw new RuntimeException("Chỉ admin được quản lý phiên đăng nhập.");
        }
    }
}
