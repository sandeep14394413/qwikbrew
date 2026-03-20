package com.qwikbrew.paymentservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "wallet_transactions", indexes = {
    @Index(name = "idx_txn_user",      columnList = "userId"),
    @Index(name = "idx_txn_reference", columnList = "reference")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WalletTransaction {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false) private String     userId;
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false) private TxnType    type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false) private TxnStatus  status;

    @Enumerated(EnumType.STRING)
    private PayMethod payMethod;

    private String reference;
    private String gatewayTxnId;
    private String failureReason;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public enum TxnType    { CREDIT, DEBIT, REFUND }
    public enum TxnStatus  { PENDING, SUCCESS, FAILED }
    public enum PayMethod  { WALLET, UPI, CARD, NET_BANKING }
}
