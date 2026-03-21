package com.qwikbrew.userservice.dto;

import lombok.*;
import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class DeductRequest {
    private BigDecimal amount;
    private String     reference;
}
