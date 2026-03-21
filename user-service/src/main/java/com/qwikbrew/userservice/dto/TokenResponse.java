package com.qwikbrew.userservice.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class TokenResponse {
    private String accessToken;
    private String refreshToken;
}
