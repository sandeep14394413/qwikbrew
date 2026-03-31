package com.qwikbrew.paymentservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpiCollectResponse {
    private String collectRequestId;
    private String status;
    private String message;
    private String reference;
}
