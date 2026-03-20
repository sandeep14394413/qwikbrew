package com.qwikbrew.userservice.service;

import com.qwikbrew.userservice.dto.*;
import com.qwikbrew.userservice.exception.DuplicateEmailException;
import com.qwikbrew.userservice.exception.UserNotFoundException;
import com.qwikbrew.userservice.model.User;
import com.qwikbrew.userservice.repository.UserRepository;
import com.qwikbrew.userservice.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.getEmail()))
            throw new DuplicateEmailException("Email already registered: " + req.getEmail());

        User user = User.builder()
            .name(req.getName())
            .email(req.getEmail())
            .passwordHash(passwordEncoder.encode(req.getPassword()))
            .phone(req.getPhone())
            .employeeId(req.getEmployeeId())
            .department(req.getDepartment())
            .floor(req.getFloor())
            .build();

        user = userRepository.save(user);
        log.info("Registered new user: {} ({})", user.getName(), user.getEmail());
        return buildAuth(user);
    }

    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByEmail(req.getEmail())
            .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash()))
            throw new BadCredentialsException("Invalid credentials");
        log.info("Login: {}", user.getEmail());
        return buildAuth(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getProfile(String userId) {
        return UserResponse.from(find(userId));
    }

    public UserResponse updateProfile(String userId, UpdateProfileRequest req) {
        User user = find(userId);
        if (req.getName()       != null) user.setName(req.getName());
        if (req.getPhone()      != null) user.setPhone(req.getPhone());
        if (req.getDepartment() != null) user.setDepartment(req.getDepartment());
        if (req.getFloor()      != null) user.setFloor(req.getFloor());
        return UserResponse.from(userRepository.save(user));
    }

    public WalletResponse topUp(String userId, BigDecimal amount) {
        userRepository.creditWallet(userId, amount);
        User user = find(userId);
        return new WalletResponse(userId, user.getWalletBalance());
    }

    public WalletResponse deduct(String userId, BigDecimal amount) {
        int updated = userRepository.debitWallet(userId, amount);
        if (updated == 0) throw new IllegalStateException("Insufficient wallet balance");
        User user = find(userId);
        return new WalletResponse(userId, user.getWalletBalance());
    }

    public PointsResponse earnPoints(String userId, int points) {
        userRepository.addBrewPoints(userId, points);
        User user = find(userId);
        return new PointsResponse(user.getBrewPoints(), tier(user.getBrewPoints()));
    }

    public PointsResponse redeemPoints(String userId, int points) {
        User user = find(userId);
        if (user.getBrewPoints() < points)
            throw new IllegalStateException("Insufficient brew points");
        user.setBrewPoints(user.getBrewPoints() - points);
        userRepository.save(user);
        return new PointsResponse(user.getBrewPoints(), tier(user.getBrewPoints()));
    }

    public TokenResponse refreshToken(String refreshToken) {
        String userId = jwtService.extractUserId(refreshToken);
        User user = find(userId);
        return new TokenResponse(jwtService.generateAccessToken(user), refreshToken);
    }

    // ── helpers ───────────────────────────────────────────────────────────────
    private User find(String id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + id));
    }

    private AuthResponse buildAuth(User user) {
        return AuthResponse.builder()
            .accessToken(jwtService.generateAccessToken(user))
            .refreshToken(jwtService.generateRefreshToken(user))
            .user(UserResponse.from(user))
            .build();
    }

    private static String tier(int pts) {
        if (pts >= 5000) return "PLATINUM";
        if (pts >= 2000) return "GOLD";
        if (pts >= 500)  return "SILVER";
        return "BRONZE";
    }
}
