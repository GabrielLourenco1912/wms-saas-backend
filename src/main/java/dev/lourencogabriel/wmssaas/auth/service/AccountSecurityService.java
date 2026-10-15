package dev.lourencogabriel.wmssaas.auth.service;

import dev.lourencogabriel.wmssaas.auth.repository.AuthChallengeRepository;
import dev.lourencogabriel.wmssaas.auth.repository.RefreshTokenRepository;
import dev.lourencogabriel.wmssaas.shared.model.UserAccountStatus;
import dev.lourencogabriel.wmssaas.user.model.User;
import dev.lourencogabriel.wmssaas.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

@Service
public class AccountSecurityService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthChallengeRepository authChallengeRepository;
    private final Clock clock;
    private final int maxFailedAttempts;
    private final Duration lockDuration;

    public AccountSecurityService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            AuthChallengeRepository authChallengeRepository,
            Clock clock,
            @Value("${api.security.account-lock.max-failed-attempts:5}") int maxFailedAttempts,
            @Value("${api.security.account-lock.duration:15m}") Duration lockDuration
    ) {
        if (maxFailedAttempts < 1) {
            throw new IllegalArgumentException("Account lock max failed attempts must be positive");
        }
        if (lockDuration.isZero() || lockDuration.isNegative()) {
            throw new IllegalArgumentException("Account lock duration must be positive");
        }
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.authChallengeRepository = authChallengeRepository;
        this.clock = clock;
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockDuration = lockDuration;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void unlockExpiredByEmail(String email) {
        this.userRepository.findByEmailForUpdate(email)
                .ifPresent(user -> this.unlockIfExpired(user, Instant.now(this.clock)));
    }

    @Transactional
    public int unlockExpiredAccounts() {
        Instant now = Instant.now(this.clock);
        int unlocked = 0;
        for (User user : this.userRepository.findAllByStatusAndLockedUntilLessThanEqual(
                UserAccountStatus.LOCKED,
                now
        )) {
            if (this.unlockIfExpired(user, now)) {
                unlocked++;
            }
        }
        return unlocked;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordFailedAuthentication(String email) {
        return this.userRepository.findByEmailForUpdate(email)
                .map(this::recordFailedAuthentication)
                .orElse(false);
    }

    public boolean recordFailedAuthentication(User user) {
        Instant now = Instant.now(this.clock);
        this.unlockIfExpired(user, now);
        if (user.getStatus() != UserAccountStatus.ACTIVE
                && user.getStatus() != UserAccountStatus.INVITED) {
            return false;
        }

        int failedAttempts = valueOrZero(user.getFailedAuthenticationAttempts()) + 1;
        user.setFailedAuthenticationAttempts(failedAttempts);
        if (failedAttempts < this.maxFailedAttempts) {
            return false;
        }

        user.setStatusBeforeLock(user.getStatus());
        user.setStatus(UserAccountStatus.LOCKED);
        user.setLockedUntil(now.plus(this.lockDuration));
        this.invalidateAuthenticationArtifacts(user, now);
        return true;
    }

    public void recordSuccessfulAuthentication(User user) {
        user.setFailedAuthenticationAttempts(0);
    }

    public void applyAdministrativeUpdate(
            User user,
            UserAccountStatus requestedStatus,
            boolean credentialsChanged
    ) {
        Objects.requireNonNull(requestedStatus, "requestedStatus");
        UserAccountStatus previousStatus = user.getStatus();
        boolean statusChanged = previousStatus != requestedStatus;

        if (statusChanged) {
            user.setStatus(requestedStatus);
            user.setFailedAuthenticationAttempts(0);
            if (requestedStatus == UserAccountStatus.LOCKED) {
                user.setLockedUntil(null);
                user.setStatusBeforeLock(null);
            } else {
                clearLockState(user);
            }
        }

        if (requestedStatus == UserAccountStatus.INVITED) {
            user.setEmailVerifiedAt(null);
        }

        if ((statusChanged || credentialsChanged) && user.getUlid() != null) {
            this.invalidateAuthenticationArtifacts(
                    user,
                    Instant.now(this.clock)
            );
        }
    }

    private boolean unlockIfExpired(User user, Instant now) {
        if (user.getStatus() != UserAccountStatus.LOCKED
                || user.getLockedUntil() == null
                || user.getLockedUntil().isAfter(now)) {
            return false;
        }

        UserAccountStatus restoredStatus = user.getStatusBeforeLock();
        if (restoredStatus != UserAccountStatus.ACTIVE
                && restoredStatus != UserAccountStatus.INVITED) {
            restoredStatus = UserAccountStatus.DISABLED;
        }
        user.setStatus(restoredStatus);
        clearLockState(user);
        return true;
    }

    private void invalidateAuthenticationArtifacts(
            User user,
            Instant now
    ) {
        user.setTokenVersion(valueOrZero(user.getTokenVersion()) + 1L);
        this.refreshTokenRepository.revokeAllActiveByUserId(user.getUlid(), now);
        this.authChallengeRepository.invalidateActiveForUser(user.getUlid(), now);
    }

    private static void clearLockState(User user) {
        user.setFailedAuthenticationAttempts(0);
        user.setLockedUntil(null);
        user.setStatusBeforeLock(null);
    }

    private static int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }
}
