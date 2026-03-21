package com.qwikbrew.orderservice.dto;

import com.qwikbrew.orderservice.model.Order;
import jakarta.validation.constraints.*;
import lombok.*;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PlaceOrderRequest {
    @NotBlank  private String userId;
               private String cafeId;
    @NotEmpty  private List<OrderItemRequest> items;
    @NotNull   private Order.PaymentMethod paymentMethod;
               private String paymentReference;
               private String specialInstructions;
               private Integer brewPointsToRedeem;
}
