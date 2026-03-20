package com.qwikbrew.userservice.security;

import com.qwikbrew.userservice.model.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
@Slf4j
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-expiry-minutes:60}")
    private long accessExpiryMinutes;

    @Value("${jwt.refresh-expiry-days:30}")
    private long refreshExpiryDays;

    public String generateAccessToken(User user) {
        return Jwts.builder()
            .subject(user.getId())
            .claim("email", user.getEmail())
            .claim("role",  user.getRole().name())
            .claim("name",  user.getName())
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + accessExpiryMinutes * 60_000))
            .signWith(getKey())
            .compact();
    }

    public String generateRefreshToken(User user) {
        return Jwts.builder()
            .subject(user.getId())
            .claim("type", "refresh")
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + refreshExpiryDays * 86_400_000L))
            .signWith(getKey())
            .compact();
    }

    public String extractUserId(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean isValid(String token) {
        try { parseClaims(token); return true; }
        catch (JwtException e) { return false; }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
            .verifyWith(getKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
