package com.example.quanlibaixesv.service;

import com.example.quanlibaixesv.exception.InvalidSessionException;
import com.example.quanlibaixesv.model.UserSession;
import com.example.quanlibaixesv.repository.UserSessionRepository;
import com.example.quanlibaixesv.security.RefreshTokenHashService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class LoginSessionService {

    private final UserSessionRepository userSessionRepo;
    private final RefreshTokenHashService refreshTokenHashService;
    private final SecureRandom secureRandom = new SecureRandom();

    public LoginSessionService(UserSessionRepository userSessionRepo,
                               RefreshTokenHashService refreshTokenHashService) {
        this.userSessionRepo = userSessionRepo;
        this.refreshTokenHashService = refreshTokenHashService;
    }

    public String generateSessionId() {
        return UUID.randomUUID().toString();
    }

    // Refresh Token gốc là chuỗi bí mật; DB chỉ lưu hash của chuỗi này.
    public String generateRefreshSecret() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Transactional
    public UserSession createSession(String sessionId,
                                     String username,
                                     String role,
                                     long tokenVersion,
                                     String cardId,
                                     LocalDateTime accessExpiresAt,
                                     String refreshToken,
                                     LocalDateTime refreshExpiresAt) {
        deactivateExpiredSessions();

        LocalDateTime now = LocalDateTime.now();

        UserSession session = new UserSession();
        session.setId(sessionId);
        session.setUsername(username);
        session.setRole(role);
        session.setCardId(cardId);
        session.setTokenVersion(tokenVersion);
        session.setIssuedAt(now);
        session.setExpiresAt(accessExpiresAt);
        session.setRefreshTokenHash(refreshTokenHashService.hash(refreshToken));
        session.setRefreshExpiresAt(refreshExpiresAt);
        session.setActive(true);

        return userSessionRepo.save(session);
    }

    @Transactional
    public void validateSession(String sessionId,
                                String username,
                                long tokenVersionFromToken) {
        deactivateExpiredSessions();

        if (sessionId == null || sessionId.isBlank()) {
            throw new InvalidSessionException("Phiên đăng nhập không hợp lệ, vui lòng đăng nhập lại.");
        }

        UserSession session = userSessionRepo.findByIdAndActiveTrue(sessionId)
                .orElseThrow(() -> new InvalidSessionException("Phiên đăng nhập đã hết hạn hoặc đã bị hủy."));

        if (!session.getUsername().equals(username)
                || session.getTokenVersion() != tokenVersionFromToken) {
            session.setActive(false);
            userSessionRepo.save(session);
            throw new InvalidSessionException("Phiên đăng nhập không khớp tài khoản, vui lòng đăng nhập lại.");
        }

        if (!session.getRefreshExpiresAt().isAfter(LocalDateTime.now())) {
            session.setActive(false);
            userSessionRepo.save(session);
            throw new InvalidSessionException("Refresh Token đã hết hạn, vui lòng đăng nhập lại.");
        }
    }

    @Transactional
    public UserSession validateRefreshSession(String sessionId,
                                              String username,
                                              long tokenVersionFromToken,
                                              String refreshToken) {
        deactivateExpiredSessions();

        if (sessionId == null || sessionId.isBlank()) {
            throw new InvalidSessionException("Refresh Token không có sessionId.");
        }

        UserSession session = userSessionRepo.findByIdAndActiveTrue(sessionId)
                .orElseThrow(() -> new InvalidSessionException("Phiên đăng nhập đã hết hạn hoặc đã bị hủy."));

        if (!session.getUsername().equals(username)
                || session.getTokenVersion() != tokenVersionFromToken) {
            session.setActive(false);
            userSessionRepo.save(session);
            throw new InvalidSessionException("Refresh Token không khớp tài khoản.");
        }

        if (!session.getRefreshExpiresAt().isAfter(LocalDateTime.now())) {
            session.setActive(false);
            userSessionRepo.save(session);
            throw new InvalidSessionException("Refresh Token đã hết hạn, vui lòng đăng nhập lại.");
        }

        if (!refreshTokenHashService.matches(refreshToken, session.getRefreshTokenHash())) {
            session.setActive(false);
            userSessionRepo.save(session);
            throw new InvalidSessionException("Refresh Token không hợp lệ hoặc đã được thay thế.");
        }

        return session;
    }

    @Transactional
    public UserSession rotateRefreshToken(String sessionId,
                                          String newRefreshToken,
                                          LocalDateTime newRefreshExpiresAt,
                                          LocalDateTime newAccessExpiresAt) {
        UserSession session = userSessionRepo.findByIdAndActiveTrue(sessionId)
                .orElseThrow(() -> new InvalidSessionException("Phiên đăng nhập đã hết hạn hoặc đã bị hủy."));

        session.setRefreshTokenHash(refreshTokenHashService.hash(newRefreshToken));
        session.setRefreshExpiresAt(newRefreshExpiresAt);
        session.setExpiresAt(newAccessExpiresAt);
        session.setActive(true);

        return userSessionRepo.save(session);
    }

    @Transactional(readOnly = true)
    public UserSession getSession(String sessionId) {
        return userSessionRepo.findByIdAndActiveTrue(sessionId)
                .orElseThrow(() -> new InvalidSessionException("Phiên đăng nhập không tồn tại hoặc đã bị hủy."));
    }

    /**
     * Không xóa session khỏi DB nữa.
     * Chỉ chuyển active = false để giữ lịch sử đăng nhập.
     */
    @Transactional
    public void deleteSession(String sessionId) {
        deactivateSession(sessionId);
    }

    @Transactional
    public void deactivateSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }

        userSessionRepo.deactivateById(sessionId);
    }

    @Transactional
    public void deactivateAllSessions(String username) {
        if (username == null || username.isBlank()) {
            return;
        }

        userSessionRepo.deactivateAllByUsername(username);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getActiveSessions() {
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
                        "refreshExpiresAt", session.getRefreshExpiresAt(),
                        "active", session.isActive()
                ))
                .toList();
    }

    /**
     * Dùng cho màn hình lịch sử đăng nhập.
     * Trả về cả phiên đang active và phiên đã hết hạn/logout.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAllSessionHistory() {
        return userSessionRepo.findAllByOrderByIssuedAtDesc()
                .stream()
                .map(session -> Map.<String, Object>of(
                        "sessionId", session.getId(),
                        "username", session.getUsername(),
                        "role", session.getRole(),
                        "cardId", session.getCardId() == null ? "" : session.getCardId(),
                        "issuedAt", session.getIssuedAt(),
                        "expiresAt", session.getExpiresAt(),
                        "refreshExpiresAt", session.getRefreshExpiresAt(),
                        "active", session.isActive()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getSessionHistoryByUsername(String username) {
        return userSessionRepo.findByUsernameOrderByIssuedAtDesc(username)
                .stream()
                .map(session -> Map.<String, Object>of(
                        "sessionId", session.getId(),
                        "username", session.getUsername(),
                        "role", session.getRole(),
                        "cardId", session.getCardId() == null ? "" : session.getCardId(),
                        "issuedAt", session.getIssuedAt(),
                        "expiresAt", session.getExpiresAt(),
                        "refreshExpiresAt", session.getRefreshExpiresAt(),
                        "active", session.isActive()
                ))
                .toList();
    }

    /**
     * Trước đây hàm này xóa dòng hết hạn khỏi DB.
     * Bây giờ chỉ chuyển active = false để giữ lại lịch sử.
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void cleanupExpiredSessions() {
        deactivateExpiredSessions();
    }

    @Transactional
    public void deactivateExpiredSessions() {
        userSessionRepo.deactivateExpiredSessions(LocalDateTime.now());
    }
}