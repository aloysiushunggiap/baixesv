package com.example.quanlibaixesv.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "admin_accounts")
@Data
public class AdminAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private boolean enabled = true;

    // Tang moi khi doi mat khau de vo hieu hoa toan bo token cu.
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private long tokenVersion = 0L;
}
