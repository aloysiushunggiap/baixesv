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
        deactivateFullyExpiredSessions();

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

    /*
     * Hàm này dùng để kiểm tra Access Token.
     *
     * Quan trọng:
     * Không kiểm tra refreshExpiresAt ở đây.
     *
     * Vì khi AT đã được cấp mới lúc RT còn hạn, AT đó phải được dùng cho tới khi AT hết hạn.
     * Ví dụ AT=2 phút, RT=3 phút:
     * - phút 2 refresh thành công, AT mới sống tới phút 4
     * - phút 3 RT hết hạn
     * - từ phút 3 đến phút 4 AT mới vẫn phải dùng được
     */
    @Transactional
    public void validateSession(String sessionId,
                                String username,
                                long tokenVersionFromToken) {
        deactivateFullyExpiredSessions();

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
    }

    /*
     * Hàm này dùng riêng cho Refresh Token.
     * Chỉ ở đây mới kiểm tra refreshExpiresAt.
     */
    @Transactional
    public UserSession validateRefreshSession(String sessionId,
                                              String username,
                                              long tokenVersionFromToken,
                                              String refreshToken) {
        deactivateFullyExpiredSessions();

        if (sessionId == null || sessionId.isBlank()) {
            throw new InvalidSessionException("Refresh Token không có sessionId.");
        }

        UserSession session = userSessionRepo.findByIdAndActiveTrue(sessionId)
                .orElseThrow(() -> new InvalidSessionException("Phiên đăng nhập đã hết hạn hoặc đã bị hủy."));

        LocalDateTime now = LocalDateTime.now();

        if (!session.getUsername().equals(username)
                || session.getTokenVersion() != tokenVersionFromToken) {
            session.setActive(false);
            userSessionRepo.save(session);
            throw new InvalidSessionException("Refresh Token không khớp tài khoản.");
        }

        if (!session.getRefreshExpiresAt().isAfter(now)) {
            /*
             * Không tắt session ngay nếu AT hiện tại vẫn còn hạn.
             * Nếu AT cũng hết hạn rồi thì mới active=false.
             */
            if (!session.getExpiresAt().isAfter(now)) {
                session.setActive(false);
                userSessionRepo.save(session);
            }

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

        return userSessionRepo.findUsableActiveSessions(now)
                .stream()
                .map(this::toSessionMap)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAllSessionHistory() {
        return userSessionRepo.findAllByOrderByIssuedAtDesc()
                .stream()
                .map(this::toSessionMap)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getSessionHistoryByUsername(String username) {
        return userSessionRepo.findByUsernameOrderByIssuedAtDesc(username)
                .stream()
                .map(this::toSessionMap)
                .toList();
    }

    private Map<String, Object> toSessionMap(UserSession session) {
        return Map.of(
                "sessionId", session.getId(),
                "username", session.getUsername(),
                "role", session.getRole(),
                "cardId", session.getCardId() == null ? "" : session.getCardId(),
                "issuedAt", session.getIssuedAt(),
                "expiresAt", session.getExpiresAt(),
                "refreshExpiresAt", session.getRefreshExpiresAt(),
                "active", session.isActive()
        );
    }

    /*
     * Chỉ dọn khi cả AT hiện tại và RT đều đã hết hạn.
     * Không dọn ngay lúc RT hết hạn, vì AT mới có thể vẫn còn hạn.
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void cleanupExpiredSessions() {
        deactivateFullyExpiredSessions();
    }

    @Transactional
    public void deactivateFullyExpiredSessions() {
        userSessionRepo.deactivateFullyExpiredSessions(LocalDateTime.now());
    }
}

