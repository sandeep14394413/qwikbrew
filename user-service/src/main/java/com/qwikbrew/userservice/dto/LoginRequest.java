package com.qwikbrew.userservice.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class LoginRequest {
    @Email    private String email;
    @NotBlank private String password;
}
