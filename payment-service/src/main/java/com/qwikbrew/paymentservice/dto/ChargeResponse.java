package com.qwikbrew.paymentservice.dto;

import lombok.*;
import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ChargeResponse {
    private String     transactionId;
    private String     status;
    private BigDecimal newBalance;
}
