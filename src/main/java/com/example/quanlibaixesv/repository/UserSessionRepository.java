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

    List<UserSession> findByActiveTrueAndRefreshExpiresAtAfterOrderByRefreshExpiresAtAsc(LocalDateTime now);

    List<UserSession> findByUsernameOrderByIssuedAtDesc(String username);

    List<UserSession> findAllByOrderByIssuedAtDesc();

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

    @Modifying
    @Query("""
            UPDATE UserSession s
            SET s.active = false
            WHERE s.refreshExpiresAt <= :now
              AND s.active = true
            """)
    int deactivateExpiredSessions(@Param("now") LocalDateTime now);
}