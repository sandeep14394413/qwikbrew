package com.qwikbrew.notificationservice.service;

import com.qwikbrew.notificationservice.model.InAppNotification;
import com.qwikbrew.notificationservice.repository.InAppNotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class NotificationService {

    private final InAppNotificationRepository notifRepo;
    private final JavaMailSender              mailSender;

    // ── Send all channels ─────────────────────────────────────────────────────
    public void sendOrderConfirmed(String userId, String email, String orderNumber) {
        String title = "☕ Order Confirmed!";
        String body  = "Your order " + orderNumber + " has been confirmed and is being prepared.";
        sendAll(userId, email, title, body, orderNumber, "ORDER");
    }

    public void sendOrderReady(String userId, String email, String orderNumber, String counter) {
        String title = "🎉 Your Order is Ready!";
        String body  = "Order " + orderNumber + " is ready for pickup at Counter " + counter + ".";
        sendAll(userId, email, title, body, orderNumber, "ORDER");
    }

    public void sendOrderCancelled(String userId, String email, String orderNumber) {
        String title = "Order Cancelled";
        String body  = "Your order " + orderNumber + " has been cancelled. Refund initiated.";
        sendAll(userId, email, title, body, orderNumber, "ORDER");
    }

    public void sendWalletTopUp(String userId, String email, String amount, String newBalance) {
        String title = "💰 Wallet Recharged";
        String body  = "₹" + amount + " added to your Café Wallet. Balance: ₹" + newBalance + ".";
        sendEmail(email, title, body);
        saveInApp(userId, title, body, null, "PAYMENT");
    }

    public void sendPointsEarned(String userId, int points, String orderNumber) {
        String title = "🏆 Brew Points Earned";
        String body  = "You earned " + points + " BrewPoints on order " + orderNumber + "!";
        saveInApp(userId, title, body, orderNumber, "ORDER");
    }

    // ── In-app query ──────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<InAppNotification> getUserNotifications(String userId, boolean unreadOnly) {
        return unreadOnly
            ? notifRepo.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId)
            : notifRepo.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(String userId) {
        return notifRepo.countByUserIdAndIsReadFalse(userId);
    }

    public void markRead(String notificationId) {
        notifRepo.findById(notificationId).ifPresent(n -> {
            n.setIsRead(true);
            notifRepo.save(n);
        });
    }

    public void markAllRead(String userId) {
        notifRepo.markAllReadForUser(userId);
    }

    // ── Internals ─────────────────────────────────────────────────────────────
    private void sendAll(String userId, String email,
                         String title, String body,
                         String refId, String refType) {
        sendEmail(email, title, body);
        sendFcmPush(userId, title, body);
        saveInApp(userId, title, body, refId, refType);
    }

    private void sendEmail(String to, String subject, String body) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(to);
            msg.setSubject("[QwikBrew] " + subject);
            msg.setText(body + "\n\nTeam QwikBrew");
            mailSender.send(msg);
            log.info("Email sent to {}: {}", to, subject);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }

    private void sendFcmPush(String userId, String title, String body) {
        // TODO: integrate Google Firebase Cloud Messaging SDK
        log.info("[FCM] push → userId:{} | {} | {}", userId, title, body);
    }

    private void saveInApp(String userId, String title, String body,
                           String refId, String refType) {
        notifRepo.save(InAppNotification.builder()
            .userId(userId)
            .title(title)
            .body(body)
            .referenceId(refId)
            .referenceType(refType)
            .build());
    }
}
