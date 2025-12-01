package com.ussd.wallet.ultimate.domain;

import lombok.*;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Table("transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {
    @PrimaryKey
    private UUID id = UUID.randomUUID();
    private Long accountId;
    private String type;
    private BigDecimal amount;
    private String currency;
    private Instant createdAt = Instant.now();
    private String status;
    private String meta;
}

