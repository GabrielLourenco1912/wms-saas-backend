package dev.lourencogabriel.wmssaas.auth.service;

import dev.lourencogabriel.wmssaas.auth.dto.AuthResponse;
import dev.lourencogabriel.wmssaas.auth.model.RefreshToken;
import dev.lourencogabriel.wmssaas.auth.repository.RefreshTokenRepository;
import dev.lourencogabriel.wmssaas.user.model.User;
import dev.lourencogabriel.wmssaas.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

@Service
public class TokenService {

    public static final String TOKEN_VERSION_CLAIM = "token_version";

    private static final int ACCESS_TOKEN_MINUTES = 15;
    private static final int REFRESH_TOKEN_DAYS = 30;
    private static final int REFRESH_TOKEN_BYTES = 32;
    private static final int MAX_USER_AGENT_LENGTH = 255;
    private static final int MAX_IP_ADDRESS_LENGTH = 45;

    private final JwtEncoder jwtEncoder;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final String issuer;
    private final SecureRandom secureRandom = new SecureRandom();

    public TokenService(
            JwtEncoder jwtEncoder,
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            @Value("${api.security.jwt.issuer}") String issuer
    ) {
        this.jwtEncoder = jwtEncoder;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.issuer = issuer;
    }

    @Transactional
    public AuthResponse generateTokens(String subject) {
        return this.generateTokens(subject, null, null);
    }

    @Transactional
    public AuthResponse generateTokens(String subject, String userAgent, String ipAddress) {
        if (!StringUtils.hasText(subject)) {
            throw new JwtException("Token subject is required");
        }

        User user = this.userRepository.findById(subject)
                .orElseThrow(() -> new JwtException("Token subject is invalid"));
        if (!user.hasActiveAccess()) {
            throw new JwtException("An active account, verified email and active profile are required");
        }

        Instant now = Instant.now();

        Instant expiration = now.plus(ACCESS_TOKEN_MINUTES, ChronoUnit.MINUTES);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(this.issuer)
                .issuedAt(now)
                .expiresAt(expiration)
                .subject(subject)
                .claim(TOKEN_VERSION_CLAIM, valueOrZero(user.getTokenVersion()))
                .build();

        JwsHeader jwsHeader = JwsHeader.with(() -> "HS256").build();

        Jwt jwt = this.jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims));

        RefreshTokenValue refreshTokenValue = this.generateUniqueRefreshToken();
        Instant refreshTokenExpiresAt = now.plus(REFRESH_TOKEN_DAYS, ChronoUnit.DAYS);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(refreshTokenValue.hash());
        refreshToken.setUserAgent(this.truncate(userAgent, MAX_USER_AGENT_LENGTH));
        refreshToken.setIpAddress(this.truncate(ipAddress, MAX_IP_ADDRESS_LENGTH));
        refreshToken.setExpiresAt(refreshTokenExpiresAt);

        this.refreshTokenRepository.save(refreshToken);

        return new AuthResponse(jwt, refreshTokenValue.token(), refreshTokenExpiresAt);
    }

    @Transactional
    public AuthResponse refreshTokens(String refreshToken, String userAgent, String ipAddress) {
        RefreshToken currentRefreshToken = this.validateRefreshTokenForUpdate(refreshToken);
        AuthResponse authResponse = this.generateTokens(currentRefreshToken.getUser().getUlid(), userAgent, ipAddress);

        this.revokeRefreshToken(currentRefreshToken, authResponse.refreshToken());

        return authResponse;
    }

    @Transactional
    public void revokeRefreshToken(String refreshToken) {
        RefreshToken currentRefreshToken = this.validateRefreshTokenForUpdate(refreshToken);
        this.revokeRefreshToken(currentRefreshToken, null);
    }

    @Transactional(readOnly = true)
    public String extractSubject(String token) {
        return this.validateRefreshToken(token).getUser().getUlid();
    }

    private RefreshToken validateRefreshToken(String token) {
        if (!StringUtils.hasText(token)) {
            throw new JwtException("Refresh token is required");
        }

        String tokenHash = this.hashRefreshToken(token);
        RefreshToken refreshToken = this.refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new JwtException("Refresh token is invalid"));

        if (refreshToken.getRevokedAt() != null) {
            throw new JwtException("Refresh token has been revoked");
        }

        if (!refreshToken.getExpiresAt().isAfter(Instant.now())) {
            throw new JwtException("Refresh token has expired");
        }

        if (refreshToken.getUser() == null || !StringUtils.hasText(refreshToken.getUser().getUlid())) {
            throw new JwtException("Refresh token user is invalid");
        }

        return refreshToken;
    }

    private RefreshToken validateRefreshTokenForUpdate(String token) {
        if (!StringUtils.hasText(token)) {
            throw new JwtException("Refresh token is required");
        }

        String tokenHash = this.hashRefreshToken(token);
        RefreshToken refreshToken = this.refreshTokenRepository.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new JwtException("Refresh token is invalid"));

        if (refreshToken.getRevokedAt() != null) {
            throw new JwtException("Refresh token has been revoked");
        }

        if (!refreshToken.getExpiresAt().isAfter(Instant.now())) {
            throw new JwtException("Refresh token has expired");
        }

        if (refreshToken.getUser() == null || !StringUtils.hasText(refreshToken.getUser().getUlid())) {
            throw new JwtException("Refresh token user is invalid");
        }

        return refreshToken;
    }

    private void revokeRefreshToken(RefreshToken refreshToken, String replacedByRefreshToken) {
        refreshToken.setRevokedAt(Instant.now());
        refreshToken.setReplacedByToken(
                StringUtils.hasText(replacedByRefreshToken)
                        ? this.hashRefreshToken(replacedByRefreshToken)
                        : null
        );

        this.refreshTokenRepository.save(refreshToken);
    }

    private RefreshTokenValue generateUniqueRefreshToken() {
        RefreshTokenValue refreshTokenValue;

        do {
            refreshTokenValue = this.generateRefreshTokenValue();
        } while (this.refreshTokenRepository.existsByTokenHash(refreshTokenValue.hash()));

        return refreshTokenValue;
    }

    private RefreshTokenValue generateRefreshTokenValue() {
        byte[] randomBytes = new byte[REFRESH_TOKEN_BYTES];
        this.secureRandom.nextBytes(randomBytes);

        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        return new RefreshTokenValue(token, this.hashRefreshToken(token));
    }

    private String hashRefreshToken(String token) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(token.getBytes(StandardCharsets.UTF_8));

            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available", exception);
        }
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String normalizedValue = value.trim();

        if (normalizedValue.length() <= maxLength) {
            return normalizedValue;
        }

        return normalizedValue.substring(0, maxLength);
    }

    private static long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }

    private record RefreshTokenValue(String token, String hash) {
    }
}
