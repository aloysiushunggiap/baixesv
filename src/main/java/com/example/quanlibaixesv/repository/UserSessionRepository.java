package com.example.quanlibaixesv.repository;

import com.example.quanlibaixesv.model.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserSessionRepository extends JpaRepository<UserSession, String> {
    Optional<UserSession> findByIdAndActiveTrue(String id);

    @Modifying
    @Query("UPDATE UserSession s SET s.active = false WHERE s.username = :username AND s.active = true")
    int deactivateAllByUsername(@Param("username") String username);
}
