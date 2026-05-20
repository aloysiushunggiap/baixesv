package com.example.quanlibaixesv.repository;

import com.example.quanlibaixesv.model.Student;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, String> {
    Optional<Student> findByCardId(String cardId);
    Optional<Student> findByUsername(String username);

    boolean existsByUsername(String username);
    boolean existsByCardId(String cardId);
    boolean existsById(String id);

    @Transactional
    void deleteByCardId(String cardId);
}