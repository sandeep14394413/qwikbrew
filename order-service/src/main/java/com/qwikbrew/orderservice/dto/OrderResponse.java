package com.qwikbrew.orderservice.dto;

import com.qwikbrew.orderservice.model.Order;
import com.qwikbrew.orderservice.model.OrderItem;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OrderResponse {
    private String         id;
    private String         orderNumber;
    private String         userId;
    private String         status;
    private BigDecimal     subtotal;
    private BigDecimal     gstAmount;
    private BigDecimal     discountAmount;
    private BigDecimal     totalAmount;
    private String         paymentMethod;
    private Integer        estimatedMinutes;
    private Integer        brewPointsEarned;
    private List<OrderItemView> items;
    private LocalDateTime  createdAt;
    private LocalDateTime  acceptedAt;
    private LocalDateTime  readyAt;
    private LocalDateTime  pickedUpAt;

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
            .items(o.getItems().stream().map(OrderItemView::from).toList())
            .createdAt(o.getCreatedAt())
            .acceptedAt(o.getAcceptedAt())
            .readyAt(o.getReadyAt())
            .pickedUpAt(o.getPickedUpAt())
            .build();
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class OrderItemView {
        private String id;
        private String menuItemId;
        private String itemName;
        private BigDecimal unitPrice;
        private Integer quantity;
        private BigDecimal lineTotal;
        private List<String> selectedAddOns;
        private String customization;

        public static OrderItemView from(OrderItem item) {
            return OrderItemView.builder()
                .id(item.getId())
                .menuItemId(item.getMenuItemId())
                .itemName(item.getItemName())
                .unitPrice(item.getUnitPrice())
                .quantity(item.getQuantity())
                .lineTotal(item.getLineTotal())
                .selectedAddOns(item.getSelectedAddOns())
                .customization(item.getCustomization())
                .build();
        }
    }
}
