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
public class TransactionStatusResponse {
    private String transactionId;
    private String reference;
    private String paymentMethod;
    private String status;
    private BigDecimal amount;
    private String message;
}
