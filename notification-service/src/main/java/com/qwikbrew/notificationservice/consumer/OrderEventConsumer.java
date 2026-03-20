package com.qwikbrew.notificationservice.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qwikbrew.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final NotificationService notificationService;
    private final ObjectMapper        objectMapper;

    @KafkaListener(topics = "order-placed", groupId = "${spring.kafka.consumer.group-id}")
    public void onOrderPlaced(String message) {
        handle(message, event -> {
            String userId      = str(event, "userId");
            String email       = str(event, "userEmail");
            String orderNumber = str(event, "orderNumber");
            if (userId != null && orderNumber != null) {
                notificationService.sendOrderConfirmed(userId, email, orderNumber);
            }
        });
    }

    @KafkaListener(topics = "order-ready", groupId = "${spring.kafka.consumer.group-id}")
    public void onOrderReady(String message) {
        handle(message, event -> {
            String userId      = str(event, "userId");
            String email       = str(event, "userEmail");
            String orderNumber = str(event, "orderNumber");
            String counter     = event.getOrDefault("counterNumber", "3").toString();
            if (userId != null && orderNumber != null) {
                notificationService.sendOrderReady(userId, email, orderNumber, counter);
            }
        });
    }

    @KafkaListener(topics = "order-cancelled", groupId = "${spring.kafka.consumer.group-id}")
    public void onOrderCancelled(String message) {
        handle(message, event -> {
            String userId      = str(event, "userId");
            String email       = str(event, "userEmail");
            String orderNumber = str(event, "orderNumber");
            if (userId != null && orderNumber != null) {
                notificationService.sendOrderCancelled(userId, email, orderNumber);
            }
        });
    }

    @KafkaListener(topics = "wallet-topup", groupId = "${spring.kafka.consumer.group-id}")
    public void onWalletTopUp(String message) {
        handle(message, event -> {
            String userId     = str(event, "userId");
            String email      = str(event, "userEmail");
            String amount     = str(event, "amount");
            String newBalance = str(event, "newBalance");
            if (userId != null) {
                notificationService.sendWalletTopUp(userId, email, amount, newBalance);
            }
        });
    }

    @KafkaListener(topics = "points-earned", groupId = "${spring.kafka.consumer.group-id}")
    public void onPointsEarned(String message) {
        handle(message, event -> {
            String userId      = str(event, "userId");
            String orderNumber = str(event, "orderNumber");
            int    points      = event.containsKey("points")
                ? Integer.parseInt(event.get("points").toString()) : 0;
            if (userId != null && points > 0) {
                notificationService.sendPointsEarned(userId, points, orderNumber);
            }
        });
    }

    // ── helpers ───────────────────────────────────────────────────────────────
    private void handle(String message, EventHandler handler) {
        try {
            Map<String, Object> event = objectMapper.readValue(
                message, new TypeReference<>() {});
            handler.handle(event);
        } catch (Exception e) {
            log.error("Failed to process Kafka message: {} | error: {}", message, e.getMessage());
        }
    }

    private static String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    @FunctionalInterface
    interface EventHandler {
        void handle(Map<String, Object> event) throws Exception;
    }
}
