package com.qwikbrew.orderservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// ── Order ────────────────────────────────────────────────────────────────────
@Entity
@Table(name = "orders")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true)
    private String orderNumber; // QBR-YYYYMMDD-XXXX

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String cafeId;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<OrderItem> items;

    @Column(nullable = false)
    private BigDecimal subtotal;

    @Column(nullable = false)
    private BigDecimal gstAmount;

    @Column(nullable = false)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    private String paymentReference;

    private String specialInstructions;

    @Column(nullable = false)
    @Builder.Default
    private Integer estimatedMinutes = 12;

    @CreationTimestamp
    private LocalDateTime createdAt;

    private LocalDateTime acceptedAt;
    private LocalDateTime readyAt;
    private LocalDateTime pickedUpAt;

    @Column
    private Integer brewPointsEarned;

    @Column
    private Integer brewPointsRedeemed;

    public enum OrderStatus {
        PENDING, CONFIRMED, PREPARING, READY, PICKED_UP, CANCELLED
    }

    public enum PaymentMethod {
        WALLET, UPI, CARD, NET_BANKING, BREW_POINTS
    }
}

// ── OrderItem ────────────────────────────────────────────────────────────────
@Entity
@Table(name = "order_items")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(nullable = false)
    private String menuItemId;

    @Column(nullable = false)
    private String itemName;

    @Column(nullable = false)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private BigDecimal lineTotal;

    @ElementCollection
    @CollectionTable(name = "order_item_addons", joinColumns = @JoinColumn(name = "order_item_id"))
    @Column(name = "addon")
    private List<String> selectedAddOns;

    private String customization;
}
