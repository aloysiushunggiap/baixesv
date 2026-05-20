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
import org.springframework.web.bind.annotation.*;

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
    public ChangePasswordResponseDto changePassword(@RequestBody ChangePasswordRequestDto request,
                                                    Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("Ban chua dang nhap.");
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

        throw new RuntimeException("Vai tro tai khoan khong hop le.");
    }

    @DeleteMapping("/admin/{username}")
    @Transactional
    public Map<String, Object> deleteAdminAccount(@PathVariable String username,
                                                  Authentication authentication) {
        if (!hasRole(authentication, "ROLE_ADMIN")) {
            throw new RuntimeException("Chi admin duoc xoa tai khoan admin.");
        }

        if (username == null || username.isBlank()) {
            throw new RuntimeException("username admin can xoa khong duoc de trong.");
        }

        String targetUsername = username.trim();
        String loggedInUsername = authentication.getName();

        if (targetUsername.equals(loggedInUsername)) {
            throw new RuntimeException("Ban khong the tu xoa tai khoan admin dang dang nhap.");
        }

        if (adminRepo.count() <= 1) {
            throw new RuntimeException("Khong the xoa vi he thong phai con it nhat mot tai khoan admin.");
        }

        AdminAccount admin = adminRepo.findByUsername(targetUsername)
                .orElseThrow(() -> new RuntimeException("Khong tim thay tai khoan admin: " + targetUsername));

        adminRepo.delete(admin);
        loginSessionService.deactivateAllSessions(targetUsername);

        return Map.of(
                "message", "Da xoa tai khoan admin thanh cong.",
                "username", targetUsername
        );
    }

    private void validateChangePasswordInput(ChangePasswordRequestDto request) {
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            throw new RuntimeException("username khong duoc de trong.");
        }

        if (request.getNewPassword() == null || request.getNewPassword().isBlank()) {
            throw new RuntimeException("mat khau moi khong duoc de trong.");
        }

        if (request.getNewPassword().length() < 6) {
            throw new RuntimeException("mat khau moi phai co it nhat 6 ky tu.");
        }

        if (request.getNewPassword().contains(" ")) {
            throw new RuntimeException("mat khau moi khong nen co dau cach.");
        }
    }

    private ChangePasswordResponseDto changePasswordAsAdmin(ChangePasswordRequestDto request) {
        String targetUsername = request.getUsername().trim();

        AdminAccount admin = adminRepo.findByUsername(targetUsername).orElse(null);
        if (admin != null) {
            if (!admin.isEnabled()) {
                throw new RuntimeException("Tai khoan admin dang bi khoa.");
            }

            admin.setPassword(passwordEncoder.encode(request.getNewPassword()));
            admin.setTokenVersion(admin.getTokenVersion() + 1);
            adminRepo.save(admin);
            loginSessionService.deactivateAllSessions(admin.getUsername());

            return new ChangePasswordResponseDto(
                    "Admin da doi mat khau tai khoan admin thanh cong. Tat ca phien dang nhap cu da bi huy.",
                    admin.getUsername(),
                    null
            );
        }

        Student student = studentRepo.findByUsername(targetUsername)
                .orElseThrow(() -> new RuntimeException("Khong tim thay tai khoan can doi mat khau: " + targetUsername));

        if (!student.isEnabled()) {
            throw new RuntimeException("Tai khoan user dang bi khoa.");
        }

        student.setPassword(passwordEncoder.encode(request.getNewPassword()));
        student.setTokenVersion(student.getTokenVersion() + 1);
        studentRepo.save(student);
        loginSessionService.deactivateAllSessions(student.getUsername());

        return new ChangePasswordResponseDto(
                "Admin da doi mat khau tai khoan user thanh cong. Tat ca phien dang nhap cu da bi huy.",
                student.getUsername(),
                null
        );
    }

    private ChangePasswordResponseDto changeOwnUserPassword(ChangePasswordRequestDto request, String loggedInUsername) {
        String targetUsername = request.getUsername().trim();

        if (!loggedInUsername.equals(targetUsername)) {
            throw new RuntimeException("Ban chi duoc doi mat khau cua chinh tai khoan dang dang nhap.");
        }

        if (request.getOldPassword() == null || request.getOldPassword().isBlank()) {
            throw new RuntimeException("mat khau cu khong duoc de trong.");
        }

        Student student = studentRepo.findByUsername(targetUsername)
                .orElseThrow(() -> new RuntimeException("Khong tim thay tai khoan user."));

        if (!student.isEnabled()) {
            throw new RuntimeException("Tai khoan user dang bi khoa.");
        }

        if (!passwordEncoder.matches(request.getOldPassword(), student.getPassword())) {
            throw new RuntimeException("Mat khau cu khong dung.");
        }

        student.setPassword(passwordEncoder.encode(request.getNewPassword()));
        student.setTokenVersion(student.getTokenVersion() + 1);
        studentRepo.save(student);
        loginSessionService.deactivateAllSessions(student.getUsername());

        return new ChangePasswordResponseDto(
                "Doi mat khau user thanh cong. Tat ca phien dang nhap cu da bi huy.",
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
