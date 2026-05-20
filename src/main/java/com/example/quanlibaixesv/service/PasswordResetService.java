package com.example.quanlibaixesv.service;

import com.example.quanlibaixesv.dto.ForgotPasswordRequestDto;
import com.example.quanlibaixesv.dto.PasswordResetDecisionDto;
import com.example.quanlibaixesv.dto.PasswordResetResponseDto;
import com.example.quanlibaixesv.model.PasswordResetRequest;
import com.example.quanlibaixesv.model.PasswordResetStatus;
import com.example.quanlibaixesv.model.Student;
import com.example.quanlibaixesv.repository.PasswordResetRequestRepository;
import com.example.quanlibaixesv.repository.StudentRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PasswordResetService {

    private final PasswordResetRequestRepository resetRequestRepo;
    private final StudentRepository studentRepo;
    private final PasswordEncoder passwordEncoder;
    private final LoginSessionService loginSessionService;

    public PasswordResetService(PasswordResetRequestRepository resetRequestRepo,
                                StudentRepository studentRepo,
                                PasswordEncoder passwordEncoder,
                                LoginSessionService loginSessionService) {
        this.resetRequestRepo = resetRequestRepo;
        this.studentRepo = studentRepo;
        this.passwordEncoder = passwordEncoder;
        this.loginSessionService = loginSessionService;
    }

    @Transactional
    public PasswordResetResponseDto createRequest(ForgotPasswordRequestDto dto) {
        validateCreateInput(dto);

        String studentId = dto.getStudentId().trim();
        Student student = studentRepo.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Thông tin sinh viên chưa chính xác"));

        if (!student.isEnabled()) {
            throw new RuntimeException("Tài khoản user đang bị khóa");
        }

        if (!normalizeName(student.getName()).equalsIgnoreCase(normalizeName(dto.getName()))
                || !normalizePlate(student.getLicensePlate()).equals(normalizePlate(dto.getLicensePlate()))) {
            throw new RuntimeException("Thông tin đã nhập không đúng");
        }

        if (resetRequestRepo.existsByStudentIdAndStatus(studentId, PasswordResetStatus.PENDING)) {
            throw new RuntimeException("Yêu cầu đang được xử lí");
        }

        PasswordResetRequest request = new PasswordResetRequest();
        request.setStudentId(student.getId());
        request.setName(student.getName());
        request.setLicensePlate(student.getLicensePlate());
        request.setUsername(student.getUsername());
        request.setNewPasswordHash(passwordEncoder.encode(dto.getNewPassword()));
        request.setStatus(PasswordResetStatus.PENDING);
        request.setRequestedAt(LocalDateTime.now());

        PasswordResetRequest saved = resetRequestRepo.save(request);
        return PasswordResetResponseDto.fromEntity("Yêu cầu đã được gửi cho admin", saved);
    }

    @Transactional(readOnly = true)
    public List<PasswordResetResponseDto> getRequests(PasswordResetStatus status) {
        List<PasswordResetRequest> requests = status == null
                ? resetRequestRepo.findAllByOrderByRequestedAtDesc()
                : resetRequestRepo.findByStatusOrderByRequestedAtDesc(status);

        return requests.stream()
                .map(request -> PasswordResetResponseDto.fromEntity(null, request))
                .toList();
    }

    @Transactional
    public PasswordResetResponseDto decide(Long id, PasswordResetDecisionDto dto, String adminUsername) {
        if (dto == null || dto.getApproved() == null) {
            throw new RuntimeException("Lua chon approved khong duoc de trong.");
        }

        PasswordResetRequest request = resetRequestRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Khong tim thay yeu cau quen mat khau id = " + id));

        if (request.getStatus() != PasswordResetStatus.PENDING) {
            throw new RuntimeException("Yeu cau nay da duoc xu ly truoc do.");
        }

        request.setProcessedAt(LocalDateTime.now());
        request.setProcessedBy(adminUsername);

        if (dto.getApproved()) {
            Student student = studentRepo.findById(request.getStudentId())
                    .orElseThrow(() -> new RuntimeException("Khong tim thay sinh vien can reset mat khau."));

            student.setPassword(request.getNewPasswordHash());
            student.setTokenVersion(student.getTokenVersion() + 1);
            studentRepo.save(student);

            loginSessionService.deactivateAllSessions(student.getUsername());

            request.setStatus(PasswordResetStatus.APPROVED);
            PasswordResetRequest saved = resetRequestRepo.save(request);
            return PasswordResetResponseDto.fromEntity("Admin da phe duyet. Mat khau moi da co hieu luc.", saved);
        }

        request.setStatus(PasswordResetStatus.REJECTED);
        request.setRejectReason(dto.getReason());
        PasswordResetRequest saved = resetRequestRepo.save(request);
        return PasswordResetResponseDto.fromEntity("Admin đã từ chối yêu cầu của bạn", saved);
    }

    private void validateCreateInput(ForgotPasswordRequestDto dto) {
        if (dto == null) {
            throw new RuntimeException("Du lieu yeu cau khong duoc de trong.");
        }
        if (isBlank(dto.getName())) {
            throw new RuntimeException("Ho ten khong duoc de trong.");
        }
        if (isBlank(dto.getStudentId())) {
            throw new RuntimeException("Ma sinh vien khong duoc de trong.");
        }
        if (isBlank(dto.getLicensePlate())) {
            throw new RuntimeException("Bien so xe khong duoc de trong.");
        }
        if (isBlank(dto.getNewPassword())) {
            throw new RuntimeException("Mat khau moi khong duoc de trong.");
        }
        if (dto.getNewPassword().length() < 6) {
            throw new RuntimeException("Mat khau moi phai co it nhat 6 ky tu.");
        }
        if (dto.getNewPassword().contains(" ")) {
            throw new RuntimeException("Mat khau moi khong nen co dau cach.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalizeName(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private String normalizePlate(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", "").toUpperCase();
    }
}
