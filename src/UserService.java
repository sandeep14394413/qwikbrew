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

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new DuplicateEmailException("Email already registered: " + req.getEmail());
        }
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
        return buildAuthResponse(user);
    }

    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        log.info("User logged in: {}", user.getEmail());
        return buildAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getProfile(String userId) {
        User user = findOrThrow(userId);
        return UserResponse.from(user);
    }

    public UserResponse updateProfile(String userId, UpdateProfileRequest req) {
        User user = findOrThrow(userId);
        if (req.getName() != null) user.setName(req.getName());
        if (req.getPhone() != null) user.setPhone(req.getPhone());
        if (req.getDepartment() != null) user.setDepartment(req.getDepartment());
        if (req.getFloor() != null) user.setFloor(req.getFloor());
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public PointsResponse getPoints(String userId) {
        User user = findOrThrow(userId);
        return new PointsResponse(user.getBrewPoints(), calculateTier(user.getBrewPoints()));
    }

    public PointsResponse earnPoints(String userId, int points, String orderId) {
        User user = findOrThrow(userId);
        user.setBrewPoints(user.getBrewPoints() + points);
        userRepository.save(user);
        log.info("Earned {} brew points for user {} on order {}", points, userId, orderId);
        return new PointsResponse(user.getBrewPoints(), calculateTier(user.getBrewPoints()));
    }

    public PointsResponse redeemPoints(String userId, int points) {
        User user = findOrThrow(userId);
        if (user.getBrewPoints() < points) {
            throw new IllegalStateException("Insufficient brew points");
        }
        user.setBrewPoints(user.getBrewPoints() - points);
        userRepository.save(user);
        return new PointsResponse(user.getBrewPoints(), calculateTier(user.getBrewPoints()));
    }

    public TokenResponse refreshToken(String refreshToken) {
        String userId = jwtService.extractUserId(refreshToken);
        User user = findOrThrow(userId);
        return new TokenResponse(jwtService.generateAccessToken(user), refreshToken);
    }

    // ── Helpers ──────────────────────────────────────────────────────────
    private User findOrThrow(String id) {
        return userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    }

    private AuthResponse buildAuthResponse(User user) {
        String access  = jwtService.generateAccessToken(user);
        String refresh = jwtService.generateRefreshToken(user);
        return AuthResponse.builder()
                .accessToken(access)
                .refreshToken(refresh)
                .user(UserResponse.from(user))
                .build();
    }

    private String calculateTier(int points) {
        if (points >= 5000) return "PLATINUM";
        if (points >= 2000) return "GOLD";
        if (points >= 500)  return "SILVER";
        return "BRONZE";
    }
}
