package com.qwikbrew.paymentservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpiCollectRequest {
    private String userId;
    private BigDecimal amount;
    private String upiId;
    private String reference;
}
