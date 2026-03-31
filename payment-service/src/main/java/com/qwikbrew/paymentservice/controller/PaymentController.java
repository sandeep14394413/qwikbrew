package com.qwikbrew.paymentservice.controller;

import com.qwikbrew.paymentservice.dto.*;
import com.qwikbrew.paymentservice.model.WalletTransaction;
import com.qwikbrew.paymentservice.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping("/health")
    public ResponseEntity<String> health() { return ResponseEntity.ok("UP"); }

    @PostMapping("/charge")
    public ResponseEntity<ChargeResponse> charge(@RequestBody ChargeRequest req) {
        return ResponseEntity.ok(paymentService.charge(req));
    }

    @PostMapping("/upi/collect")
    public ResponseEntity<UpiCollectResponse> upiCollect(@RequestBody UpiCollectRequest req) {
        return ResponseEntity.ok(paymentService.initiateUpiCollect(req));
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
    public ResponseEntity<WalletResponse> balance(@PathVariable String userId) {
        return ResponseEntity.ok(paymentService.getBalance(userId));
    }

    @GetMapping("/wallet/{userId}/transactions")
    public ResponseEntity<List<WalletTransaction>> transactions(@PathVariable String userId) {
        return ResponseEntity.ok(paymentService.getTransactions(userId));
    }

    @GetMapping("/transactions/status")
    public ResponseEntity<TransactionStatusResponse> transactionStatus(
        @RequestParam(required = false) String transactionId,
        @RequestParam(required = false) String reference
    ) {
        return ResponseEntity.ok(paymentService.getTransactionStatus(transactionId, reference));
    }
}
