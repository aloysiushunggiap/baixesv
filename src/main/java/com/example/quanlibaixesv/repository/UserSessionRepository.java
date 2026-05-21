package com.example.quanlibaixesv.repository;

import com.example.quanlibaixesv.model.UserSession;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserSessionRepository extends JpaRepository<UserSession, String> {

    Optional<UserSession> findByIdAndActiveTrue(String id);

    Optional<UserSession> findByRefreshTokenHashAndActiveTrue(String refreshTokenHash);

    // Một phiên còn hiệu lực nếu active=true và Refresh Token chưa hết hạn.
    List<UserSession> findByActiveTrueAndRefreshExpiresAtAfterOrderByRefreshExpiresAtAsc(LocalDateTime now);

    @Transactional
    void deleteByRefreshExpiresAtBefore(LocalDateTime now);

    @Transactional
    void deleteByUsername(String username);
}
