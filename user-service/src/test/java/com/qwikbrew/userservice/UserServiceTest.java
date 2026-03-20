package com.qwikbrew.userservice;

import com.qwikbrew.userservice.dto.*;
import com.qwikbrew.userservice.exception.DuplicateEmailException;
import com.qwikbrew.userservice.model.User;
import com.qwikbrew.userservice.repository.UserRepository;
import com.qwikbrew.userservice.security.JwtService;
import com.qwikbrew.userservice.service.UserService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository  userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService      jwtService;
    @InjectMocks UserService userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
            .id("user-001")
            .name("Arjun Kumar")
            .email("arjun@techcorp.in")
            .passwordHash("$2a$12$hashedpassword")
            .phone("9876543210")
            .role(User.Role.EMPLOYEE)
            .walletBalance(new BigDecimal("500.00"))
            .brewPoints(100)
            .active(true)
            .build();
    }

    @Test
    @DisplayName("Register — creates new user and returns auth response")
    void register_newUser_returnsAuthResponse() {
        RegisterRequest req = new RegisterRequest(
            "Arjun Kumar", "arjun@techcorp.in", "Password123",
            "9876543210", "EMP001", "Engineering", "4");

        when(userRepository.existsByEmail(req.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(req.getPassword())).thenReturn("$2a$12$hashed");
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(jwtService.generateAccessToken(any())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(any())).thenReturn("refresh-token");

        AuthResponse response = userService.register(req);

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getUser().getEmail()).isEqualTo("arjun@techcorp.in");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Register — throws DuplicateEmailException for existing email")
    void register_existingEmail_throwsDuplicateEmailException() {
        RegisterRequest req = new RegisterRequest(
            "Arjun Kumar", "arjun@techcorp.in", "Password123",
            "9876543210", null, null, null);

        when(userRepository.existsByEmail(req.getEmail())).thenReturn(true);

        assertThatThrownBy(() -> userService.register(req))
            .isInstanceOf(DuplicateEmailException.class)
            .hasMessageContaining("arjun@techcorp.in");
    }

    @Test
    @DisplayName("Login — correct credentials returns auth response")
    void login_validCredentials_returnsAuthResponse() {
        LoginRequest req = new LoginRequest("arjun@techcorp.in", "Password123");

        when(userRepository.findByEmail(req.getEmail())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(req.getPassword(), testUser.getPasswordHash())).thenReturn(true);
        when(jwtService.generateAccessToken(any())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(any())).thenReturn("refresh-token");

        AuthResponse response = userService.login(req);

        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getUser().getId()).isEqualTo("user-001");
    }

    @Test
    @DisplayName("Login — wrong password throws BadCredentialsException")
    void login_wrongPassword_throwsBadCredentials() {
        LoginRequest req = new LoginRequest("arjun@techcorp.in", "WrongPassword");

        when(userRepository.findByEmail(req.getEmail())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> userService.login(req))
            .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("Earn points — increments user brew points")
    void earnPoints_incrementsPoints() {
        when(userRepository.addBrewPoints("user-001", 50)).thenReturn(1);
        when(userRepository.findById("user-001")).thenReturn(Optional.of(testUser));
        testUser.setBrewPoints(150);

        PointsResponse response = userService.earnPoints("user-001", 50);

        assertThat(response.getPoints()).isEqualTo(150);
        assertThat(response.getTier()).isEqualTo("BRONZE");
    }

    @Test
    @DisplayName("Deduct wallet — insufficient balance throws exception")
    void deductWallet_insufficientBalance_throws() {
        when(userRepository.debitWallet("user-001", new BigDecimal("1000")))
            .thenReturn(0); // 0 rows updated = insufficient balance

        assertThatThrownBy(() ->
            userService.deduct("user-001", new BigDecimal("1000")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Insufficient wallet balance");
    }
}
