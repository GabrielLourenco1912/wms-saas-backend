package dev.lourencogabriel.wmssaas.auth.config;

import dev.lourencogabriel.wmssaas.auth.service.AccountSecurityService;
import dev.lourencogabriel.wmssaas.shared.model.UserAccountStatus;
import dev.lourencogabriel.wmssaas.user.model.AccessProfile;
import dev.lourencogabriel.wmssaas.user.model.User;
import dev.lourencogabriel.wmssaas.user.repository.AccessProfileRepository;
import dev.lourencogabriel.wmssaas.user.repository.UserRepository;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InitialManagerBootstrapTest {
    private final UserRepository users = mock(UserRepository.class);
    private final AccessProfileRepository profiles = mock(AccessProfileRepository.class);
    private final AccountSecurityService security = mock(AccountSecurityService.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test
    void shouldProvisionAnInvitedManagerWithoutPersistingThePlainPassword() {
        var profile = new AccessProfile("GESTOR", "Gestor", Set.of());
        when(profiles.findByCodeForUpdate("GESTOR")).thenReturn(Optional.of(profile));
        bootstrap("MANAGER@EXAMPLE.COM", "password-123").run(null);
        verify(users).save(argThat(user -> user.getProfile() == profile
                && user.getEmail().equals("manager@example.com")
                && user.getStatus() == UserAccountStatus.INVITED
                && user.getEmailVerifiedAt() == null
                && !"password-123".equals(user.getPassword())
                && encoder.matches("password-123", user.getPassword())));
    }

    @Test
    void shouldPreserveAccountsWhenAManagerAlreadyExists() {
        when(profiles.findByCodeForUpdate("GESTOR"))
                .thenReturn(Optional.of(new AccessProfile("GESTOR", "Gestor", Set.of())));
        when(users.existsByProfile_Code("GESTOR")).thenReturn(true);
        bootstrap("manager@example.com", "password-123").run(null);
        verify(users, never()).save(any());
        verifyNoInteractions(security);
    }

    @Test
    void shouldPromoteOnlyTheExplicitlyConfiguredLegacyAccount() {
        var profile = new AccessProfile("GESTOR", "Gestor", Set.of());
        when(profiles.findByCodeForUpdate("GESTOR")).thenReturn(Optional.of(profile));
        var legacy = new User("Legacy", "manager@example.com", "old-hash");
        when(users.findByEmailForUpdate("manager@example.com")).thenReturn(Optional.of(legacy));
        bootstrap("manager@example.com", "password-123").run(null);
        assertSame(profile, legacy.getProfile());
        assertTrue(encoder.matches("password-123", legacy.getPassword()));
        verify(security).applyAdministrativeUpdate(legacy, UserAccountStatus.INVITED, true);
        verify(users).save(legacy);
    }

    @Test
    void shouldRejectInvalidProvisioningCredentials() {
        when(profiles.findByCodeForUpdate("GESTOR"))
                .thenReturn(Optional.of(new AccessProfile("GESTOR", "Gestor", Set.of())));
        assertThrows(IllegalArgumentException.class, () -> bootstrap("invalid-email", "short").run(null));
        verify(users, never()).save(any());
    }

    private InitialManagerBootstrap bootstrap(String email, String password) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            return new InitialManagerBootstrap(users, profiles, encoder, security,
                    factory.getValidator(), "Initial manager", email, password);
        }
    }
}
