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
import com.example.quanlibaixesv.security.JwtCookieService;
import com.example.quanlibaixesv.security.JwtService;
import com.example.quanlibaixesv.service.CardSignatureService;
import com.example.quanlibaixesv.service.LoginSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Date;
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
    private JwtCookieService jwtCookieService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CardSignatureService cardSignatureService;

    @Autowired
    private LoginSessionService loginSessionService;

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody RegisterRequestDto request, Authentication authentication) {
        if (!hasRole(authentication, "ROLE_ADMIN")) {
            throw new RuntimeException("Chỉ admin được đăng ký tài khoản mới.");
        }

        if (request.getUsername() == null || request.getUsername().isBlank()) {
            throw new RuntimeException("username không được để trống");
        }

        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new RuntimeException("password không được để trống");
        }

        if (request.getRole() == null) {
            throw new RuntimeException("role không được để trống");
        }

        if (adminRepo.findByUsername(request.getUsername()).isPresent()
                || studentRepo.findByUsername(request.getUsername()).isPresent()) {
            throw new RuntimeException("username đã tồn tại");
        }

        if (request.getRole() == Role.ROLE_ADMIN) {
            AdminAccount admin = new AdminAccount();
            admin.setUsername(request.getUsername());
            admin.setPassword(passwordEncoder.encode(request.getPassword()));
            admin.setEnabled(true);
            admin.setTokenVersion(0L);
            adminRepo.save(admin);

            return Map.of(
                    "message", "Đăng ký admin thành công!",
                    "username", admin.getUsername(),
                    "role", "ROLE_ADMIN",
                    "registeredAt", LocalDateTime.now()
            );
        }

        if (request.getStudentId() == null || request.getStudentId().isBlank()) {
            throw new RuntimeException("mã sinh viên không được để trống");
        }

        if (!request.getStudentId().matches("\\d{8}")) {
            throw new RuntimeException("mã sinh viên phải gồm đúng 8 chữ số");
        }

        if (request.getName() == null || request.getName().isBlank()) {
            throw new RuntimeException("họ tên không được để trống");
        }

        if (request.getLicensePlate() == null || request.getLicensePlate().isBlank()) {
            throw new RuntimeException("biển số không được để trống");
        }

        if (studentRepo.existsById(request.getStudentId())) {
            throw new RuntimeException("mã sinh viên đã tồn tại");
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
                "message", "Đăng ký user sinh viên thành công!",
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
    public LoginResponseDto login(@RequestBody LoginRequestDto request,
                                  HttpServletResponse response) {
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

            Date expirationDate = jwtService.generateExpirationDate();
            LocalDateTime expiresAt = jwtService.toLocalDateTime(expirationDate);

            UserSession session = loginSessionService.createSession(
                    admin.getUsername(),
                    "ROLE_ADMIN",
                    admin.getTokenVersion(),
                    null,
                    expiresAt
            );

            String token = jwtService.generateToken(
                    userDetails,
                    "ROLE_ADMIN",
                    null,
                    admin.getTokenVersion(),
                    session.getId(),
                    expirationDate
            );

            // Token được lưu trong cookie HttpOnly để JavaScript không đọc được token trực tiếp.
            jwtCookieService.addLoginCookie(response, token, jwtService.getJwtExpirationMillis());

            return new LoginResponseDto(
                    null,
                    admin.getUsername(),
                    "ROLE_ADMIN",
                    session.getId(),
                    expiresAt,
                    null
            );
        }

        Student student = studentRepo.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản"));

        UserDetails userDetails = org.springframework.security.core.userdetails.User
                .withUsername(student.getUsername())
                .password(student.getPassword())
                .authorities(student.getRole().name())
                .build();

        Date expirationDate = jwtService.generateExpirationDate();
        LocalDateTime expiresAt = jwtService.toLocalDateTime(expirationDate);

        UserSession session = loginSessionService.createSession(
                student.getUsername(),
                student.getRole().name(),
                student.getTokenVersion(),
                student.getCardId(),
                expiresAt
        );

        String token = jwtService.generateToken(
                userDetails,
                student.getRole().name(),
                student.getCardId(),
                student.getTokenVersion(),
                session.getId(),
                expirationDate
        );

        // Token được lưu trong cookie HttpOnly để JavaScript không đọc được token trực tiếp.
        jwtCookieService.addLoginCookie(response, token, jwtService.getJwtExpirationMillis());

        return new LoginResponseDto(
                null,
                student.getUsername(),
                student.getRole().name(),
                session.getId(),
                expiresAt,
                student.getCardId()
        );
    }

    @GetMapping("/me")
    public LoginResponseDto currentUser(HttpServletRequest request) {
        String token = jwtCookieService.resolveToken(request);
        if (token == null || token.isBlank()) {
            throw new RuntimeException("Bạn chưa đăng nhập.");
        }

        return new LoginResponseDto(
                null,
                jwtService.extractUsername(token),
                jwtService.extractRole(token),
                jwtService.extractSessionId(token),
                jwtService.toLocalDateTime(jwtService.extractExpiration(token)),
                jwtService.extractCardId(token)
        );
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest request,
                                      HttpServletResponse response) {
        String token = jwtCookieService.resolveToken(request);
        if (token != null && !token.isBlank()) {
            try {
                String sessionId = jwtService.extractSessionId(token);
                loginSessionService.deleteSession(sessionId);
            } catch (Exception ignored) {
                // Nếu token đã hết hạn hoặc không parse được, vẫn phải xóa cookie ở browser.
            }
        }

        jwtCookieService.addLogoutCookie(response);
        return Map.of("message", "Đã đăng xuất.");
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication != null
                && authentication.getAuthorities() != null
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> role.equals(authority.getAuthority()));
    }
}
