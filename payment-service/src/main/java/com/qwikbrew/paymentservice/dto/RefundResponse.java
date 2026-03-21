package com.qwikbrew.paymentservice.dto;

import lombok.*;
import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class RefundResponse {
    private String     transactionId;
    private BigDecimal newBalance;
}
