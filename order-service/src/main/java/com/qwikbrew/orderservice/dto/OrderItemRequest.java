package com.qwikbrew.orderservice.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class OrderItemRequest {
    @NotBlank  private String     menuItemId;
    @NotBlank  private String     itemName;
    @NotNull @Positive private BigDecimal unitPrice;
    @Positive  private Integer    quantity;
               private List<String> selectedAddOns;
               private String    customization;
}
