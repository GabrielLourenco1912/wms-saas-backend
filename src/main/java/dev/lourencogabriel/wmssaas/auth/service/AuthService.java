package dev.lourencogabriel.wmssaas.auth.service;

import dev.lourencogabriel.wmssaas.auth.dto.AuthChallengeResponse;
import dev.lourencogabriel.wmssaas.auth.dto.AuthResponse;
import dev.lourencogabriel.wmssaas.auth.dto.RegisterRequest;
import dev.lourencogabriel.wmssaas.auth.exception.InvalidAuthChallengeException;
import dev.lourencogabriel.wmssaas.auth.exception.PasswordResetUnavailableException;
import dev.lourencogabriel.wmssaas.auth.model.AuthChallengePurpose;
import dev.lourencogabriel.wmssaas.user.exception.EmailAlreadyInUseException;
import dev.lourencogabriel.wmssaas.user.model.AccessProfile;
import dev.lourencogabriel.wmssaas.user.model.User;
import dev.lourencogabriel.wmssaas.user.repository.AccessProfileRepository;
import dev.lourencogabriel.wmssaas.user.repository.UserRepository;
import dev.lourencogabriel.wmssaas.web.exception.BusinessRuleException;

import java.util.Locale;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authManager;
    private final TokenService tokenService;
    private final AuthChallengeService challengeService;
    private final AccountSecurityService accountSecurityService;
    private final AccessProfileRepository profileRepository;

    @Transactional
    public AuthChallengeResponse authenticate(String email, String password) {
        return this.authenticate(email, password, null, null);
    }

    @Transactional
    public AuthChallengeResponse authenticate(
            String email,
            String password,
            String userAgent,
            String ipAddress
    ) {
        String normalizedEmail = this.normalizeEmail(email);
        this.accountSecurityService.unlockExpiredByEmail(normalizedEmail);

        User currentUser;
        try {
            currentUser = this.authenticateUser(normalizedEmail, password);
        } catch (AuthenticationException exception) {
            this.accountSecurityService.recordFailedAuthentication(normalizedEmail);
            throw exception;
        }
        AuthChallengePurpose purpose = currentUser.getEmailVerifiedAt() == null
                ? AuthChallengePurpose.EMAIL_VERIFICATION
                : AuthChallengePurpose.MFA_LOGIN;
        return this.challengeService.issue(currentUser, purpose, userAgent, ipAddress);
    }

    @Transactional
    @PreAuthorize("@wmsAuthorization.can(authentication, 'USER_CREATE') and @wmsAuthorization.can(authentication, 'USER_PROFILE_ASSIGN')")
    public AuthChallengeResponse register(RegisterRequest registerRequest) {
        return this.register(registerRequest, null, null);
    }

    @Transactional
    @PreAuthorize("@wmsAuthorization.can(authentication, 'USER_CREATE') and @wmsAuthorization.can(authentication, 'USER_PROFILE_ASSIGN')")
    public AuthChallengeResponse register(
            RegisterRequest registerRequest,
            String userAgent,
            String ipAddress
    ) {
        String email = this.normalizeEmail(registerRequest.email());
        this.ensureEmailIsAvailable(email);
        AccessProfile profile = this.profileRepository.findByCodeForUpdate(registerRequest.profileCode())
                .filter(AccessProfile::isActive)
                .orElseThrow(() -> new BusinessRuleException("An active WMS profile is required"));
        User newUser = new User(registerRequest.name().trim(), email,
                this.passwordEncoder.encode(registerRequest.password()));
        newUser.setProfile(profile);
        User savedUser = this.userRepository.save(newUser);

        return this.challengeService.issue(
                savedUser,
                AuthChallengePurpose.EMAIL_VERIFICATION,
                userAgent,
                ipAddress
        );
    }

    @Transactional
    public AuthChallengeResponse requestPasswordReset(
            String email,
            String userAgent,
            String ipAddress
    ) {
        String normalizedEmail = this.normalizeEmail(email);
        this.accountSecurityService.unlockExpiredByEmail(normalizedEmail);
        User user = this.userRepository.findByEmailForUpdate(normalizedEmail)
                .filter(this::canResetPassword)
                .orElseThrow(PasswordResetUnavailableException::new);

        return this.challengeService.issue(
                user,
                AuthChallengePurpose.PASSWORD_RESET,
                userAgent,
                ipAddress
        );
    }

    @Transactional(noRollbackFor = InvalidAuthChallengeException.class)
    public void resetPassword(String challengeId, String code, String newPassword) {
        User user = this.challengeService.verifyPasswordReset(challengeId, code);
        user.setPassword(this.passwordEncoder.encode(newPassword));
        this.accountSecurityService.applyAdministrativeUpdate(
                user,
                user.getStatus(),
                true
        );
    }

    @Transactional(noRollbackFor = InvalidAuthChallengeException.class)
    public AuthResponse verifyChallenge(
            String challengeId,
            String code,
            String userAgent,
            String ipAddress
    ) {
        User user = this.challengeService.verify(challengeId, code);
        return this.tokenService.generateTokens(user.getUlid(), userAgent, ipAddress);
    }

    @Transactional
    public AuthResponse refresh(String refreshToken, String userAgent, String ipAddress) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new JwtException("Refresh token is required");
        }

        return this.tokenService.refreshTokens(refreshToken, userAgent, ipAddress);
    }

    @Transactional
    public void logout(String refreshToken) {
        this.tokenService.revokeRefreshToken(refreshToken);
    }

    private User authenticateUser(String email, String password) {
        Authentication authentication = this.authManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, password)
        );

        if (authentication.getPrincipal() instanceof User user) {
            return user;
        }

        throw new IllegalStateException("Authenticated principal is not a User");
    }

    private void ensureEmailIsAvailable(String email) {
        if (this.userRepository.existsByEmail(email)) {
            throw new EmailAlreadyInUseException(email);
        }
    }

    private boolean canResetPassword(User user) {
        return user.isEnabled() && user.isAccountNonLocked();
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
