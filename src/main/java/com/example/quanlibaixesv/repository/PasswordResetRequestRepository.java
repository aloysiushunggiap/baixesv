package com.example.quanlibaixesv.repository;

import com.example.quanlibaixesv.model.PasswordResetRequest;
import com.example.quanlibaixesv.model.PasswordResetStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PasswordResetRequestRepository extends JpaRepository<PasswordResetRequest, Long> {
    List<PasswordResetRequest> findAllByOrderByRequestedAtDesc();

    List<PasswordResetRequest> findByStatusOrderByRequestedAtDesc(PasswordResetStatus status);

    boolean existsByStudentIdAndStatus(String studentId, PasswordResetStatus status);
}
