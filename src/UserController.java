package com.qwikbrew.userservice.controller;

import com.qwikbrew.userservice.dto.*;
import com.qwikbrew.userservice.service.UserService;
import com.qwikbrew.userservice.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final WalletService walletService;

    // ── Auth ──────────────────────────────────────────────
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.register(req));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(userService.login(req));
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<TokenResponse> refresh(@RequestBody TokenRefreshRequest req) {
        return ResponseEntity.ok(userService.refreshToken(req.getRefreshToken()));
    }

    // ── Profile ───────────────────────────────────────────
    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getProfile(@PathVariable String userId) {
        return ResponseEntity.ok(userService.getProfile(userId));
    }

    @PutMapping("/{userId}")
    public ResponseEntity<UserResponse> updateProfile(
            @PathVariable String userId,
            @Valid @RequestBody UpdateProfileRequest req) {
        return ResponseEntity.ok(userService.updateProfile(userId, req));
    }

    // ── Wallet ────────────────────────────────────────────
    @GetMapping("/{userId}/wallet")
    public ResponseEntity<WalletResponse> getWallet(@PathVariable String userId) {
        return ResponseEntity.ok(walletService.getBalance(userId));
    }

    @PostMapping("/{userId}/wallet/topup")
    public ResponseEntity<WalletResponse> topUp(
            @PathVariable String userId,
            @RequestBody TopUpRequest req) {
        return ResponseEntity.ok(walletService.topUp(userId, req.getAmount(), req.getPaymentMethod()));
    }

    @PostMapping("/{userId}/wallet/deduct")
    public ResponseEntity<WalletResponse> deductBalance(
            @PathVariable String userId,
            @RequestBody DeductRequest req) {
        return ResponseEntity.ok(walletService.deduct(userId, req.getAmount(), req.getReference()));
    }

    // ── Brew Points ───────────────────────────────────────
    @GetMapping("/{userId}/points")
    public ResponseEntity<PointsResponse> getPoints(@PathVariable String userId) {
        return ResponseEntity.ok(userService.getPoints(userId));
    }

    @PostMapping("/{userId}/points/earn")
    public ResponseEntity<PointsResponse> earnPoints(
            @PathVariable String userId,
            @RequestParam Integer points,
            @RequestParam String orderId) {
        return ResponseEntity.ok(userService.earnPoints(userId, points, orderId));
    }

    @PostMapping("/{userId}/points/redeem")
    public ResponseEntity<PointsResponse> redeemPoints(
            @PathVariable String userId,
            @RequestParam Integer points) {
        return ResponseEntity.ok(userService.redeemPoints(userId, points));
    }
}
