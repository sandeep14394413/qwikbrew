package com.qwikbrew.userservice.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuthResponse {
    private String       accessToken;
    private String       refreshToken;
    private UserResponse user;
}
