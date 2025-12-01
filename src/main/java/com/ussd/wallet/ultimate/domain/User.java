package com.ussd.wallet.ultimate.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String msisdn;

    private String fullName;

    private String defaultCurrency = "NGN";

    private Instant createdAt = Instant.now();

    // hashed PIN
    private String pinHash;
}

