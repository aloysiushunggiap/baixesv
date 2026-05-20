package com.example.quanlibaixesv.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "students_list")
@Data
public class Student {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String licensePlate;

    @Column(nullable = false, unique = true)
    private String cardId;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.ROLE_USER;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false, name = "card_secret", length = 128)
    private String cardSecret;

    // Tang moi khi doi mat khau/reset mat khau de vo hieu hoa toan bo token cu.
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private long tokenVersion = 0L;
}
