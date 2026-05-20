package com.example.quanlibaixesv.config;

import com.example.quanlibaixesv.model.AdminAccount;
import com.example.quanlibaixesv.repository.AdminAccountRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initAdmin(AdminAccountRepository repo, PasswordEncoder encoder) {
        return args -> {
            if (repo.findByUsername("admin").isEmpty()) {
                AdminAccount admin = new AdminAccount();
                admin.setUsername("admin");
                admin.setPassword(encoder.encode("123456"));
                admin.setEnabled(true);
                repo.save(admin);
            }
        };
    }
}