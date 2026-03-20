package com.qwikbrew.userservice.controller;

import com.qwikbrew.userservice.dto.*;
import com.qwikbrew.userservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/health")
    public ResponseEntity<String> health() { return ResponseEntity.ok("UP"); }

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

    @PostMapping("/{userId}/wallet/topup")
    public ResponseEntity<WalletResponse> topUp(
            @PathVariable String userId,
            @RequestParam BigDecimal amount) {
        return ResponseEntity.ok(userService.topUp(userId, amount));
    }

    @PostMapping("/{userId}/wallet/deduct")
    public ResponseEntity<WalletResponse> deduct(
            @PathVariable String userId,
            @RequestBody DeductRequest req) {
        return ResponseEntity.ok(userService.deduct(userId, req.getAmount()));
    }

    @PostMapping("/{userId}/points/earn")
    public ResponseEntity<PointsResponse> earn(
            @PathVariable String userId,
            @RequestParam int points) {
        return ResponseEntity.ok(userService.earnPoints(userId, points));
    }

    @PostMapping("/{userId}/points/redeem")
    public ResponseEntity<PointsResponse> redeem(
            @PathVariable String userId,
            @RequestParam int points) {
        return ResponseEntity.ok(userService.redeemPoints(userId, points));
    }
}
