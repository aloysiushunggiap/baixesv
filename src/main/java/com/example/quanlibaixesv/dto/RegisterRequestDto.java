package com.example.quanlibaixesv.dto;

import com.example.quanlibaixesv.model.Role;
import lombok.Data;

@Data
public class RegisterRequestDto {
    private String username;
    private String password;
    private Role role;

    // dùng cho sinh viên
    private String studentId;     // mã sinh viên
    private String name;          // họ tên
    private String licensePlate;  // biển số
}