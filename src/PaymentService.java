package com.qwikbrew.paymentservice;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// ── Application ───────────────────────────────────────────────────────────────
@SpringBootApplication
@EnableDiscoveryClient
class PaymentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}

// ── Transaction Model ─────────────────────────────────────────────────────────
@Entity @Table(name = "wallet_transactions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false) private String userId;
    @Column(nullable = false) private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false) private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false) private TransactionStatus status;

    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    private String reference;      // orderId or topup reference
    private String gatewayTxnId;   // external gateway transaction id
    private String failureReason;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public enum TransactionType   { CREDIT, DEBIT, REFUND }
    public enum TransactionStatus { PENDING, SUCCESS, FAILED }
    public enum PaymentMethod     { WALLET, UPI, CARD, NET_BANKING }
}

// ── Repository ────────────────────────────────────────────────────────────────
interface WalletTransactionRepository extends JpaRepository<WalletTransaction, String> {
    List<WalletTransaction> findByUserIdOrderByCreatedAtDesc(String userId);
}

// ── Wallet Balance Model ──────────────────────────────────────────────────────
@Entity @Table(name = "wallet_balances")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class WalletBalance {
    @Id private String userId;
    @Column(nullable = false) @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;
    @Version private Long version; // optimistic locking
}

interface WalletBalanceRepository extends JpaRepository<WalletBalance, String> {}

// ── Payment Controller ────────────────────────────────────────────────────────
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/charge")
    public ResponseEntity<ChargeResponse> charge(@RequestBody ChargeRequest req) {
        return ResponseEntity.ok(paymentService.charge(req));
    }

    @PostMapping("/refund")
    public ResponseEntity<RefundResponse> refund(@RequestBody RefundRequest req) {
        return ResponseEntity.ok(paymentService.refund(req));
    }

    @PostMapping("/wallet/topup")
    public ResponseEntity<WalletResponse> topUp(@RequestBody TopUpRequest req) {
        return ResponseEntity.ok(paymentService.topUp(req));
    }

    @GetMapping("/wallet/{userId}")
    public ResponseEntity<WalletResponse> getBalance(@PathVariable String userId) {
        return ResponseEntity.ok(paymentService.getBalance(userId));
    }

    @GetMapping("/wallet/{userId}/transactions")
    public ResponseEntity<List<WalletTransaction>> getTransactions(@PathVariable String userId) {
        return ResponseEntity.ok(paymentService.getTransactions(userId));
    }
}

// ── Payment Service ───────────────────────────────────────────────────────────
@Service @RequiredArgsConstructor @Slf4j @Transactional
class PaymentService {

    private final WalletBalanceRepository  balanceRepo;
    private final WalletTransactionRepository txRepo;

    public ChargeResponse charge(ChargeRequest req) {
        if (req.getPaymentMethod() == WalletTransaction.PaymentMethod.WALLET) {
            return chargeWallet(req);
        }
        // For UPI / Card — integrate Pine Labs / Razorpay gateway here
        return chargeGateway(req);
    }

    private ChargeResponse chargeWallet(ChargeRequest req) {
        WalletBalance wb = balanceRepo.findById(req.getUserId())
                .orElseThrow(() -> new IllegalStateException("Wallet not found for user: " + req.getUserId()));

        if (wb.getBalance().compareTo(req.getAmount()) < 0) {
            throw new IllegalStateException("Insufficient wallet balance");
        }

        wb.setBalance(wb.getBalance().subtract(req.getAmount()));
        balanceRepo.save(wb);

        WalletTransaction tx = WalletTransaction.builder()
                .userId(req.getUserId())
                .amount(req.getAmount())
                .type(WalletTransaction.TransactionType.DEBIT)
                .status(WalletTransaction.TransactionStatus.SUCCESS)
                .paymentMethod(WalletTransaction.PaymentMethod.WALLET)
                .reference(req.getReference())
                .build();
        txRepo.save(tx);

        log.info("Wallet charge ₹{} for user {} — balance now ₹{}",
                req.getAmount(), req.getUserId(), wb.getBalance());
        return new ChargeResponse(tx.getId(), "SUCCESS", wb.getBalance());
    }

    private ChargeResponse chargeGateway(ChargeRequest req) {
        // TODO: integrate Razorpay / Pine Labs payment gateway
        String mockGatewayTxnId = "GW-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        WalletTransaction tx = WalletTransaction.builder()
                .userId(req.getUserId())
                .amount(req.getAmount())
                .type(WalletTransaction.TransactionType.DEBIT)
                .status(WalletTransaction.TransactionStatus.SUCCESS)
                .paymentMethod(req.getPaymentMethod())
                .reference(req.getReference())
                .gatewayTxnId(mockGatewayTxnId)
                .build();
        txRepo.save(tx);
        return new ChargeResponse(tx.getId(), "SUCCESS", null);
    }

    public RefundResponse refund(RefundRequest req) {
        WalletBalance wb = balanceRepo.findById(req.getUserId())
                .orElseGet(() -> WalletBalance.builder().userId(req.getUserId()).build());
        wb.setBalance(wb.getBalance().add(req.getAmount()));
        balanceRepo.save(wb);

        WalletTransaction tx = WalletTransaction.builder()
                .userId(req.getUserId())
                .amount(req.getAmount())
                .type(WalletTransaction.TransactionType.REFUND)
                .status(WalletTransaction.TransactionStatus.SUCCESS)
                .reference(req.getOrderId())
                .build();
        txRepo.save(tx);
        log.info("Refund ₹{} to user {} for order {}", req.getAmount(), req.getUserId(), req.getOrderId());
        return new RefundResponse(tx.getId(), wb.getBalance());
    }

    public WalletResponse topUp(TopUpRequest req) {
        WalletBalance wb = balanceRepo.findById(req.getUserId())
                .orElseGet(() -> WalletBalance.builder().userId(req.getUserId()).build());
        wb.setBalance(wb.getBalance().add(req.getAmount()));
        balanceRepo.save(wb);

        WalletTransaction tx = WalletTransaction.builder()
                .userId(req.getUserId())
                .amount(req.getAmount())
                .type(WalletTransaction.TransactionType.CREDIT)
                .status(WalletTransaction.TransactionStatus.SUCCESS)
                .paymentMethod(req.getPaymentMethod())
                .build();
        txRepo.save(tx);
        return new WalletResponse(wb.getUserId(), wb.getBalance());
    }

    @Transactional(readOnly = true)
    public WalletResponse getBalance(String userId) {
        WalletBalance wb = balanceRepo.findById(userId)
                .orElse(WalletBalance.builder().userId(userId).balance(BigDecimal.ZERO).build());
        return new WalletResponse(wb.getUserId(), wb.getBalance());
    }

    @Transactional(readOnly = true)
    public List<WalletTransaction> getTransactions(String userId) {
        return txRepo.findByUserIdOrderByCreatedAtDesc(userId);
    }
}

// ── DTOs (records) ────────────────────────────────────────────────────────────
record ChargeRequest(String userId, BigDecimal amount,
                     WalletTransaction.PaymentMethod paymentMethod, String reference) {}
record ChargeResponse(String transactionId, String status, BigDecimal newBalance) {}
record RefundRequest(String userId, BigDecimal amount, String orderId) {}
record RefundResponse(String transactionId, BigDecimal newBalance) {}
record TopUpRequest(String userId, BigDecimal amount,
                    WalletTransaction.PaymentMethod paymentMethod) {}
record WalletResponse(String userId, BigDecimal balance) {}
