package com.qwikbrew.orderservice.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qwikbrew.orderservice.model.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishOrderPlaced(Order order) {
        publish("order-placed", order.getId(), buildPayload(order, "ORDER_PLACED"));
    }

    public void publishStatusChanged(Order order) {
        String topic = switch (order.getStatus()) {
            case READY     -> "order-ready";
            case CANCELLED -> "order-cancelled";
            default        -> "order-placed";
        };
        publish(topic, order.getId(), buildPayload(order, order.getStatus().name()));
    }

    public void publishOrderCancelled(Order order, String reason) {
        Map<String, Object> payload = buildPayload(order, "ORDER_CANCELLED");
        payload.put("reason", reason);
        publish("order-cancelled", order.getId(), payload);
    }

    private void publish(String topic, String key, Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(topic, key, json);
            log.info("Published {} event for order {}", payload.get("eventType"), key);
        } catch (Exception e) {
            log.error("Failed to publish event for order {}: {}", key, e.getMessage());
        }
    }

    private Map<String, Object> buildPayload(Order order, String eventType) {
        return new java.util.HashMap<>(Map.of(
            "eventType",   eventType,
            "orderId",     order.getId(),
            "orderNumber", order.getOrderNumber(),
            "userId",      order.getUserId(),
            "status",      order.getStatus().name(),
            "totalAmount", order.getTotalAmount().toString(),
            "timestamp",   LocalDateTime.now().toString()
        ));
    }
}
