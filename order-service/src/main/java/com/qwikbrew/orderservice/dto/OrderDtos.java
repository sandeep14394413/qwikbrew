package com.qwikbrew.orderservice.dto;

import com.qwikbrew.orderservice.model.Order;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// ── Request ───────────────────────────────────────────────────────────────────
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PlaceOrderRequest {
    @NotBlank  private String userId;
               private String cafeId;
    @NotEmpty @Valid
               private List<OrderItemRequest> items;
    @NotNull   private Order.PaymentMethod paymentMethod;
               private String paymentReference;
               private String specialInstructions;
               private Integer brewPointsToRedeem;
}

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OrderItemRequest {
    @NotBlank  private String     menuItemId;
    @NotBlank  private String     itemName;
    @NotNull @DecimalMin("0.01")
               private BigDecimal unitPrice;
    @Min(1)    private Integer    quantity;
               private List<String> selectedAddOns;
               private String    customization;
}

// ── Response ──────────────────────────────────────────────────────────────────
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OrderResponse {
    private String id;
    private String orderNumber;
    private String userId;
    private String status;
    private BigDecimal subtotal;
    private BigDecimal gstAmount;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;
    private String  paymentMethod;
    private Integer estimatedMinutes;
    private Integer brewPointsEarned;
    private LocalDateTime createdAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime readyAt;
    private LocalDateTime pickedUpAt;

    public static OrderResponse from(Order o) {
        return OrderResponse.builder()
            .id(o.getId())
            .orderNumber(o.getOrderNumber())
            .userId(o.getUserId())
            .status(o.getStatus().name())
            .subtotal(o.getSubtotal())
            .gstAmount(o.getGstAmount())
            .discountAmount(o.getDiscountAmount())
            .totalAmount(o.getTotalAmount())
            .paymentMethod(o.getPaymentMethod() != null ? o.getPaymentMethod().name() : null)
            .estimatedMinutes(o.getEstimatedMinutes())
            .brewPointsEarned(o.getBrewPointsEarned())
            .createdAt(o.getCreatedAt())
            .acceptedAt(o.getAcceptedAt())
            .readyAt(o.getReadyAt())
            .pickedUpAt(o.getPickedUpAt())
            .build();
    }
}
