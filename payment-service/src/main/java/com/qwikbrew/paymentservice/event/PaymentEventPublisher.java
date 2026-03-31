package com.qwikbrew.paymentservice.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qwikbrew.paymentservice.model.WalletTransaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishCharged(WalletTransaction tx, BigDecimal walletBalance) {
        Map<String, Object> payload = basePayload(tx, "PAYMENT_CHARGED");
        if (walletBalance != null) payload.put("walletBalance", walletBalance);
        publish("payment-charged", tx.getId(), payload);
    }

    public void publishRefunded(WalletTransaction tx, BigDecimal walletBalance) {
        Map<String, Object> payload = basePayload(tx, "PAYMENT_REFUNDED");
        payload.put("walletBalance", walletBalance);
        publish("payment-refunded", tx.getId(), payload);
    }

    public void publishWalletTopup(WalletTransaction tx, BigDecimal walletBalance) {
        Map<String, Object> payload = basePayload(tx, "WALLET_TOPUP");
        payload.put("walletBalance", walletBalance);
        publish("wallet-topup", tx.getId(), payload);
    }

    public void publishUpiCollectRequested(WalletTransaction tx, String upiId, String maskedUpiId) {
        Map<String, Object> payload = basePayload(tx, "UPI_COLLECT_REQUESTED");
        payload.put("upiId", upiId);
        payload.put("maskedUpiId", maskedUpiId);
        publish("upi-collect-requested", tx.getId(), payload);
    }

    private Map<String, Object> basePayload(WalletTransaction tx, String eventType) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType", eventType);
        payload.put("transactionId", tx.getId());
        payload.put("userId", tx.getUserId());
        payload.put("amount", tx.getAmount());
        payload.put("paymentMethod", tx.getPayMethod() != null ? tx.getPayMethod().name() : null);
        payload.put("status", tx.getStatus() != null ? tx.getStatus().name() : null);
        payload.put("reference", tx.getReference());
        payload.put("gatewayTxnId", tx.getGatewayTxnId());
        payload.put("timestamp", LocalDateTime.now().toString());
        return payload;
    }

    private void publish(String topic, String key, Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(topic, key, json);
            log.info("Published {} to topic={} key={}", payload.get("eventType"), topic, key);
        } catch (Exception e) {
            log.error("Failed to publish payment event key={} topic={} error={}", key, topic, e.getMessage());
        }
    }
}
