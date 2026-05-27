package dev.lourencogabriel.wmssaas.service;

import dev.lourencogabriel.wmssaas.dto.AuthResponse;
import dev.lourencogabriel.wmssaas.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtEncoder jwtEncoder;

    public AuthResponse generateToken(Authentication auth) {
        Instant now = Instant.now();

        Instant expiration = now.plus(15, ChronoUnit.MINUTES);

        User currentUser = (User) auth.getPrincipal();

        assert currentUser != null;
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("https://api.wmssaas.com")
                .issuedAt(now)
                .expiresAt(expiration)
                .subject(currentUser.getUlid())
                .build();

        JwsHeader jwsHeader = JwsHeader.with(() -> "HS256").build();

        Jwt jwt = jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims));

        return new AuthResponse(jwt);
    }
}
