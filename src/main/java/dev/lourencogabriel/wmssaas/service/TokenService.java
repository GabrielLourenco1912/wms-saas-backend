package dev.lourencogabriel.wmssaas.service;

import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import dev.lourencogabriel.wmssaas.dto.AuthResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtEncoder jwtEncoder;
    private final SecretKey secretKey;

    public AuthResponse generateTokens(String subject) {
        Instant now = Instant.now();

        Instant expiration = now.plus(15, ChronoUnit.MINUTES);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("https://api.wmssaas.com")
                .issuedAt(now)
                .expiresAt(expiration)
                .subject(subject)
                .build();

        JwsHeader jwsHeader = JwsHeader.with(() -> "HS256").build();

        Jwt jwt = this.jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims));

        SecureRandom secureRandom = new SecureRandom();
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);

        String refreshTokenText = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        return new AuthResponse(jwt, refreshTokenText);
    }

    public String extractSubject(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);

            MACVerifier verifier = new MACVerifier(this.secretKey.getEncoded());

            if (!signedJWT.verify(verifier)) {
                throw new JwtException("Invalid token signature");
            }

            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            return claims.getSubject();

        } catch (Exception exception) {
            throw new JwtException("Invalid access token", exception);
        }
    }
}
