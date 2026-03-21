package com.qwikbrew.userservice.dto;

import com.qwikbrew.userservice.model.User;
import lombok.*;
import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserResponse {
    private String     id;
    private String     name;
    private String     email;
    private String     phone;
    private String     employeeId;
    private String     department;
    private String     floor;
    private BigDecimal walletBalance;
    private Integer    brewPoints;
    private String     role;
    private String     loyaltyTier;

    public static UserResponse from(User u) {
        return UserResponse.builder()
            .id(u.getId())
            .name(u.getName())
            .email(u.getEmail())
            .phone(u.getPhone())
            .employeeId(u.getEmployeeId())
            .department(u.getDepartment())
            .floor(u.getFloor())
            .walletBalance(u.getWalletBalance())
            .brewPoints(u.getBrewPoints())
            .role(u.getRole().name())
            .loyaltyTier(tier(u.getBrewPoints()))
            .build();
    }

    private static String tier(int pts) {
        if (pts >= 5000) return "PLATINUM";
        if (pts >= 2000) return "GOLD";
        if (pts >= 500)  return "SILVER";
        return "BRONZE";
    }
}
