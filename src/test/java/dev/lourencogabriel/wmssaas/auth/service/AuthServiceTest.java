package dev.lourencogabriel.wmssaas.auth.service;

import dev.lourencogabriel.wmssaas.auth.dto.RegisterRequest;
import dev.lourencogabriel.wmssaas.auth.model.AuthChallengePurpose;
import dev.lourencogabriel.wmssaas.shared.model.UserAccountStatus;
import dev.lourencogabriel.wmssaas.user.exception.EmailAlreadyInUseException;
import dev.lourencogabriel.wmssaas.user.model.AccessProfile;
import dev.lourencogabriel.wmssaas.user.model.User;
import dev.lourencogabriel.wmssaas.user.repository.AccessProfileRepository;
import dev.lourencogabriel.wmssaas.user.repository.UserRepository;
import dev.lourencogabriel.wmssaas.web.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock UserRepository users;
    @Mock PasswordEncoder encoder;
    @Mock AuthenticationManager manager;
    @Mock TokenService tokens;
    @Mock AuthChallengeService challenges;
    @Mock AccountSecurityService security;
    @Mock AccessProfileRepository profiles;
    AuthService service;

    @BeforeEach
    void setUp() {
        this.service = new AuthService(users, encoder, manager, tokens, challenges, security, profiles);
    }

    @Test
    void shouldNormalizeCredentialsAndRequireEmailVerificationBeforeTokens() {
        User user = new User("User", "user@example.com", "hash");
        when(manager.authenticate(any())).thenReturn(new UsernamePasswordAuthenticationToken(user, null));
        service.authenticate(" USER@EXAMPLE.COM ", "password", "agent", "203.0.113.1");
        verify(manager).authenticate(argThat(a -> "user@example.com".equals(a.getName())));
        verify(challenges).issue(user, AuthChallengePurpose.EMAIL_VERIFICATION, "agent", "203.0.113.1");
        verifyNoInteractions(tokens);
    }

    @Test
    void shouldRequireMfaForVerifiedAccounts() {
        User user = new User("User", "user@example.com", "hash");
        user.setEmailVerifiedAt(Instant.now());
        when(manager.authenticate(any())).thenReturn(new UsernamePasswordAuthenticationToken(user, null));
        service.authenticate("user@example.com", "password");
        verify(challenges).issue(user, AuthChallengePurpose.MFA_LOGIN, null, null);
        verifyNoInteractions(tokens);
    }

    @Test
    void shouldRecordFailedCredentials() {
        when(manager.authenticate(any())).thenThrow(new BadCredentialsException("Invalid credentials"));
        assertThrows(BadCredentialsException.class, () -> service.authenticate(" USER@EXAMPLE.COM ", "wrong"));
        verify(security).recordFailedAuthentication("user@example.com");
        verifyNoInteractions(tokens, challenges);
    }

    @Test
    void shouldCreateAnInvitedUserWithAnExplicitProfile() {
        var profile = new AccessProfile("OPERADOR", "Operador", Set.of());
        when(profiles.findByCodeForUpdate("OPERADOR")).thenReturn(Optional.of(profile));
        when(encoder.encode("password-123")).thenReturn("bcrypt-hash");
        when(users.save(any())).thenAnswer(call -> call.getArgument(0));
        service.register(new RegisterRequest(" New user ", " USER@EXAMPLE.COM ", "password-123", "OPERADOR"));
        verify(challenges).issue(argThat(user -> user.getProfile() == profile
                        && user.getStatus() == UserAccountStatus.INVITED
                        && "user@example.com".equals(user.getEmail()) && "New user".equals(user.getName())
                        && "bcrypt-hash".equals(user.getPassword())),
                eq(AuthChallengePurpose.EMAIL_VERIFICATION), isNull(), isNull());
        verifyNoInteractions(tokens);
    }

    @Test
    void shouldRejectDuplicateEmailAndInactiveProfiles() {
        var request = new RegisterRequest("User", "user@example.com", "password-123", "OPERADOR");
        when(users.existsByEmail(request.email())).thenReturn(true);
        assertThrows(EmailAlreadyInUseException.class, () -> service.register(request));
        when(users.existsByEmail(request.email())).thenReturn(false);
        var profile = new AccessProfile("OPERADOR", "Operador", Set.of());
        profile.setActive(false);
        when(profiles.findByCodeForUpdate("OPERADOR")).thenReturn(Optional.of(profile));
        assertThrows(BusinessRuleException.class, () -> service.register(request));
        verify(users, never()).save(any());
        verifyNoInteractions(encoder, tokens, challenges);
    }

    @Test
    void shouldRejectBlankRefreshTokens() {
        assertThrows(JwtException.class, () -> service.refresh(" ", null, null));
        verifyNoInteractions(tokens);
    }

    @Test
    void shouldDelegateRefreshAndLogout() {
        service.refresh("refresh", "agent", "ip");
        service.logout("refresh");
        verify(tokens).refreshTokens("refresh", "agent", "ip");
        verify(tokens).revokeRefreshToken("refresh");
    }
}
