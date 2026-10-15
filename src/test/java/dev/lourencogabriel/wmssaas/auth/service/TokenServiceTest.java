package dev.lourencogabriel.wmssaas.auth.service;

import dev.lourencogabriel.wmssaas.auth.dto.AuthResponse;
import dev.lourencogabriel.wmssaas.auth.model.RefreshToken;
import dev.lourencogabriel.wmssaas.auth.repository.RefreshTokenRepository;
import dev.lourencogabriel.wmssaas.user.model.User;
import dev.lourencogabriel.wmssaas.user.model.AccessProfile;
import dev.lourencogabriel.wmssaas.shared.model.UserAccountStatus;
import java.util.Set;
import dev.lourencogabriel.wmssaas.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    private static final String USER_ID = "01JTESTUSER0000000000000000";
    private static final String ORIGINAL_REFRESH_TOKEN = "original-refresh-token";
    private static final String ISSUER = "http://inat";

    @Mock
    private JwtEncoder jwtEncoder;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        this.tokenService = new TokenService(
                this.jwtEncoder,
                this.userRepository,
                this.refreshTokenRepository,
                ISSUER
        );
    }

    @Test
    void shouldGenerateAndPersistHashedRefreshToken() {
        User user = this.user();
        user.setTokenVersion(7L);
        this.prepareTokenGeneration(user);
        String longIpAddress = "1".repeat(50);

        AuthResponse response = this.tokenService.generateTokens(
                USER_ID,
                "  integration-test-agent  ",
                longIpAddress
        );

        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        ArgumentCaptor<JwtEncoderParameters> jwtCaptor =
                ArgumentCaptor.forClass(JwtEncoderParameters.class);
        verify(this.jwtEncoder).encode(jwtCaptor.capture());
        verify(this.refreshTokenRepository).save(tokenCaptor.capture());
        RefreshToken persistedToken = tokenCaptor.getValue();

        assertAll(
                () -> assertEquals("access-token", response.token()),
                () -> assertEquals(USER_ID, response.subject()),
                () -> assertNotNull(response.refreshToken()),
                () -> assertFalse(response.refreshToken().isBlank()),
                () -> assertNotEquals(response.refreshToken(), persistedToken.getTokenHash()),
                () -> assertEquals(this.hash(response.refreshToken()), persistedToken.getTokenHash()),
                () -> assertSame(user, persistedToken.getUser()),
                () -> assertEquals("integration-test-agent", persistedToken.getUserAgent()),
                () -> assertEquals(45, persistedToken.getIpAddress().length()),
                () -> assertEquals(
                        7L,
                        ((Number) jwtCaptor.getValue().getClaims().getClaim(
                                TokenService.TOKEN_VERSION_CLAIM
                        )).longValue()
                ),
                () -> assertTrue(persistedToken.getExpiresAt().isAfter(Instant.now().plus(29, ChronoUnit.DAYS)))
        );
    }

    @Test
    void shouldRotateAndRevokeCurrentRefreshTokenUsingLockedQuery() {
        User user = this.user();
        RefreshToken currentToken = this.refreshToken(user, Instant.now().plus(1, ChronoUnit.DAYS));
        when(this.refreshTokenRepository.findByTokenHashForUpdate(this.hash(ORIGINAL_REFRESH_TOKEN)))
                .thenReturn(Optional.of(currentToken));
        this.prepareTokenGeneration(user);

        AuthResponse response = this.tokenService.refreshTokens(
                ORIGINAL_REFRESH_TOKEN,
                "new-agent",
                "203.0.113.10"
        );

        assertAll(
                () -> assertNotNull(currentToken.getRevokedAt()),
                () -> assertEquals(this.hash(response.refreshToken()), currentToken.getReplacedByToken())
        );
        verify(this.refreshTokenRepository).findByTokenHashForUpdate(this.hash(ORIGINAL_REFRESH_TOKEN));
        verify(this.refreshTokenRepository, never()).findByTokenHash(anyString());
        verify(this.refreshTokenRepository, times(2)).save(any(RefreshToken.class));
    }

    @Test
    void shouldUseNonLockingQueryWhenOnlyExtractingSubject() {
        User user = this.user();
        RefreshToken currentToken = this.refreshToken(user, Instant.now().plus(1, ChronoUnit.DAYS));
        when(this.refreshTokenRepository.findByTokenHash(this.hash(ORIGINAL_REFRESH_TOKEN)))
                .thenReturn(Optional.of(currentToken));

        String subject = this.tokenService.extractSubject(ORIGINAL_REFRESH_TOKEN);

        assertEquals(USER_ID, subject);
        verify(this.refreshTokenRepository).findByTokenHash(this.hash(ORIGINAL_REFRESH_TOKEN));
        verify(this.refreshTokenRepository, never()).findByTokenHashForUpdate(anyString());
    }

    @Test
    void shouldRevokeRefreshTokenWithoutReplacementUsingLockedQuery() {
        RefreshToken currentToken = this.refreshToken(this.user(), Instant.now().plus(1, ChronoUnit.DAYS));
        when(this.refreshTokenRepository.findByTokenHashForUpdate(this.hash(ORIGINAL_REFRESH_TOKEN)))
                .thenReturn(Optional.of(currentToken));

        this.tokenService.revokeRefreshToken(ORIGINAL_REFRESH_TOKEN);

        assertAll(
                () -> assertNotNull(currentToken.getRevokedAt()),
                () -> assertNull(currentToken.getReplacedByToken())
        );
        verify(this.refreshTokenRepository).findByTokenHashForUpdate(this.hash(ORIGINAL_REFRESH_TOKEN));
        verify(this.refreshTokenRepository).save(currentToken);
    }

    @Test
    void shouldRejectBlankRefreshTokenBeforeAccessingDatabase() {
        JwtException exception = assertThrows(
                JwtException.class,
                () -> this.tokenService.refreshTokens(" ", null, null)
        );

        assertEquals("Refresh token is required", exception.getMessage());
        verifyNoInteractions(this.refreshTokenRepository);
    }

    @Test
    void shouldRejectUnknownRefreshToken() {
        when(this.refreshTokenRepository.findByTokenHashForUpdate(this.hash(ORIGINAL_REFRESH_TOKEN)))
                .thenReturn(Optional.empty());

        JwtException exception = assertThrows(
                JwtException.class,
                () -> this.tokenService.refreshTokens(ORIGINAL_REFRESH_TOKEN, null, null)
        );

        assertEquals("Refresh token is invalid", exception.getMessage());
        verifyNoInteractions(this.jwtEncoder);
    }

    @Test
    void shouldRejectRevokedRefreshTokenWithoutGeneratingReplacement() {
        RefreshToken currentToken = this.refreshToken(this.user(), Instant.now().plus(1, ChronoUnit.DAYS));
        currentToken.setRevokedAt(Instant.now());
        when(this.refreshTokenRepository.findByTokenHashForUpdate(this.hash(ORIGINAL_REFRESH_TOKEN)))
                .thenReturn(Optional.of(currentToken));

        JwtException exception = assertThrows(
                JwtException.class,
                () -> this.tokenService.refreshTokens(ORIGINAL_REFRESH_TOKEN, null, null)
        );

        assertEquals("Refresh token has been revoked", exception.getMessage());
        verifyNoInteractions(this.jwtEncoder);
        verify(this.refreshTokenRepository, never()).save(any());
    }

    @Test
    void shouldRejectExpiredRefreshTokenWithoutGeneratingReplacement() {
        RefreshToken currentToken = this.refreshToken(this.user(), Instant.now().minus(1, ChronoUnit.SECONDS));
        when(this.refreshTokenRepository.findByTokenHashForUpdate(this.hash(ORIGINAL_REFRESH_TOKEN)))
                .thenReturn(Optional.of(currentToken));

        JwtException exception = assertThrows(
                JwtException.class,
                () -> this.tokenService.refreshTokens(ORIGINAL_REFRESH_TOKEN, null, null)
        );

        assertEquals("Refresh token has expired", exception.getMessage());
        verifyNoInteractions(this.jwtEncoder);
        verify(this.refreshTokenRepository, never()).save(any());
    }

    @Test
    void shouldRejectTokenGenerationBeforeEmailVerification() {
        User user = this.user();
        user.setEmailVerifiedAt(null);
        when(this.userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        JwtException exception = assertThrows(
                JwtException.class,
                () -> this.tokenService.generateTokens(USER_ID)
        );

        assertEquals(
                "An active account, verified email and active profile are required",
                exception.getMessage()
        );
        verifyNoInteractions(this.jwtEncoder, this.refreshTokenRepository);
    }

    private void prepareTokenGeneration(User user) {
        when(this.userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(this.jwtEncoder.encode(any())).thenReturn(this.jwt());
        when(this.refreshTokenRepository.existsByTokenHash(anyString())).thenReturn(false);
    }

    private User user() {
        User user = new User("Test User", "test@example.com", "encoded-password");
        user.setUlid(USER_ID);
        user.setStatus(UserAccountStatus.ACTIVE);
        user.setProfile(new AccessProfile("OPERADOR", "Operador", Set.of()));
        user.setEmailVerifiedAt(Instant.now());
        return user;
    }

    private RefreshToken refreshToken(User user, Instant expiresAt) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(this.hash(ORIGINAL_REFRESH_TOKEN));
        refreshToken.setExpiresAt(expiresAt);
        return refreshToken;
    }

    private Jwt jwt() {
        Instant now = Instant.now();
        return Jwt.withTokenValue("access-token")
                .header("alg", "HS256")
                .issuer(ISSUER)
                .issuedAt(now)
                .expiresAt(now.plus(15, ChronoUnit.MINUTES))
                .subject(USER_ID)
                .build();
    }

    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
