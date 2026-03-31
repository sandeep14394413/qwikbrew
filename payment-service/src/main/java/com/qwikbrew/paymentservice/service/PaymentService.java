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
        WalletTransaction tx = WalletTransaction.builder()
            .userId(req.getUserId())
            .amount(req.getAmount())
            .type(WalletTransaction.TxnType.DEBIT)
            .status(WalletTransaction.TxnStatus.SUCCESS)
            .payMethod(req.getPaymentMethod())
            .reference(req.getReference())
            .gatewayTxnId(mockGwId)
            .build();
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
}
