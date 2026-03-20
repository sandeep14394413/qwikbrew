package com.qwikbrew.userservice.dto;

import com.qwikbrew.userservice.model.User;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class RegisterRequest {
    @NotBlank  private String name;
    @Email @NotBlank private String email;
    @Size(min=8) @NotBlank private String password;
    @NotBlank  private String phone;
    private String employeeId;
    private String department;
    private String floor;
}

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class LoginRequest {
    @Email @NotBlank private String email;
    @NotBlank private String password;
}

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class UpdateProfileRequest {
    private String name;
    private String phone;
    private String department;
    private String floor;
}

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class TokenRefreshRequest { private String refreshToken; }

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class TopUpRequest { private BigDecimal amount; private String paymentMethod; }

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class DeductRequest { private BigDecimal amount; private String reference; }

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserResponse {
    private String id, name, email, phone, employeeId, department, floor, role, loyaltyTier;
    private BigDecimal walletBalance;
    private Integer brewPoints;

    public static UserResponse from(User u) {
        return UserResponse.builder()
            .id(u.getId()).name(u.getName()).email(u.getEmail())
            .phone(u.getPhone()).employeeId(u.getEmployeeId())
            .department(u.getDepartment()).floor(u.getFloor())
            .walletBalance(u.getWalletBalance()).brewPoints(u.getBrewPoints())
            .role(u.getRole().name()).loyaltyTier(tier(u.getBrewPoints()))
            .build();
    }

    private static String tier(int pts) {
        if (pts >= 5000) return "PLATINUM";
        if (pts >= 2000) return "GOLD";
        if (pts >= 500)  return "SILVER";
        return "BRONZE";
    }
}

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private UserResponse user;
}

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class TokenResponse { private String accessToken; private String refreshToken; }

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class WalletResponse { private String userId; private BigDecimal balance; }

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class PointsResponse { private Integer points; private String tier; }
