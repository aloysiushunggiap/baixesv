package com.example.quanlibaixesv.controller;

import com.example.quanlibaixesv.dto.LoginRequestDto;
import com.example.quanlibaixesv.dto.LoginResponseDto;
import com.example.quanlibaixesv.dto.RegisterRequestDto;
import com.example.quanlibaixesv.exception.InvalidSessionException;
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
import org.springframework.http.HttpStatus;
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
import org.springframework.web.server.ResponseStatusException;

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
            return loginAdmin(admin, response);
        }

        Student student = studentRepo.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản"));

        return loginStudent(student, response);
    }

    @PostMapping("/refresh")
    public LoginResponseDto refreshAccessToken(HttpServletRequest request,
                                               HttpServletResponse response) {
        String refreshToken = jwtCookieService.resolveRefreshToken(request);
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Không tìm thấy Refresh Token, vui lòng đăng nhập lại.");
        }

        try {
            UserSession session = loginSessionService.validateRefreshToken(refreshToken);

            AdminAccount admin = adminRepo.findByUsername(session.getUsername()).orElse(null);
            if (admin != null) {
                if (!admin.isEnabled()) {
                    loginSessionService.deleteSession(session.getId());
                    throw new InvalidSessionException("Tài khoản admin đang bị khóa.");
                }

                if (admin.getTokenVersion() != session.getTokenVersion()) {
                    loginSessionService.deleteSession(session.getId());
                    throw new InvalidSessionException("Phiên đăng nhập đã bị hủy do đổi mật khẩu.");
                }

                return issueNewAccessTokenForAdmin(admin, session, response);
            }

            Student student = studentRepo.findByUsername(session.getUsername())
                    .orElseThrow(() -> new InvalidSessionException("Không tìm thấy tài khoản user."));

            if (!student.isEnabled()) {
                loginSessionService.deleteSession(session.getId());
                throw new InvalidSessionException("Tài khoản user đang bị khóa.");
            }

            if (student.getTokenVersion() != session.getTokenVersion()) {
                loginSessionService.deleteSession(session.getId());
                throw new InvalidSessionException("Phiên đăng nhập đã bị hủy do đổi mật khẩu.");
            }

            return issueNewAccessTokenForStudent(student, session, response);
        } catch (InvalidSessionException ex) {
            jwtCookieService.addLogoutCookies(response);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, ex.getMessage());
        }
    }

    @GetMapping("/me")
    public LoginResponseDto currentUser(HttpServletRequest request) {
        String token = jwtCookieService.resolveAccessToken(request);
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bạn chưa đăng nhập.");
        }

        String sessionId = jwtService.extractSessionId(token);
        UserSession session = loginSessionService.getSession(sessionId);

        return new LoginResponseDto(
                null,
                jwtService.extractUsername(token),
                jwtService.extractRole(token),
                sessionId,
                jwtService.toLocalDateTime(jwtService.extractExpiration(token)),
                jwtService.extractCardId(token),
                session.getRefreshExpiresAt()
        );
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest request,
                                      HttpServletResponse response) {
        String accessToken = jwtCookieService.resolveAccessToken(request);
        if (accessToken != null && !accessToken.isBlank()) {
            try {
                String sessionId = jwtService.extractSessionId(accessToken);
                loginSessionService.deleteSession(sessionId);
            } catch (Exception ignored) {
                // Nếu access token đã hết hạn hoặc không parse được, thử xóa bằng refresh token bên dưới.
            }
        } else {
            String refreshToken = jwtCookieService.resolveRefreshToken(request);
            loginSessionService.deleteSessionByRefreshToken(refreshToken);
        }

        jwtCookieService.addLogoutCookies(response);
        return Map.of("message", "Đã đăng xuất.");
    }

    private LoginResponseDto loginAdmin(AdminAccount admin, HttpServletResponse response) {
        UserDetails userDetails = org.springframework.security.core.userdetails.User
                .withUsername(admin.getUsername())
                .password(admin.getPassword())
                .authorities("ROLE_ADMIN")
                .build();

        Date accessExpirationDate = jwtService.generateExpirationDate();
        LocalDateTime accessExpiresAt = jwtService.toLocalDateTime(accessExpirationDate);
        LocalDateTime refreshExpiresAt = jwtService.generateRefreshExpirationDateTime();
        String refreshToken = loginSessionService.generateRefreshToken();

        UserSession session = loginSessionService.createSession(
                admin.getUsername(),
                "ROLE_ADMIN",
                admin.getTokenVersion(),
                null,
                accessExpiresAt,
                refreshToken,
                refreshExpiresAt
        );

        String accessToken = jwtService.generateToken(
                userDetails,
                "ROLE_ADMIN",
                null,
                admin.getTokenVersion(),
                session.getId(),
                accessExpirationDate
        );

        addAuthCookies(response, accessToken, refreshToken);

        return new LoginResponseDto(
                null,
                admin.getUsername(),
                "ROLE_ADMIN",
                session.getId(),
                accessExpiresAt,
                null,
                refreshExpiresAt
        );
    }

    private LoginResponseDto loginStudent(Student student, HttpServletResponse response) {
        UserDetails userDetails = org.springframework.security.core.userdetails.User
                .withUsername(student.getUsername())
                .password(student.getPassword())
                .authorities(student.getRole().name())
                .build();

        Date accessExpirationDate = jwtService.generateExpirationDate();
        LocalDateTime accessExpiresAt = jwtService.toLocalDateTime(accessExpirationDate);
        LocalDateTime refreshExpiresAt = jwtService.generateRefreshExpirationDateTime();
        String refreshToken = loginSessionService.generateRefreshToken();

        UserSession session = loginSessionService.createSession(
                student.getUsername(),
                student.getRole().name(),
                student.getTokenVersion(),
                student.getCardId(),
                accessExpiresAt,
                refreshToken,
                refreshExpiresAt
        );

        String accessToken = jwtService.generateToken(
                userDetails,
                student.getRole().name(),
                student.getCardId(),
                student.getTokenVersion(),
                session.getId(),
                accessExpirationDate
        );

        addAuthCookies(response, accessToken, refreshToken);

        return new LoginResponseDto(
                null,
                student.getUsername(),
                student.getRole().name(),
                session.getId(),
                accessExpiresAt,
                student.getCardId(),
                refreshExpiresAt
        );
    }

    private LoginResponseDto issueNewAccessTokenForAdmin(AdminAccount admin,
                                                         UserSession session,
                                                         HttpServletResponse response) {
        UserDetails userDetails = org.springframework.security.core.userdetails.User
                .withUsername(admin.getUsername())
                .password(admin.getPassword())
                .authorities("ROLE_ADMIN")
                .build();

        Date accessExpirationDate = jwtService.generateExpirationDate();
        LocalDateTime accessExpiresAt = jwtService.toLocalDateTime(accessExpirationDate);
        LocalDateTime refreshExpiresAt = jwtService.generateRefreshExpirationDateTime();
        String newRefreshToken = loginSessionService.generateRefreshToken();

        UserSession updatedSession = loginSessionService.rotateRefreshToken(
                session.getId(),
                newRefreshToken,
                refreshExpiresAt,
                accessExpiresAt
        );

        String accessToken = jwtService.generateToken(
                userDetails,
                "ROLE_ADMIN",
                null,
                admin.getTokenVersion(),
                updatedSession.getId(),
                accessExpirationDate
        );

        addAuthCookies(response, accessToken, newRefreshToken);

        return new LoginResponseDto(
                null,
                admin.getUsername(),
                "ROLE_ADMIN",
                updatedSession.getId(),
                accessExpiresAt,
                null,
                refreshExpiresAt
        );
    }

    private LoginResponseDto issueNewAccessTokenForStudent(Student student,
                                                           UserSession session,
                                                           HttpServletResponse response) {
        UserDetails userDetails = org.springframework.security.core.userdetails.User
                .withUsername(student.getUsername())
                .password(student.getPassword())
                .authorities(student.getRole().name())
                .build();

        Date accessExpirationDate = jwtService.generateExpirationDate();
        LocalDateTime accessExpiresAt = jwtService.toLocalDateTime(accessExpirationDate);
        LocalDateTime refreshExpiresAt = jwtService.generateRefreshExpirationDateTime();
        String newRefreshToken = loginSessionService.generateRefreshToken();

        UserSession updatedSession = loginSessionService.rotateRefreshToken(
                session.getId(),
                newRefreshToken,
                refreshExpiresAt,
                accessExpiresAt
        );

        String accessToken = jwtService.generateToken(
                userDetails,
                student.getRole().name(),
                student.getCardId(),
                student.getTokenVersion(),
                updatedSession.getId(),
                accessExpirationDate
        );

        addAuthCookies(response, accessToken, newRefreshToken);

        return new LoginResponseDto(
                null,
                student.getUsername(),
                student.getRole().name(),
                updatedSession.getId(),
                accessExpiresAt,
                student.getCardId(),
                refreshExpiresAt
        );
    }

    private void addAuthCookies(HttpServletResponse response, String accessToken, String refreshToken) {
        jwtCookieService.addAccessTokenCookie(response, accessToken, jwtService.getJwtExpirationMillis());
        jwtCookieService.addRefreshTokenCookie(response, refreshToken, jwtService.getRefreshTokenExpirationMillis());
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication != null
                && authentication.getAuthorities() != null
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> role.equals(authority.getAuthority()));
    }
}
