package com.qwikbrew.paymentservice.dto;

import com.qwikbrew.paymentservice.model.WalletTransaction;
import lombok.*;
import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ChargeRequest {
    private String                  userId;
    private BigDecimal              amount;
    private WalletTransaction.PayMethod paymentMethod;
    private String                  reference;
}
