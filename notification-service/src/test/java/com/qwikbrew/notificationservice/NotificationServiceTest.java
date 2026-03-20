package com.qwikbrew.notificationservice;

import com.qwikbrew.notificationservice.model.InAppNotification;
import com.qwikbrew.notificationservice.repository.InAppNotificationRepository;
import com.qwikbrew.notificationservice.service.NotificationService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock InAppNotificationRepository notifRepo;
    @Mock JavaMailSender               mailSender;
    @InjectMocks NotificationService   notificationService;

    private InAppNotification unreadNotif;
    private InAppNotification readNotif;

    @BeforeEach
    void setUp() {
        unreadNotif = InAppNotification.builder()
            .id("notif-001").userId("user-001")
            .title("☕ Order Confirmed!").body("Your order QBR-001 is confirmed.")
            .referenceId("order-001").referenceType("ORDER").isRead(false)
            .createdAt(LocalDateTime.now().minusMinutes(5))
            .build();

        readNotif = InAppNotification.builder()
            .id("notif-002").userId("user-001")
            .title("🎉 Order Ready!").body("Pick up at Counter 3")
            .referenceId("order-001").referenceType("ORDER").isRead(true)
            .createdAt(LocalDateTime.now().minusMinutes(2))
            .build();
    }

    // ── sendOrderConfirmed ────────────────────────────────────────────────────

    @Test @DisplayName("sendOrderConfirmed — sends email and saves in-app notification")
    void sendOrderConfirmed_sendsEmailAndSavesNotification() {
        doNothing().when(mailSender).send(any(SimpleMailMessage.class));
        when(notifRepo.save(any())).thenReturn(unreadNotif);

        notificationService.sendOrderConfirmed("user-001", "arjun@techcorp.in", "QBR-001");

        verify(mailSender).send(any(SimpleMailMessage.class));
        verify(notifRepo).save(argThat(n ->
            n.getTitle().contains("Confirmed") &&
            n.getBody().contains("QBR-001") &&
            n.getReferenceType().equals("ORDER") &&
            !n.getIsRead()));
    }

    @Test @DisplayName("sendOrderConfirmed — email failure does not throw (fire and forget)")
    void sendOrderConfirmed_emailFails_doesNotThrow() {
        doThrow(new RuntimeException("SMTP error")).when(mailSender).send(any(SimpleMailMessage.class));
        when(notifRepo.save(any())).thenReturn(unreadNotif);

        assertThatCode(() ->
            notificationService.sendOrderConfirmed("user-001", "bad-email", "QBR-001"))
            .doesNotThrowAnyException();
    }

    // ── sendOrderReady ────────────────────────────────────────────────────────

    @Test @DisplayName("sendOrderReady — includes counter info in notification body")
    void sendOrderReady_includesCounterInfo() {
        doNothing().when(mailSender).send(any(SimpleMailMessage.class));
        when(notifRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        notificationService.sendOrderReady("user-001", "arjun@techcorp.in", "QBR-001", "3");

        verify(notifRepo).save(argThat(n -> n.getBody().contains("Counter 3")));
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    // ── sendOrderCancelled ────────────────────────────────────────────────────

    @Test @DisplayName("sendOrderCancelled — sends email and saves notification with refund mention")
    void sendOrderCancelled_sendsEmailAndNotification() {
        doNothing().when(mailSender).send(any(SimpleMailMessage.class));
        when(notifRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        notificationService.sendOrderCancelled("user-001", "arjun@techcorp.in", "QBR-001");

        verify(notifRepo).save(argThat(n ->
            n.getTitle().contains("Cancelled") && n.getBody().contains("Refund")));
    }

    // ── sendWalletTopUp ───────────────────────────────────────────────────────

    @Test @DisplayName("sendWalletTopUp — sends email with amount and new balance")
    void sendWalletTopUp_sendsEmailWithDetails() {
        doNothing().when(mailSender).send(any(SimpleMailMessage.class));
        when(notifRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        notificationService.sendWalletTopUp("user-001", "arjun@techcorp.in", "500", "1500");

        verify(mailSender).send(argThat(msg -> {
            SimpleMailMessage m = (SimpleMailMessage) msg;
            return m.getText() != null && m.getText().contains("500") && m.getText().contains("1500");
        }));
        verify(notifRepo).save(argThat(n -> n.getReferenceType().equals("PAYMENT")));
    }

    // ── sendPointsEarned ──────────────────────────────────────────────────────

    @Test @DisplayName("sendPointsEarned — saves in-app notification only (no email)")
    void sendPointsEarned_savesInAppNotificationOnly() {
        when(notifRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        notificationService.sendPointsEarned("user-001", 13, "QBR-001");

        verify(notifRepo).save(argThat(n ->
            n.getTitle().contains("BrewPoints") && n.getBody().contains("13")));
        verifyNoInteractions(mailSender);
    }

    // ── getUserNotifications ──────────────────────────────────────────────────

    @Test @DisplayName("getUserNotifications — returns all notifications when unreadOnly=false")
    void getUserNotifications_allNotifications_returnsBoth() {
        when(notifRepo.findByUserIdOrderByCreatedAtDesc("user-001"))
            .thenReturn(List.of(readNotif, unreadNotif));

        List<InAppNotification> result =
            notificationService.getUserNotifications("user-001", false);

        assertThat(result).hasSize(2);
    }

    @Test @DisplayName("getUserNotifications — returns only unread when unreadOnly=true")
    void getUserNotifications_unreadOnly_returnsOnlyUnread() {
        when(notifRepo.findByUserIdAndIsReadFalseOrderByCreatedAtDesc("user-001"))
            .thenReturn(List.of(unreadNotif));

        List<InAppNotification> result =
            notificationService.getUserNotifications("user-001", true);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getIsRead()).isFalse();
    }

    @Test @DisplayName("getUserNotifications — returns empty list for user with no notifications")
    void getUserNotifications_noNotifications_returnsEmpty() {
        when(notifRepo.findByUserIdOrderByCreatedAtDesc("new-user"))
            .thenReturn(Collections.emptyList());
        assertThat(notificationService.getUserNotifications("new-user", false)).isEmpty();
    }

    // ── getUnreadCount ────────────────────────────────────────────────────────

    @Test @DisplayName("getUnreadCount — returns correct unread count")
    void getUnreadCount_returnsCount() {
        when(notifRepo.countByUserIdAndIsReadFalse("user-001")).thenReturn(3L);
        assertThat(notificationService.getUnreadCount("user-001")).isEqualTo(3L);
    }

    @Test @DisplayName("getUnreadCount — returns 0 when all notifications are read")
    void getUnreadCount_allRead_returnsZero() {
        when(notifRepo.countByUserIdAndIsReadFalse("user-001")).thenReturn(0L);
        assertThat(notificationService.getUnreadCount("user-001")).isEqualTo(0L);
    }

    // ── markRead ─────────────────────────────────────────────────────────────

    @Test @DisplayName("markRead — sets isRead true for existing notification")
    void markRead_existingNotification_setsReadTrue() {
        when(notifRepo.findById("notif-001")).thenReturn(Optional.of(unreadNotif));
        when(notifRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        notificationService.markRead("notif-001");

        verify(notifRepo).save(argThat(n -> n.getIsRead()));
    }

    @Test @DisplayName("markRead — silently ignores unknown notification id")
    void markRead_unknownId_doesNothing() {
        when(notifRepo.findById("bad-id")).thenReturn(Optional.empty());

        assertThatCode(() -> notificationService.markRead("bad-id"))
            .doesNotThrowAnyException();
        verify(notifRepo, never()).save(any());
    }

    // ── markAllRead ───────────────────────────────────────────────────────────

    @Test @DisplayName("markAllRead — calls repo to mark all unread as read")
    void markAllRead_callsRepoMethod() {
        when(notifRepo.markAllReadForUser("user-001")).thenReturn(3);

        notificationService.markAllRead("user-001");

        verify(notifRepo).markAllReadForUser("user-001");
    }

    @Test @DisplayName("markAllRead — returns 0 when no unread notifications exist")
    void markAllRead_noneUnread_returnsZero() {
        when(notifRepo.markAllReadForUser("user-001")).thenReturn(0);
        notificationService.markAllRead("user-001");
        verify(notifRepo).markAllReadForUser("user-001");
    }
}
