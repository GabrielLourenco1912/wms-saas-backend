package dev.lourencogabriel.wmssaas.dto;

import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

public record AuthResponse(
    String issuer,
    Instant issuedAt,
    Instant expiresAt,
    String subject,
    String token

) {
    public AuthResponse(Jwt jwt) {
        this(
                jwt.getIssuer().toString(),
                jwt.getIssuedAt(),
                jwt.getExpiresAt(),
                jwt.getSubject(),
                jwt.getTokenValue()
        );
    }
}