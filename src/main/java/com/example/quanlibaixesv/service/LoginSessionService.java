package com.example.quanlibaixesv.service;

import com.example.quanlibaixesv.exception.InvalidSessionException;
import com.example.quanlibaixesv.model.UserSession;
import com.example.quanlibaixesv.repository.UserSessionRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class LoginSessionService {

    private final UserSessionRepository userSessionRepo;
    private final SecureRandom secureRandom = new SecureRandom();

    public LoginSessionService(UserSessionRepository userSessionRepo) {
        this.userSessionRepo = userSessionRepo;
    }

    // Tạo refresh token dạng random đủ mạnh. Token gốc chỉ đưa vào cookie HttpOnly.
    public String generateRefreshToken() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // Chỉ lưu hash của refresh token trong database để nếu DB bị lộ thì token gốc không bị lộ trực tiếp.
    public String hashRefreshToken(String refreshToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(refreshToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Không thể hash refresh token.", e);
        }
    }

    // Tạo session mới sau khi login thành công.
    @Transactional
    public UserSession createSession(String username,
                                     String role,
                                     long tokenVersion,
                                     String cardId,
                                     LocalDateTime accessExpiresAt,
                                     String refreshToken,
                                     LocalDateTime refreshExpiresAt) {
        cleanupExpiredSessions();

        LocalDateTime now = LocalDateTime.now();

        UserSession session = new UserSession();
        session.setId(UUID.randomUUID().toString());
        session.setUsername(username);
        session.setRole(role);
        session.setCardId(cardId);
        session.setTokenVersion(tokenVersion);
        session.setIssuedAt(now);
        session.setExpiresAt(accessExpiresAt);
        session.setRefreshTokenHash(hashRefreshToken(refreshToken));
        session.setRefreshExpiresAt(refreshExpiresAt);
        session.setActive(true);

        return userSessionRepo.save(session);
    }

    // Mỗi request có Access Token hợp lệ sẽ kiểm tra thêm sessionId.
    // Access Token hết hạn do JwtService xử lý bằng claim exp; không xóa session ở đây vì Refresh Token có thể còn hạn.
    @Transactional
    public void validateSession(String sessionId, String username, long tokenVersionFromToken) {
        cleanupExpiredSessions();

        if (sessionId == null || sessionId.isBlank()) {
            throw new InvalidSessionException("Phiên đăng nhập không hợp lệ, vui lòng đăng nhập lại.");
        }

        UserSession session = userSessionRepo.findByIdAndActiveTrue(sessionId)
                .orElseThrow(() -> new InvalidSessionException("Phiên đăng nhập đã hết hạn hoặc đã bị hủy."));

        if (!session.getUsername().equals(username) || session.getTokenVersion() != tokenVersionFromToken) {
            userSessionRepo.deleteById(sessionId);
            throw new InvalidSessionException("Phiên đăng nhập không khớp tài khoản, vui lòng đăng nhập lại.");
        }

        if (!session.getRefreshExpiresAt().isAfter(LocalDateTime.now())) {
            userSessionRepo.deleteById(sessionId);
            throw new InvalidSessionException("Refresh Token đã hết hạn, vui lòng đăng nhập lại.");
        }
    }


    @Transactional(readOnly = true)
    public UserSession getSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new InvalidSessionException("Phiên đăng nhập không hợp lệ, vui lòng đăng nhập lại.");
        }

        UserSession session = userSessionRepo.findByIdAndActiveTrue(sessionId)
                .orElseThrow(() -> new InvalidSessionException("Phiên đăng nhập đã hết hạn hoặc đã bị hủy."));

        if (!session.getRefreshExpiresAt().isAfter(LocalDateTime.now())) {
            throw new InvalidSessionException("Refresh Token đã hết hạn, vui lòng đăng nhập lại.");
        }

        return session;
    }

    // Kiểm tra Refresh Token để cấp Access Token mới.
    @Transactional
    public UserSession validateRefreshToken(String refreshToken) {
        cleanupExpiredSessions();

        if (refreshToken == null || refreshToken.isBlank()) {
            throw new InvalidSessionException("Không tìm thấy Refresh Token, vui lòng đăng nhập lại.");
        }

        String refreshHash = hashRefreshToken(refreshToken);
        UserSession session = userSessionRepo.findByRefreshTokenHashAndActiveTrue(refreshHash)
                .orElseThrow(() -> new InvalidSessionException("Refresh Token không hợp lệ hoặc đã bị thu hồi."));

        if (!session.getRefreshExpiresAt().isAfter(LocalDateTime.now())) {
            userSessionRepo.deleteById(session.getId());
            throw new InvalidSessionException("Refresh Token đã hết hạn, vui lòng đăng nhập lại.");
        }

        return session;
    }

    // Mỗi lần refresh thành công thì rotate Refresh Token và cập nhật hạn Access Token mới.
    @Transactional
    public UserSession rotateRefreshToken(String sessionId,
                                          String newRefreshToken,
                                          LocalDateTime newRefreshExpiresAt,
                                          LocalDateTime newAccessExpiresAt) {
        UserSession session = userSessionRepo.findByIdAndActiveTrue(sessionId)
                .orElseThrow(() -> new InvalidSessionException("Phiên đăng nhập không tồn tại hoặc đã bị hủy."));

        session.setRefreshTokenHash(hashRefreshToken(newRefreshToken));
        session.setRefreshExpiresAt(newRefreshExpiresAt);
        session.setExpiresAt(newAccessExpiresAt);
        return userSessionRepo.save(session);
    }

    // Khi đổi mật khẩu/reset mật khẩu/xóa tài khoản, xóa toàn bộ session của username đó.
    @Transactional
    public void deactivateAllSessions(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        userSessionRepo.deleteByUsername(username);
    }

    @Transactional
    public List<Map<String, Object>> getActiveSessions() {
        cleanupExpiredSessions();

        LocalDateTime now = LocalDateTime.now();

        return userSessionRepo.findByActiveTrueAndRefreshExpiresAtAfterOrderByRefreshExpiresAtAsc(now)
                .stream()
                .map(session -> Map.<String, Object>of(
                        "sessionId", session.getId(),
                        "username", session.getUsername(),
                        "role", session.getRole(),
                        "cardId", session.getCardId() == null ? "" : session.getCardId(),
                        "issuedAt", session.getIssuedAt(),
                        "expiresAt", session.getExpiresAt(),
                        "refreshExpiresAt", session.getRefreshExpiresAt()
                ))
                .toList();
    }

    @Transactional
    public void deleteSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new RuntimeException("sessionId không được để trống.");
        }
        userSessionRepo.deleteById(sessionId);
    }

    @Transactional
    public void deleteSessionByRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        String refreshHash = hashRefreshToken(refreshToken);
        userSessionRepo.findByRefreshTokenHashAndActiveTrue(refreshHash)
                .ifPresent(session -> userSessionRepo.deleteById(session.getId()));
    }

    // Tự xóa session khi Refresh Token hết hạn. Access Token hết hạn không xóa session ngay vì còn dùng Refresh Token để cấp lại.
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void cleanupExpiredSessions() {
        userSessionRepo.deleteByRefreshExpiresAtBefore(LocalDateTime.now());
    }
}
