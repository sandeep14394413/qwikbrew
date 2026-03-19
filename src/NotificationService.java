package com.qwikbrew.notificationservice;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

// ── Application ───────────────────────────────────────────────────────────────
@SpringBootApplication
@EnableDiscoveryClient
public class NotificationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}

// ── Notification Types ────────────────────────────────────────────────────────
@Getter @AllArgsConstructor
enum NotificationType {
    ORDER_CONFIRMED  ("☕ Order Confirmed!",        "Your order %s has been confirmed and is being prepared."),
    ORDER_READY      ("🎉 Your Order is Ready!",    "Order %s is ready for pickup at Counter %s."),
    ORDER_CANCELLED  ("Order Cancelled",             "Your order %s has been cancelled. Refund initiated."),
    WALLET_TOPUP     ("💰 Wallet Recharged",         "₹%s added to your Café Wallet. Balance: ₹%s."),
    POINTS_EARNED    ("🏆 Brew Points Earned",       "You earned %d BrewPoints on order %s!"),
    OFFER_ALERT      ("🌟 Today's Special Offer",    "%s");

    private final String title;
    private final String bodyTemplate;
}

// ── Event DTOs ────────────────────────────────────────────────────────────────
record OrderEvent(String orderId, String orderNumber, String userId,
                  String userEmail, String userPhone, String status,
                  String counterNumber, LocalDateTime timestamp) {}

record WalletEvent(String userId, String userEmail,
                   String amount, String newBalance) {}

// ── Notification Controller (for in-app notifications) ───────────────────────
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<InAppNotification>> getUserNotifications(
            @PathVariable String userId,
            @RequestParam(defaultValue = "false") boolean unreadOnly) {
        return ResponseEntity.ok(notificationService.getUserNotifications(userId, unreadOnly));
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<Void> markRead(@PathVariable String notificationId) {
        notificationService.markRead(notificationId);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/user/{userId}/read-all")
    public ResponseEntity<Void> markAllRead(@PathVariable String userId) {
        notificationService.markAllRead(userId);
        return ResponseEntity.ok().build();
    }
}

// ── Notification Service ──────────────────────────────────────────────────────
@Service @RequiredArgsConstructor @Slf4j
class NotificationService {

    private final JavaMailSender        mailSender;
    private final FcmPushService        fcmPushService;
    private final InAppNotificationRepo inAppRepo;

    // ── Kafka Consumers ──────────────────────────────────────────────────────
    @KafkaListener(topics = "order-placed", groupId = "notification-service")
    public void onOrderPlaced(OrderEvent event) {
        String msg = String.format(NotificationType.ORDER_CONFIRMED.getBodyTemplate(), event.orderNumber());
        sendAll(event.userId(), event.userEmail(), NotificationType.ORDER_CONFIRMED, msg, event.orderId());
    }

    @KafkaListener(topics = "order-ready", groupId = "notification-service")
    public void onOrderReady(OrderEvent event) {
        String counter = event.counterNumber() != null ? event.counterNumber() : "3";
        String msg = String.format(NotificationType.ORDER_READY.getBodyTemplate(),
                event.orderNumber(), counter);
        sendAll(event.userId(), event.userEmail(), NotificationType.ORDER_READY, msg, event.orderId());
    }

    @KafkaListener(topics = "order-cancelled", groupId = "notification-service")
    public void onOrderCancelled(OrderEvent event) {
        String msg = String.format(NotificationType.ORDER_CANCELLED.getBodyTemplate(), event.orderNumber());
        sendAll(event.userId(), event.userEmail(), NotificationType.ORDER_CANCELLED, msg, event.orderId());
    }

    @KafkaListener(topics = "wallet-topup", groupId = "notification-service")
    public void onWalletTopUp(WalletEvent event) {
        String msg = String.format(NotificationType.WALLET_TOPUP.getBodyTemplate(),
                event.amount(), event.newBalance());
        sendEmail(event.userEmail(), NotificationType.WALLET_TOPUP.getTitle(), msg);
        saveInApp(event.userId(), NotificationType.WALLET_TOPUP.getTitle(), msg, null);
    }

    // ── Internal helpers ─────────────────────────────────────────────────────
    private void sendAll(String userId, String email, NotificationType type, String body, String refId) {
        sendEmail(email, type.getTitle(), body);
        fcmPushService.send(userId, type.getTitle(), body);
        saveInApp(userId, type.getTitle(), body, refId);
    }

    private void sendEmail(String to, String subject, String body) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(to);
            msg.setSubject("[QwikBrew] " + subject);
            msg.setText(body + "\n\nTeam QwikBrew");
            mailSender.send(msg);
            log.info("Email sent to {}: {}", to, subject);
        } catch (Exception ex) {
            log.error("Failed to send email to {}: {}", to, ex.getMessage());
        }
    }

    private void saveInApp(String userId, String title, String body, String referenceId) {
        inAppRepo.save(InAppNotification.builder()
                .userId(userId).title(title).body(body)
                .referenceId(referenceId).build());
    }

    public List<InAppNotification> getUserNotifications(String userId, boolean unreadOnly) {
        return unreadOnly
                ? inAppRepo.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId)
                : inAppRepo.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public void markRead(String id) {
        inAppRepo.findById(id).ifPresent(n -> { n.setRead(true); inAppRepo.save(n); });
    }

    public void markAllRead(String userId) {
        inAppRepo.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId)
                .forEach(n -> { n.setRead(true); inAppRepo.save(n); });
    }
}

// ── In-App Notification Entity ────────────────────────────────────────────────
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

@Entity @Table(name = "in_app_notifications")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class InAppNotification {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    @Column(nullable = false) private String userId;
    @Column(nullable = false) private String title;
    @Column(columnDefinition = "TEXT") private String body;
    private String referenceId;
    @Builder.Default private Boolean read = false;
    @CreationTimestamp private LocalDateTime createdAt;
}

interface InAppNotificationRepo extends org.springframework.data.jpa.repository.JpaRepository<InAppNotification, String> {
    List<InAppNotification> findByUserIdOrderByCreatedAtDesc(String userId);
    List<InAppNotification> findByUserIdAndReadFalseOrderByCreatedAtDesc(String userId);
}

// ── FCM Push Service stub ─────────────────────────────────────────────────────
@Service @Slf4j
class FcmPushService {
    public void send(String userId, String title, String body) {
        // TODO: integrate Google Firebase Cloud Messaging
        log.info("[FCM] → user:{} | {} | {}", userId, title, body);
    }
}
