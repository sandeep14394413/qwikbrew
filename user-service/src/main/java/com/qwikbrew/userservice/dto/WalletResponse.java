package com.qwikbrew.userservice.dto;

import lombok.*;
import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class WalletResponse {
    private String     userId;
    private BigDecimal balance;
}
