package com.qwikbrew.gateway.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping("/orders")
    public ResponseEntity<Map<String, Object>> ordersFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
            "timestamp", LocalDateTime.now().toString(),
            "status",    503,
            "error",     "Service Unavailable",
            "message",   "Order service is temporarily unavailable. Please try again in a moment.",
            "service",   "order-service"
        ));
    }

    @GetMapping("/payments")
    public ResponseEntity<Map<String, Object>> paymentsFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
            "timestamp", LocalDateTime.now().toString(),
            "status",    503,
            "error",     "Service Unavailable",
            "message",   "Payment service is temporarily unavailable.",
            "service",   "payment-service"
        ));
    }
}
