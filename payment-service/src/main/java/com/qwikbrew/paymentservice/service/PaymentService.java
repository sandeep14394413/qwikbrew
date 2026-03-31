package com.qwikbrew.paymentservice.service;

import com.qwikbrew.paymentservice.dto.*;
import com.qwikbrew.paymentservice.event.PaymentEventPublisher;
import com.qwikbrew.paymentservice.model.*;
import com.qwikbrew.paymentservice.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PaymentService {

    private final WalletBalanceRepository     balanceRepo;
    private final WalletTransactionRepository txRepo;
    private final PaymentEventPublisher       paymentEventPublisher;

    // ── Charge ────────────────────────────────────────────────────────────────
    public ChargeResponse charge(ChargeRequest req) {
        return req.getPaymentMethod() == WalletTransaction.PayMethod.WALLET
            ? chargeWallet(req)
            : chargeGateway(req);
    }

    public UpiCollectResponse initiateUpiCollect(UpiCollectRequest req) {
        String maskedUpi = maskUpi(req.getUpiId());
        String collectRef = req.getReference() != null && !req.getReference().isBlank()
            ? req.getReference()
            : "UPI-COLLECT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);

        WalletTransaction tx = WalletTransaction.builder()
            .userId(req.getUserId())
            .amount(req.getAmount())
            .type(WalletTransaction.TxnType.DEBIT)
            .status(WalletTransaction.TxnStatus.PENDING)
            .payMethod(WalletTransaction.PayMethod.UPI)
            .reference(collectRef)
            .gatewayTxnId("UPI-REQ-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase(Locale.ROOT))
            .build();
        txRepo.save(tx);
        paymentEventPublisher.publishUpiCollectRequested(tx, req.getUpiId(), maskedUpi);

        log.info("UPI collect initiated for user {} amount {} upi={} reference={}",
            req.getUserId(), req.getAmount(), maskedUpi, collectRef);
        return new UpiCollectResponse(
            tx.getId(),
            "COLLECT_REQUESTED",
            "Collect request sent to UPI app. Please approve to continue.",
            collectRef
        );
    }

    private ChargeResponse chargeWallet(ChargeRequest req) {
        WalletBalance wb = balanceRepo.findById(req.getUserId())
            .orElseThrow(() -> new IllegalStateException("Wallet not found for: " + req.getUserId()));

        if (wb.getBalance().compareTo(req.getAmount()) < 0)
            throw new IllegalStateException("Insufficient wallet balance");

        wb.setBalance(wb.getBalance().subtract(req.getAmount()));
        balanceRepo.save(wb);

        WalletTransaction tx = WalletTransaction.builder()
            .userId(req.getUserId())
            .amount(req.getAmount())
            .type(WalletTransaction.TxnType.DEBIT)
            .status(WalletTransaction.TxnStatus.SUCCESS)
            .payMethod(WalletTransaction.PayMethod.WALLET)
            .reference(req.getReference())
            .build();
        txRepo.save(tx);
        paymentEventPublisher.publishCharged(tx, wb.getBalance());

        log.info("Wallet charge ₹{} for user {} — balance now ₹{}",
            req.getAmount(), req.getUserId(), wb.getBalance());
        return new ChargeResponse(tx.getId(), "SUCCESS", wb.getBalance());
    }

    private ChargeResponse chargeGateway(ChargeRequest req) {
        // TODO: integrate Razorpay / Pine Labs gateway
        String mockGwId = "GW-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        WalletTransaction tx = txRepo.findTopByReferenceOrderByCreatedAtDesc(req.getReference())
            .filter(existing -> existing.getPayMethod() == req.getPaymentMethod()
                && existing.getStatus() == WalletTransaction.TxnStatus.PENDING)
            .map(existing -> {
                existing.setStatus(WalletTransaction.TxnStatus.SUCCESS);
                existing.setGatewayTxnId(mockGwId);
                existing.setFailureReason(null);
                return existing;
            })
            .orElseGet(() -> WalletTransaction.builder()
                .userId(req.getUserId())
                .amount(req.getAmount())
                .type(WalletTransaction.TxnType.DEBIT)
                .status(WalletTransaction.TxnStatus.SUCCESS)
                .payMethod(req.getPaymentMethod())
                .reference(req.getReference())
                .gatewayTxnId(mockGwId)
                .build());
        txRepo.save(tx);
        paymentEventPublisher.publishCharged(tx, null);
        return new ChargeResponse(tx.getId(), "SUCCESS", null);
    }

    // ── Refund ────────────────────────────────────────────────────────────────
    public RefundResponse refund(RefundRequest req) {
        WalletBalance wb = balanceRepo.findById(req.getUserId())
            .orElseGet(() -> WalletBalance.builder().userId(req.getUserId()).build());
        wb.setBalance(wb.getBalance().add(req.getAmount()));
        balanceRepo.save(wb);

        WalletTransaction tx = WalletTransaction.builder()
            .userId(req.getUserId())
            .amount(req.getAmount())
            .type(WalletTransaction.TxnType.REFUND)
            .status(WalletTransaction.TxnStatus.SUCCESS)
            .reference(req.getOrderId())
            .build();
        txRepo.save(tx);
        paymentEventPublisher.publishRefunded(tx, wb.getBalance());

        log.info("Refund ₹{} to user {} for order {}", req.getAmount(), req.getUserId(), req.getOrderId());
        return new RefundResponse(tx.getId(), wb.getBalance());
    }

    // ── Top-up ────────────────────────────────────────────────────────────────
    public WalletResponse topUp(TopUpRequest req) {
        WalletBalance wb = balanceRepo.findById(req.getUserId())
            .orElseGet(() -> WalletBalance.builder().userId(req.getUserId()).build());
        wb.setBalance(wb.getBalance().add(req.getAmount()));
        balanceRepo.save(wb);

        WalletTransaction tx = WalletTransaction.builder()
            .userId(req.getUserId())
            .amount(req.getAmount())
            .type(WalletTransaction.TxnType.CREDIT)
            .status(WalletTransaction.TxnStatus.SUCCESS)
            .payMethod(req.getPaymentMethod())
            .build();
        txRepo.save(tx);
        paymentEventPublisher.publishWalletTopup(tx, wb.getBalance());

        log.info("Top-up ₹{} for user {} — balance now ₹{}", req.getAmount(), req.getUserId(), wb.getBalance());
        return new WalletResponse(wb.getUserId(), wb.getBalance());
    }

    // ── Query ─────────────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public WalletResponse getBalance(String userId) {
        BigDecimal balance = balanceRepo.findById(userId)
            .map(WalletBalance::getBalance)
            .orElse(BigDecimal.ZERO);
        return new WalletResponse(userId, balance);
    }

    @Transactional(readOnly = true)
    public List<WalletTransaction> getTransactions(String userId) {
        return txRepo.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public TransactionStatusResponse getTransactionStatus(String txnId, String reference) {
        WalletTransaction tx = null;
        if (txnId != null && !txnId.isBlank()) {
            tx = txRepo.findById(txnId).orElse(null);
        }
        if (tx == null && reference != null && !reference.isBlank()) {
            tx = txRepo.findTopByReferenceOrderByCreatedAtDesc(reference).orElse(null);
        }
        if (tx == null) {
            return new TransactionStatusResponse(null, reference, null, "NOT_FOUND", null, "No transaction found");
        }
        return new TransactionStatusResponse(
            tx.getId(),
            tx.getReference(),
            tx.getPayMethod() != null ? tx.getPayMethod().name() : null,
            tx.getStatus().name(),
            tx.getAmount(),
            tx.getStatus() == WalletTransaction.TxnStatus.SUCCESS ? "Transaction completed" :
                tx.getStatus() == WalletTransaction.TxnStatus.PENDING ? "Transaction pending approval" :
                "Transaction failed"
        );
    }

    private static String maskUpi(String upiId) {
        if (upiId == null || !upiId.contains("@")) return "invalid";
        String[] parts = upiId.split("@", 2);
        String user = parts[0];
        if (user.isBlank()) return "***@" + parts[1];
        String maskedUser = user.length() <= 2 ? user.substring(0, 1) + "*" : user.substring(0, 2) + "***";
        return maskedUser + "@" + parts[1];
    }
}
