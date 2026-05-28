package com.example.quanlibaixesv.repository;

import com.example.quanlibaixesv.model.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserSessionRepository extends JpaRepository<UserSession, String> {

    Optional<UserSession> findByIdAndActiveTrue(String id);


    List<UserSession> findByUsernameOrderByIssuedAtDesc(String username);

    List<UserSession> findAllByOrderByIssuedAtDesc();

    /*
     * Một session còn "có thể dùng" nếu:
     * - active = true
     * - Access Token hiện tại còn hạn, hoặc Refresh Token còn hạn để cấp AT mới
     */
    @Query("""
            SELECT s
            FROM UserSession s
            WHERE s.active = true
              AND (
                    s.expiresAt > :now
                    OR s.refreshExpiresAt > :now
                  )
            ORDER BY s.issuedAt DESC
            """)
    List<UserSession> findUsableActiveSessions(@Param("now") LocalDateTime now);

    @Modifying
    @Query("""
            UPDATE UserSession s
            SET s.active = false
            WHERE s.id = :sessionId
            """)
    int deactivateById(@Param("sessionId") String sessionId);

    @Modifying
    @Query("""
            UPDATE UserSession s
            SET s.active = false
            WHERE s.username = :username
              AND s.active = true
            """)
    int deactivateAllByUsername(@Param("username") String username);

    /*
     * Không được tắt session chỉ vì RT hết hạn.
     * Chỉ tắt khi cả AT hiện tại và RT đều đã hết hạn.
     */
    @Modifying
    @Query("""
            UPDATE UserSession s
            SET s.active = false
            WHERE s.active = true
              AND s.expiresAt <= :now
              AND s.refreshExpiresAt <= :now
            """)
    int deactivateFullyExpiredSessions(@Param("now") LocalDateTime now);
}