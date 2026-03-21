package com.qwikbrew.orderservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders", indexes = {
    @Index(name = "idx_orders_user",   columnList = "userId"),
    @Index(name = "idx_orders_number", columnList = "orderNumber", unique = true),
    @Index(name = "idx_orders_status", columnList = "status")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true)
    private String orderNumber;

    @Column(nullable = false) private String userId;
    @Column(nullable = false) private String cafeId;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal subtotal;
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal gstAmount;
    @Column(nullable = false, precision = 10, scale = 2) @Builder.Default private BigDecimal discountAmount = BigDecimal.ZERO;
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Builder.Default private OrderStatus status = OrderStatus.PENDING;

    @Enumerated(EnumType.STRING) private PaymentMethod paymentMethod;

    private String  paymentReference;
    private String  specialInstructions;
    @Builder.Default private Integer estimatedMinutes = 12;

    @CreationTimestamp private LocalDateTime createdAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime readyAt;
    private LocalDateTime pickedUpAt;

    private Integer brewPointsEarned;
    private Integer brewPointsRedeemed;

    public enum OrderStatus  { PENDING, CONFIRMED, PREPARING, READY, PICKED_UP, CANCELLED }
    public enum PaymentMethod { WALLET, UPI, CARD, NET_BANKING, BREW_POINTS }
}
