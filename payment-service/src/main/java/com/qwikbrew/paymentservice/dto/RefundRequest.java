package com.qwikbrew.paymentservice.dto;

import lombok.*;
import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class RefundRequest {
    private String     userId;
    private BigDecimal amount;
    private String     orderId;
}
