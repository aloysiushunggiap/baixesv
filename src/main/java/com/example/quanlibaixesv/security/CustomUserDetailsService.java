package com.example.quanlibaixesv.security;

import com.example.quanlibaixesv.model.AdminAccount;
import com.example.quanlibaixesv.model.Student;
import com.example.quanlibaixesv.repository.AdminAccountRepository;
import com.example.quanlibaixesv.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private AdminAccountRepository adminRepo;

    @Autowired
    private StudentRepository studentRepo;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        AdminAccount admin = adminRepo.findByUsername(username).orElse(null);
        if (admin != null) {
            return new User(
                    admin.getUsername(),
                    admin.getPassword(),
                    List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
            );
        }

        Student student = studentRepo.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy tài khoản"));

        return new User(
                student.getUsername(),
                student.getPassword(),
                List.of(new SimpleGrantedAuthority(student.getRole().name()))
        );
    }
}