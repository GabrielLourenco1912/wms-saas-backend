package dev.lourencogabriel.wmssaas.auth.service;

import dev.lourencogabriel.wmssaas.auth.repository.AuthChallengeRepository;
import dev.lourencogabriel.wmssaas.auth.repository.RefreshTokenRepository;
import dev.lourencogabriel.wmssaas.shared.model.UserAccountStatus;
import dev.lourencogabriel.wmssaas.user.model.User;
import dev.lourencogabriel.wmssaas.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountSecurityServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-08T14:00:00Z");
    private static final String USER_ID = "01JTESTUSER0000000000000000";

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private AuthChallengeRepository authChallengeRepository;

    private AccountSecurityService service;

    @BeforeEach
    void setUp() {
        this.service = new AccountSecurityService(
                this.userRepository,
                this.refreshTokenRepository,
                this.authChallengeRepository,
                Clock.fixed(NOW, ZoneOffset.UTC),
                3,
                Duration.ofMinutes(15)
        );
    }

    @Test
    void shouldTemporarilyLockAndInvalidateSessionsAtFailureLimit() {
        User user = activeUser();
        user.setTokenVersion(4L);
        when(this.userRepository.findByEmailForUpdate("test@example.com"))
                .thenReturn(Optional.of(user));

        assertFalse(this.service.recordFailedAuthentication("test@example.com"));
        assertFalse(this.service.recordFailedAuthentication("test@example.com"));
        assertTrue(this.service.recordFailedAuthentication("test@example.com"));

        assertEquals(UserAccountStatus.LOCKED, user.getStatus());
        assertEquals(UserAccountStatus.ACTIVE, user.getStatusBeforeLock());
        assertEquals(NOW.plus(Duration.ofMinutes(15)), user.getLockedUntil());
        assertEquals(3, user.getFailedAuthenticationAttempts());
        assertEquals(5L, user.getTokenVersion());
        verify(this.refreshTokenRepository).revokeAllActiveByUserId(USER_ID, NOW);
        verify(this.authChallengeRepository).invalidateActiveForUser(USER_ID, NOW);
    }

    @Test
    void shouldRestorePreviousStatusAfterTemporaryLockExpires() {
        User user = activeUser();
        user.setStatus(UserAccountStatus.LOCKED);
        user.setStatusBeforeLock(UserAccountStatus.ACTIVE);
        user.setLockedUntil(NOW);
        user.setFailedAuthenticationAttempts(3);
        when(this.userRepository.findAllByStatusAndLockedUntilLessThanEqual(
                UserAccountStatus.LOCKED,
                NOW
        )).thenReturn(List.of(user));

        assertEquals(1, this.service.unlockExpiredAccounts());

        assertEquals(UserAccountStatus.ACTIVE, user.getStatus());
        assertEquals(0, user.getFailedAuthenticationAttempts());
        assertNull(user.getStatusBeforeLock());
        assertNull(user.getLockedUntil());
        verify(this.refreshTokenRepository, never())
                .revokeAllActiveByUserId(USER_ID, NOW);
    }

    @Test
    void shouldKeepAdministrativeLockIndefiniteAndInvalidateExistingSessions() {
        User user = activeUser();

        this.service.applyAdministrativeUpdate(user, UserAccountStatus.LOCKED, false);

        assertEquals(UserAccountStatus.LOCKED, user.getStatus());
        assertNull(user.getLockedUntil());
        assertNull(user.getStatusBeforeLock());
        assertEquals(1L, user.getTokenVersion());
        verify(this.refreshTokenRepository).revokeAllActiveByUserId(USER_ID, NOW);
        verify(this.authChallengeRepository).invalidateActiveForUser(USER_ID, NOW);
    }

    @Test
    void shouldMakeInvitedStatusConsistentWithEmailVerificationFlow() {
        User user = activeUser();
        user.setEmailVerifiedAt(NOW.minusSeconds(60));

        this.service.applyAdministrativeUpdate(user, UserAccountStatus.INVITED, false);

        assertEquals(UserAccountStatus.INVITED, user.getStatus());
        assertNull(user.getEmailVerifiedAt());
        assertEquals(1L, user.getTokenVersion());
    }

    @Test
    void shouldInvalidateSessionsAfterCredentialChange() {
        User user = activeUser();

        this.service.applyAdministrativeUpdate(user, UserAccountStatus.ACTIVE, true);

        assertEquals(1L, user.getTokenVersion());
        verify(this.refreshTokenRepository).revokeAllActiveByUserId(USER_ID, NOW);
        verify(this.authChallengeRepository).invalidateActiveForUser(USER_ID, NOW);
    }

    private User activeUser() {
        User user = new User("Test User", "test@example.com", "hash");
        user.setUlid(USER_ID);
        user.setStatus(UserAccountStatus.ACTIVE);
        user.setEmailVerifiedAt(NOW.minusSeconds(60));
        return user;
    }
}
