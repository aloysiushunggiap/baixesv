package com.example.quanlibaixesv.controller;

import com.example.quanlibaixesv.dto.ChangePasswordRequestDto;
import com.example.quanlibaixesv.dto.ChangePasswordResponseDto;
import com.example.quanlibaixesv.model.AdminAccount;
import com.example.quanlibaixesv.model.Student;
import com.example.quanlibaixesv.repository.AdminAccountRepository;
import com.example.quanlibaixesv.repository.StudentRepository;
import com.example.quanlibaixesv.service.LoginSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/account")
public class AccountController {

    @Autowired
    private AdminAccountRepository adminRepo;

    @Autowired
    private StudentRepository studentRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private LoginSessionService loginSessionService;

    @PutMapping("/change-password")
    @Transactional
    public ChangePasswordResponseDto changePassword(@RequestBody ChangePasswordRequestDto request,
                                                    Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("Bạn chưa đăng nhập.");
        }

        validateChangePasswordInput(request);

        boolean isAdmin = hasRole(authentication, "ROLE_ADMIN");
        boolean isUser = hasRole(authentication, "ROLE_USER");

        if (isAdmin) {
            return changePasswordAsAdmin(request);
        }

        if (isUser) {
            return changeOwnUserPassword(request, authentication.getName());
        }

        throw new RuntimeException("Vai trò tài khoản không hợp lệ.");
    }

    @DeleteMapping("/admin/{username}")
    @Transactional
    public Map<String, Object> deleteAdminAccount(@PathVariable String username,
                                                  Authentication authentication) {
        if (!hasRole(authentication, "ROLE_ADMIN")) {
            throw new RuntimeException("Chỉ admin được xóa tài khoản admin.");
        }

        if (username == null || username.isBlank()) {
            throw new RuntimeException("username admin cần xóa không được để trống.");
        }

        String targetUsername = username.trim();
        String loggedInUsername = authentication.getName();

        if (targetUsername.equals(loggedInUsername)) {
            throw new RuntimeException("Bạn không thể tự xóa tài khoản admin đang đăng nhập.");
        }

        if (adminRepo.count() <= 1) {
            throw new RuntimeException("Không thể xóa vì hệ thống phải còn ít nhất một tài khoản admin.");
        }

        AdminAccount admin = adminRepo.findByUsername(targetUsername)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản admin: " + targetUsername));

        adminRepo.delete(admin);
        loginSessionService.deactivateAllSessions(targetUsername);

        return Map.of(
                "message", "Đã xóa tài khoản admin thành công.",
                "username", targetUsername
        );
    }

    private void validateChangePasswordInput(ChangePasswordRequestDto request) {
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            throw new RuntimeException("username không được để trống.");
        }

        if (request.getNewPassword() == null || request.getNewPassword().isBlank()) {
            throw new RuntimeException("mật khẩu mới không được để trống.");
        }

        if (request.getNewPassword().length() < 6) {
            throw new RuntimeException("mật khẩu mới phải có ít nhất 6 ký tự.");
        }

        if (request.getNewPassword().contains(" ")) {
            throw new RuntimeException("mật khẩu mới không nên có dấu cách.");
        }
    }

    private ChangePasswordResponseDto changePasswordAsAdmin(ChangePasswordRequestDto request) {
        String targetUsername = request.getUsername().trim();

        AdminAccount admin = adminRepo.findByUsername(targetUsername).orElse(null);
        if (admin != null) {
            if (!admin.isEnabled()) {
                throw new RuntimeException("Tài khoản admin đang bị khóa.");
            }

            admin.setPassword(passwordEncoder.encode(request.getNewPassword()));
            admin.setTokenVersion(admin.getTokenVersion() + 1);
            adminRepo.save(admin);
            loginSessionService.deactivateAllSessions(admin.getUsername());

            return new ChangePasswordResponseDto(
                    "Admin đã đổi mật khẩu tài khoản admin thành công. Tất cả phiên đăng nhập cũ đã bị hủy.",
                    admin.getUsername(),
                    null
            );
        }

        Student student = studentRepo.findByUsername(targetUsername)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản cần đổi mật khẩu: " + targetUsername));

        if (!student.isEnabled()) {
            throw new RuntimeException("Tài khoản user đang bị khóa.");
        }

        student.setPassword(passwordEncoder.encode(request.getNewPassword()));
        student.setTokenVersion(student.getTokenVersion() + 1);
        studentRepo.save(student);
        loginSessionService.deactivateAllSessions(student.getUsername());

        return new ChangePasswordResponseDto(
                "Admin đã đổi mật khẩu tài khoản user thành công. Tất cả phiên đăng nhập cũ đã bị hủy.",
                student.getUsername(),
                null
        );
    }

    private ChangePasswordResponseDto changeOwnUserPassword(ChangePasswordRequestDto request, String loggedInUsername) {
        String targetUsername = request.getUsername().trim();

        if (!loggedInUsername.equals(targetUsername)) {
            throw new RuntimeException("Bạn chỉ được đổi mật khẩu của chính tài khoản đang đăng nhập.");
        }

        if (request.getOldPassword() == null || request.getOldPassword().isBlank()) {
            throw new RuntimeException("mật khẩu cũ không được để trống.");
        }

        Student student = studentRepo.findByUsername(targetUsername)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản user."));

        if (!student.isEnabled()) {
            throw new RuntimeException("Tài khoản user đang bị khóa.");
        }

        if (!passwordEncoder.matches(request.getOldPassword(), student.getPassword())) {
            throw new RuntimeException("Mật khẩu cũ không đúng.");
        }

        student.setPassword(passwordEncoder.encode(request.getNewPassword()));
        student.setTokenVersion(student.getTokenVersion() + 1);
        studentRepo.save(student);
        loginSessionService.deactivateAllSessions(student.getUsername());

        return new ChangePasswordResponseDto(
                "Đổi mật khẩu user thành công. Tất cả phiên đăng nhập cũ đã bị hủy.",
                student.getUsername(),
                null
        );
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication != null
                && authentication.getAuthorities() != null
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> role.equals(authority.getAuthority()));
    }
}
