package com.qwikbrew.paymentservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "wallet_balances")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WalletBalance {
    @Id
    private String userId;

    @Column(nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    @Version
    private Long version;   // optimistic locking — prevents double-spend
}
