package com.qwikbrew.notificationservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "in_app_notifications", indexes = {
    @Index(name = "idx_notif_user",  columnList = "userId"),
    @Index(name = "idx_notif_read",  columnList = "userId,isRead")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InAppNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false) private String  userId;
    @Column(nullable = false) private String  title;

    @Column(columnDefinition = "TEXT")
    private String body;

    private String  referenceId;     // orderId, transactionId, etc.
    private String  referenceType;   // ORDER, PAYMENT, OFFER

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private Boolean isRead = false;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
