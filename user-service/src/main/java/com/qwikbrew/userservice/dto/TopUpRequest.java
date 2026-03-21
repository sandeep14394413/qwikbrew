package com.qwikbrew.userservice.dto;

import lombok.*;
import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class TopUpRequest {
    private BigDecimal amount;
    private String     paymentMethod;
}
