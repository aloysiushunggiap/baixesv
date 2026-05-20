package com.example.quanlibaixesv.controller;

import com.example.quanlibaixesv.dto.LoginRequestDto;
import com.example.quanlibaixesv.dto.LoginResponseDto;
import com.example.quanlibaixesv.dto.RegisterRequestDto;
import com.example.quanlibaixesv.model.AdminAccount;
import com.example.quanlibaixesv.model.Role;
import com.example.quanlibaixesv.model.Student;
import com.example.quanlibaixesv.model.UserSession;
import com.example.quanlibaixesv.repository.AdminAccountRepository;
import com.example.quanlibaixesv.repository.StudentRepository;
import com.example.quanlibaixesv.security.JwtService;
import com.example.quanlibaixesv.service.CardSignatureService;
import com.example.quanlibaixesv.service.LoginSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private StudentRepository studentRepo;

    @Autowired
    private AdminAccountRepository adminRepo;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CardSignatureService cardSignatureService;

    @Autowired
    private LoginSessionService loginSessionService;

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody RegisterRequestDto request, Authentication authentication) {
        if (!hasRole(authentication, "ROLE_ADMIN")) {
            throw new RuntimeException("Chi admin duoc dang ky tai khoan moi.");
        }

        if (request.getUsername() == null || request.getUsername().isBlank()) {
            throw new RuntimeException("username khong duoc de trong");
        }

        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new RuntimeException("password khong duoc de trong");
        }

        if (request.getRole() == null) {
            throw new RuntimeException("role khong duoc de trong");
        }

        if (adminRepo.findByUsername(request.getUsername()).isPresent()
                || studentRepo.findByUsername(request.getUsername()).isPresent()) {
            throw new RuntimeException("username da ton tai");
        }

        if (request.getRole() == Role.ROLE_ADMIN) {
            AdminAccount admin = new AdminAccount();
            admin.setUsername(request.getUsername());
            admin.setPassword(passwordEncoder.encode(request.getPassword()));
            admin.setEnabled(true);
            admin.setTokenVersion(0L);
            adminRepo.save(admin);

            return Map.of(
                    "message", "Dang ky admin thanh cong!",
                    "username", admin.getUsername(),
                    "role", "ROLE_ADMIN",
                    "registeredAt", LocalDateTime.now()
            );
        }

        if (request.getStudentId() == null || request.getStudentId().isBlank()) {
            throw new RuntimeException("ma sinh vien khong duoc de trong");
        }

        if (!request.getStudentId().matches("\\d{8}")) {
            throw new RuntimeException("ma sinh vien phai gom dung 8 chu so");
        }

        if (request.getName() == null || request.getName().isBlank()) {
            throw new RuntimeException("ho ten khong duoc de trong");
        }

        if (request.getLicensePlate() == null || request.getLicensePlate().isBlank()) {
            throw new RuntimeException("bien so khong duoc de trong");
        }

        if (studentRepo.existsById(request.getStudentId())) {
            throw new RuntimeException("ma sinh vien da ton tai");
        }

        String generatedCardId;
        do {
            generatedCardId = "CARD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } while (studentRepo.existsByCardId(generatedCardId));

        Student student = new Student();
        student.setId(request.getStudentId());
        student.setName(request.getName());
        student.setLicensePlate(request.getLicensePlate());
        student.setCardId(generatedCardId);
        student.setUsername(request.getUsername());
        student.setPassword(passwordEncoder.encode(request.getPassword()));
        student.setRole(Role.ROLE_USER);
        student.setEnabled(true);
        student.setCardSecret(cardSignatureService.generateCardSecret());
        student.setTokenVersion(0L);

        studentRepo.save(student);

        return Map.of(
                "message", "Dang ky user sinh vien thanh cong!",
                "studentId", student.getId(),
                "name", student.getName(),
                "licensePlate", student.getLicensePlate(),
                "username", student.getUsername(),
                "role", student.getRole().name(),
                "cardId", student.getCardId(),
                "cardSecret", student.getCardSecret(),
                "enabled", student.isEnabled(),
                "registeredAt", LocalDateTime.now()
        );
    }

    @PostMapping("/login")
    public LoginResponseDto login(@RequestBody LoginRequestDto request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );

        AdminAccount admin = adminRepo.findByUsername(request.getUsername()).orElse(null);
        if (admin != null) {
            UserDetails userDetails = org.springframework.security.core.userdetails.User
                    .withUsername(admin.getUsername())
                    .password(admin.getPassword())
                    .authorities("ROLE_ADMIN")
                    .build();

            UserSession session = loginSessionService.createSession(
                    admin.getUsername(),
                    "ROLE_ADMIN",
                    admin.getTokenVersion()
            );

            String token = jwtService.generateToken(
                    userDetails,
                    "ROLE_ADMIN",
                    null,
                    admin.getTokenVersion(),
                    session.getId()
            );
            return new LoginResponseDto(token, admin.getUsername(), "ROLE_ADMIN");
        }

        Student student = studentRepo.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("Khong tim thay tai khoan"));

        UserDetails userDetails = org.springframework.security.core.userdetails.User
                .withUsername(student.getUsername())
                .password(student.getPassword())
                .authorities(student.getRole().name())
                .build();

        UserSession session = loginSessionService.createSession(
                student.getUsername(),
                student.getRole().name(),
                student.getTokenVersion()
        );

        String token = jwtService.generateToken(
                userDetails,
                student.getRole().name(),
                student.getCardId(),
                student.getTokenVersion(),
                session.getId()
        );

        return new LoginResponseDto(token, student.getUsername(), student.getRole().name());
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication != null
                && authentication.getAuthorities() != null
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> role.equals(authority.getAuthority()));
    }
}
