package com.example.quanlibaixesv.service;

import com.example.quanlibaixesv.exception.InvalidSessionException;
import com.example.quanlibaixesv.model.UserSession;
import com.example.quanlibaixesv.repository.UserSessionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class LoginSessionService {

    private final UserSessionRepository userSessionRepo;

    @Value("${app.session.idle-timeout-minutes:5}")
    private long idleTimeoutMinutes;

    public LoginSessionService(UserSessionRepository userSessionRepo) {
        this.userSessionRepo = userSessionRepo;
    }

    @Transactional
    public UserSession createSession(String username, String role, long tokenVersion) {
        LocalDateTime now = LocalDateTime.now();

        UserSession session = new UserSession();
        session.setId(UUID.randomUUID().toString());
        session.setUsername(username);
        session.setRole(role);
        session.setTokenVersion(tokenVersion);
        session.setCreatedAt(now);
        session.setLastAccessAt(now);
        session.setActive(true);

        return userSessionRepo.save(session);
    }

    @Transactional
    public void validateAndTouch(String sessionId, String username, long tokenVersionFromToken) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new InvalidSessionException("Phien dang nhap khong hop le, vui long dang nhap lai.");
        }

        UserSession session = userSessionRepo.findByIdAndActiveTrue(sessionId)
                .orElseThrow(() -> new InvalidSessionException("Phien dang nhap da bi huy, vui long dang nhap lai."));

        if (!session.getUsername().equals(username) || session.getTokenVersion() != tokenVersionFromToken) {
            session.setActive(false);
            userSessionRepo.save(session);
            throw new InvalidSessionException("Phien dang nhap khong khop tai khoan, vui long dang nhap lai.");
        }

        LocalDateTime now = LocalDateTime.now();
        if (!now.isBefore(session.getLastAccessAt().plusMinutes(idleTimeoutMinutes))) {
            session.setActive(false);
            userSessionRepo.save(session);
            throw new InvalidSessionException("Ban da khong thao tac qua 5 phut, vui long dang nhap lai.");
        }

        session.setLastAccessAt(now);
        userSessionRepo.save(session);
    }

    @Transactional
    public void deactivateAllSessions(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        userSessionRepo.deactivateAllByUsername(username);
    }
}
