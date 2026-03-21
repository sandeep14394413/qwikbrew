package com.qwikbrew.paymentservice.dto;

import com.qwikbrew.paymentservice.model.WalletTransaction;
import lombok.*;
import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class TopUpRequest {
    private String                  userId;
    private BigDecimal              amount;
    private WalletTransaction.PayMethod paymentMethod;
}
