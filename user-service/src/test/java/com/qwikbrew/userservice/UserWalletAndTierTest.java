package com.qwikbrew.userservice;

import com.qwikbrew.userservice.dto.*;
import com.qwikbrew.userservice.security.JwtService;
import com.qwikbrew.userservice.model.User;
import com.qwikbrew.userservice.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.qwikbrew.userservice.service.UserService;

/**
 * Additional wallet and loyalty tier tests for UserService.
 */
@ExtendWith(MockitoExtension.class)
class UserWalletAndTierTest {

    @Mock UserRepository  userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService      jwtService;
    @InjectMocks UserService userService;

    // ── Loyalty Tiers ─────────────────────────────────────────────────────────

    @Test @DisplayName("Bronze tier — user with < 500 BrewPoints")
    void tier_under500Points_isBronze() {
        User u = buildUser("u1", 400, new BigDecimal("200"));
        when(userRepository.addBrewPoints("u1", 0)).thenReturn(1);
        when(userRepository.findById("u1")).thenReturn(Optional.of(u));
        PointsResponse resp = userService.earnPoints("u1", 0);
        assertThat(resp.getTier()).isEqualTo("BRONZE");
    }

    @Test @DisplayName("Silver tier — user with >= 500 BrewPoints")
    void tier_500To1999Points_isSilver() {
        User u = buildUser("u2", 750, new BigDecimal("200"));
        when(userRepository.addBrewPoints("u2", 0)).thenReturn(1);
        when(userRepository.findById("u2")).thenReturn(Optional.of(u));
        PointsResponse resp = userService.earnPoints("u2", 0);
        assertThat(resp.getTier()).isEqualTo("SILVER");
    }

    @Test @DisplayName("Gold tier — user with >= 2000 BrewPoints")
    void tier_2000To4999Points_isGold() {
        User u = buildUser("u3", 3500, new BigDecimal("200"));
        when(userRepository.addBrewPoints("u3", 0)).thenReturn(1);
        when(userRepository.findById("u3")).thenReturn(Optional.of(u));
        PointsResponse resp = userService.earnPoints("u3", 0);
        assertThat(resp.getTier()).isEqualTo("GOLD");
    }

    @Test @DisplayName("Platinum tier — user with >= 5000 BrewPoints")
    void tier_over5000Points_isPlatinum() {
        User u = buildUser("u4", 6000, new BigDecimal("200"));
        when(userRepository.addBrewPoints("u4", 0)).thenReturn(1);
        when(userRepository.findById("u4")).thenReturn(Optional.of(u));
        PointsResponse resp = userService.earnPoints("u4", 0);
        assertThat(resp.getTier()).isEqualTo("PLATINUM");
    }

    // ── Wallet top-up ─────────────────────────────────────────────────────────

    @Test @DisplayName("topUp — credits correct amount to wallet")
    void topUp_creditsCorrectAmount() {
        User u = buildUser("u5", 0, new BigDecimal("200"));
        when(userRepository.creditWallet("u5", new BigDecimal("500"))).thenReturn(1);
        when(userRepository.findById("u5")).thenReturn(Optional.of(u));
        u.setWalletBalance(new BigDecimal("700")); // simulated post-update

        WalletResponse resp = userService.topUp("u5", new BigDecimal("500"));

        assertThat(resp.getUserId()).isEqualTo("u5");
        assertThat(resp.getBalance()).isEqualByComparingTo("700");
    }

    // ── Points redemption ─────────────────────────────────────────────────────

    @Test @DisplayName("redeemPoints — sufficient points redeemed successfully")
    void redeemPoints_sufficientPoints_success() {
        User u = buildUser("u6", 300, new BigDecimal("0"));
        when(userRepository.findById("u6")).thenReturn(Optional.of(u));
        when(userRepository.save(any())).thenReturn(u);

        PointsResponse resp = userService.redeemPoints("u6", 100);

        assertThat(resp.getPoints()).isEqualTo(200);
    }

    @Test @DisplayName("redeemPoints — insufficient points throws IllegalStateException")
    void redeemPoints_insufficientPoints_throws() {
        User u = buildUser("u7", 50, new BigDecimal("0"));
        when(userRepository.findById("u7")).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> userService.redeemPoints("u7", 100))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Insufficient brew points");
    }

    // ── Profile update ────────────────────────────────────────────────────────

    @Test @DisplayName("updateProfile — updates only provided fields")
    void updateProfile_partialUpdate_updatesOnlyProvidedFields() {
        User u = buildUser("u8", 0, BigDecimal.ZERO);
        u.setDepartment("Engineering");
        when(userRepository.findById("u8")).thenReturn(Optional.of(u));
        when(userRepository.save(any())).thenReturn(u);

        UpdateProfileRequest req = new UpdateProfileRequest(null, "9999999999", "Product", null);
        UserResponse resp = userService.updateProfile("u8", req);

        verify(userRepository).save(argThat(saved ->
            saved.getPhone().equals("9999999999") &&
            saved.getDepartment().equals("Product")));
    }

    // ── helpers ───────────────────────────────────────────────────────────────
    private User buildUser(String id, int points, BigDecimal balance) {
        return User.builder()
            .id(id).name("Test User").email(id + "@corp.in")
            .passwordHash("hashed").phone("9000000000")
            .role(User.Role.EMPLOYEE)
            .brewPoints(points)
            .walletBalance(balance)
            .active(true)
            .build();
    }
}
