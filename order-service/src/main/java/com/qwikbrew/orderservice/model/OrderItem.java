package com.qwikbrew.orderservice.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.List;

@Entity
@Table(name = "order_items")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OrderItem {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false) private String     menuItemId;
    @Column(nullable = false) private String     itemName;
    @Column(nullable = false, precision = 8, scale = 2) private BigDecimal unitPrice;
    @Column(nullable = false) private Integer    quantity;
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal lineTotal;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "order_item_addons", joinColumns = @JoinColumn(name = "order_item_id"))
    @Column(name = "addon")
    private List<String> selectedAddOns;

    private String customization;
}
