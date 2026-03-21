package com.qwikbrew.userservice.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class RegisterRequest {
    @NotBlank  private String name;
    @Email     private String email;
    @Size(min=8) private String password;
    @NotBlank  private String phone;
               private String employeeId;
               private String department;
               private String floor;
}
