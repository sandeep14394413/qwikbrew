package com.qwikbrew.paymentservice.dto;

import com.qwikbrew.paymentservice.model.WalletTransaction;
import lombok.*;
import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ChargeRequest {
    private String userId;
    private BigDecimal amount;
    private WalletTransaction.PayMethod paymentMethod;
    private String reference;
}

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ChargeResponse {
    private String transactionId;
    private String status;
    private BigDecimal newBalance;
}

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class RefundRequest {
    private String userId;
    private BigDecimal amount;
    private String orderId;
}

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class RefundResponse {
    private String transactionId;
    private BigDecimal newBalance;
}

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class TopUpRequest {
    private String userId;
    private BigDecimal amount;
    private WalletTransaction.PayMethod paymentMethod;
}

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class WalletResponse {
    private String userId;
    private BigDecimal balance;
}
