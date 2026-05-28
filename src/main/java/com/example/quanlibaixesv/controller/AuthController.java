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
import io.jsonwebtoken.JwtException;
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

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
    public Map<String, Object> register(@RequestBody RegisterRequestDto request,
                                        Authentication authentication) {
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
            generatedCardId = "CARD-" + UUID.randomUUID()
                    .toString()
                    .substring(0, 8)
                    .toUpperCase();
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

        AdminAccount admin = adminRepo.findByUsername(request.getUsername())
                .orElse(null);

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
            jwtCookieService.addLogoutCookies(response);
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Không tìm thấy Refresh Token, vui lòng đăng nhập lại."
            );
        }

        try {
            if (!jwtService.isRefreshToken(refreshToken)) {
                throw new InvalidSessionException("Token gửi lên không phải Refresh Token.");
            }

            String username = jwtService.extractUsername(refreshToken);
            String sessionId = jwtService.extractSessionId(refreshToken);
            long tokenVersion = jwtService.extractTokenVersion(refreshToken);

            UserSession session = loginSessionService.validateRefreshSession(
                    sessionId,
                    username,
                    tokenVersion,
                    refreshToken
            );

            AdminAccount admin = adminRepo.findByUsername(session.getUsername())
                    .orElse(null);

            if (admin != null) {
                if (!admin.isEnabled()) {
                    loginSessionService.deleteSession(session.getId());
                    throw new InvalidSessionException("Tài khoản admin đang bị khóa.");
                }

                if (admin.getTokenVersion() != tokenVersion) {
                    loginSessionService.deleteSession(session.getId());
                    throw new InvalidSessionException("Phiên đăng nhập đã bị hủy do đổi mật khẩu.");
                }

                return issueNewTokensForAdmin(admin, session, response);
            }

            Student student = studentRepo.findByUsername(session.getUsername())
                    .orElseThrow(() -> new InvalidSessionException("Không tìm thấy tài khoản user."));

            if (!student.isEnabled()) {
                loginSessionService.deleteSession(session.getId());
                throw new InvalidSessionException("Tài khoản user đang bị khóa.");
            }

            if (student.getTokenVersion() != tokenVersion) {
                loginSessionService.deleteSession(session.getId());
                throw new InvalidSessionException("Phiên đăng nhập đã bị hủy do đổi mật khẩu.");
            }

            return issueNewTokensForStudent(student, session, response);
        } catch (JwtException | IllegalArgumentException | InvalidSessionException ex) {
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
        String refreshToken = jwtCookieService.resolveRefreshToken(request);

        deleteSessionFromAnyToken(accessToken);
        deleteSessionFromAnyToken(refreshToken);

        jwtCookieService.addLogoutCookies(response);

        return Map.of("message", "Đã đăng xuất.");
    }

    private LoginResponseDto loginAdmin(AdminAccount admin,
                                        HttpServletResponse response) {
        UserDetails userDetails = org.springframework.security.core.userdetails.User
                .withUsername(admin.getUsername())
                .password(admin.getPassword())
                .authorities("ROLE_ADMIN")
                .build();

        String sessionId = loginSessionService.generateSessionId();

        Date accessExpirationDate = jwtService.generateExpirationDate();
        Date refreshExpirationDate = jwtService.generateRefreshExpirationDate();

        LocalDateTime accessExpiresAt = jwtService.toLocalDateTime(accessExpirationDate);
        LocalDateTime refreshExpiresAt = jwtService.toLocalDateTime(refreshExpirationDate);

        String accessToken = jwtService.generateToken(
                userDetails,
                "ROLE_ADMIN",
                null,
                admin.getTokenVersion(),
                sessionId,
                accessExpirationDate
        );

        String refreshToken = jwtService.generateRefreshToken(
                admin.getUsername(),
                admin.getTokenVersion(),
                sessionId,
                refreshExpirationDate
        );

        UserSession session = loginSessionService.createSession(
                sessionId,
                admin.getUsername(),
                "ROLE_ADMIN",
                admin.getTokenVersion(),
                null,
                accessExpiresAt,
                refreshToken,
                refreshExpiresAt
        );

        addAuthCookies(response, accessToken, refreshToken, jwtService.getRefreshTokenExpirationMillis());

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

    private LoginResponseDto loginStudent(Student student,
                                          HttpServletResponse response) {
        UserDetails userDetails = org.springframework.security.core.userdetails.User
                .withUsername(student.getUsername())
                .password(student.getPassword())
                .authorities(student.getRole().name())
                .build();

        String sessionId = loginSessionService.generateSessionId();

        Date accessExpirationDate = jwtService.generateExpirationDate();
        Date refreshExpirationDate = jwtService.generateRefreshExpirationDate();

        LocalDateTime accessExpiresAt = jwtService.toLocalDateTime(accessExpirationDate);
        LocalDateTime refreshExpiresAt = jwtService.toLocalDateTime(refreshExpirationDate);

        String accessToken = jwtService.generateToken(
                userDetails,
                student.getRole().name(),
                student.getCardId(),
                student.getTokenVersion(),
                sessionId,
                accessExpirationDate
        );

        String refreshToken = jwtService.generateRefreshToken(
                student.getUsername(),
                student.getTokenVersion(),
                sessionId,
                refreshExpirationDate
        );

        UserSession session = loginSessionService.createSession(
                sessionId,
                student.getUsername(),
                student.getRole().name(),
                student.getTokenVersion(),
                student.getCardId(),
                accessExpiresAt,
                refreshToken,
                refreshExpiresAt
        );

        addAuthCookies(response, accessToken, refreshToken, jwtService.getRefreshTokenExpirationMillis());

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

    private LoginResponseDto issueNewTokensForAdmin(AdminAccount admin,
                                                    UserSession session,
                                                    HttpServletResponse response) {
        UserDetails userDetails = org.springframework.security.core.userdetails.User
                .withUsername(admin.getUsername())
                .password(admin.getPassword())
                .authorities("ROLE_ADMIN")
                .build();

        Date accessExpirationDate = jwtService.generateExpirationDate();
        LocalDateTime accessExpiresAt = jwtService.toLocalDateTime(accessExpirationDate);

        /*
         * Quan trọng:
         * Không tạo refreshExpirationDate mới bằng jwtService.generateRefreshExpirationDate().
         * Dùng lại hạn RT cũ của session để RT không bị gia hạn sau mỗi lần refresh.
         */
        LocalDateTime refreshExpiresAt = session.getRefreshExpiresAt();
        Date refreshExpirationDate = toDate(refreshExpiresAt);

        String newAccessToken = jwtService.generateToken(
                userDetails,
                "ROLE_ADMIN",
                null,
                admin.getTokenVersion(),
                session.getId(),
                accessExpirationDate
        );

        String newRefreshToken = jwtService.generateRefreshToken(
                admin.getUsername(),
                admin.getTokenVersion(),
                session.getId(),
                refreshExpirationDate
        );

        UserSession updatedSession = loginSessionService.rotateRefreshToken(
                session.getId(),
                newRefreshToken,
                refreshExpiresAt,
                accessExpiresAt
        );

        addAuthCookies(response, newAccessToken, newRefreshToken, remainingMillis(refreshExpiresAt));

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

    private LoginResponseDto issueNewTokensForStudent(Student student,
                                                      UserSession session,
                                                      HttpServletResponse response) {
        UserDetails userDetails = org.springframework.security.core.userdetails.User
                .withUsername(student.getUsername())
                .password(student.getPassword())
                .authorities(student.getRole().name())
                .build();

        Date accessExpirationDate = jwtService.generateExpirationDate();
        LocalDateTime accessExpiresAt = jwtService.toLocalDateTime(accessExpirationDate);


        LocalDateTime refreshExpiresAt = session.getRefreshExpiresAt();
        Date refreshExpirationDate = toDate(refreshExpiresAt);

        String newAccessToken = jwtService.generateToken(
                userDetails,
                student.getRole().name(),
                student.getCardId(),
                student.getTokenVersion(),
                session.getId(),
                accessExpirationDate
        );

        String newRefreshToken = jwtService.generateRefreshToken(
                student.getUsername(),
                student.getTokenVersion(),
                session.getId(),
                refreshExpirationDate
        );

        UserSession updatedSession = loginSessionService.rotateRefreshToken(
                session.getId(),
                newRefreshToken,
                refreshExpiresAt,
                accessExpiresAt
        );

        addAuthCookies(response, newAccessToken, newRefreshToken, remainingMillis(refreshExpiresAt));

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

    private void addAuthCookies(HttpServletResponse response,
                                String accessToken,
                                String refreshToken,
                                long refreshCookieMaxAgeMillis) {
        jwtCookieService.addAccessTokenCookie(
                response,
                accessToken,
                jwtService.getJwtExpirationMillis()
        );

        jwtCookieService.addRefreshTokenCookie(
                response,
                refreshToken,
                Math.max(0, refreshCookieMaxAgeMillis)
        );
    }

    private Date toDate(LocalDateTime localDateTime) {
        return Date.from(
                localDateTime
                        .atZone(ZoneId.systemDefault())
                        .toInstant()
        );
    }

    private long remainingMillis(LocalDateTime expiresAt) {
        return Duration.between(LocalDateTime.now(), expiresAt).toMillis();
    }

    private void deleteSessionFromAnyToken(String token) {
        if (token == null || token.isBlank()) {
            return;
        }

        try {
            String sessionId = jwtService.extractSessionId(token);
            loginSessionService.deleteSession(sessionId);
        } catch (Exception ignored) {
            // Token hết hạn hoặc không parse được thì bỏ qua.
            // Frontend vẫn xóa localStorage, backend vẫn clear cookie.
        }
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication != null
                && authentication.getAuthorities() != null
                && authentication.getAuthorities()
                .stream()
                .anyMatch(authority -> role.equals(authority.getAuthority()));
    }
}