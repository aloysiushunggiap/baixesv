package com.example.quanlibaixesv.service;

import com.example.quanlibaixesv.exception.InvalidSessionException;
import com.example.quanlibaixesv.model.UserSession;
import com.example.quanlibaixesv.repository.UserSessionRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class LoginSessionService {

    private final UserSessionRepository userSessionRepo;

    public LoginSessionService(UserSessionRepository userSessionRepo) {
        this.userSessionRepo = userSessionRepo;
    }

    // Tạo session mới sau khi login thành công.
    // expiresAt phải lấy từ JWT exp, không tự tính idle timeout ở đây.
    @Transactional
    public UserSession createSession(String username,
                                     String role,
                                     long tokenVersion,
                                     String cardId,
                                     LocalDateTime expiresAt) {
        cleanupExpiredSessions();

        LocalDateTime now = LocalDateTime.now();

        UserSession session = new UserSession();
        session.setId(UUID.randomUUID().toString());
        session.setUsername(username);
        session.setRole(role);
        session.setCardId(cardId);
        session.setTokenVersion(tokenVersion);
        session.setIssuedAt(now);
        session.setExpiresAt(expiresAt);
        session.setActive(true);

        return userSessionRepo.save(session);
    }

    // Mỗi request có token hợp lệ sẽ kiểm tra thêm sessionId.
    // Không gia hạn thời gian ở đây: thời gian được quyết định hoàn toàn bởi JWT exp.
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

        if (!session.getExpiresAt().isAfter(LocalDateTime.now())) {
            userSessionRepo.deleteById(sessionId);
            throw new InvalidSessionException("Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại.");
        }
    }

    // Khi đổi mật khẩu/reset mật khẩu/xóa tài khoản, xóa toàn bộ session của username đó.
    // Như vậy mọi token cũ còn exp nhưng sessionId không còn trong DB sẽ bị từ chối.
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

        return userSessionRepo.findByActiveTrueAndExpiresAtAfterOrderByExpiresAtAsc(now)
                .stream()
                .map(session -> Map.<String, Object>of(
                        "sessionId", session.getId(),
                        "username", session.getUsername(),
                        "role", session.getRole(),
                        "cardId", session.getCardId() == null ? "" : session.getCardId(),
                        "issuedAt", session.getIssuedAt(),
                        "expiresAt", session.getExpiresAt()
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

    // Tự xóa session hết hạn theo exp của token mỗi 60 giây.
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void cleanupExpiredSessions() {
        userSessionRepo.deleteByExpiresAtBefore(LocalDateTime.now());
    }
}
