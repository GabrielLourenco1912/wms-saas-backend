package dev.lourencogabriel.wmssaas.auth.config;

import dev.lourencogabriel.wmssaas.auth.dto.RegisterRequest;
import dev.lourencogabriel.wmssaas.auth.service.AccountSecurityService;
import dev.lourencogabriel.wmssaas.shared.model.UserAccountStatus;
import dev.lourencogabriel.wmssaas.user.model.SystemRoleCode;
import dev.lourencogabriel.wmssaas.user.model.User;
import dev.lourencogabriel.wmssaas.user.repository.AccessProfileRepository;
import dev.lourencogabriel.wmssaas.user.repository.UserRepository;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/** Explicit server configuration provisions the first manager without exposing public signup. */
@Component
@ConditionalOnProperty(name = "api.security.bootstrap.enabled", havingValue = "true")
public class InitialManagerBootstrap implements ApplicationRunner {
    private final UserRepository userRepository;
    private final AccessProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountSecurityService accountSecurityService;
    private final Validator validator;
    private final RegisterRequest request;

    public InitialManagerBootstrap(UserRepository userRepository, AccessProfileRepository profileRepository,
                                   PasswordEncoder passwordEncoder, AccountSecurityService accountSecurityService,
                                   Validator validator,
                                   @Value("${api.security.bootstrap.name}") String name,
                                   @Value("${api.security.bootstrap.email}") String email,
                                   @Value("${api.security.bootstrap.password}") String password) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.passwordEncoder = passwordEncoder;
        this.accountSecurityService = accountSecurityService;
        this.validator = validator;
        this.request = new RegisterRequest(name, email, password, SystemRoleCode.GESTOR.name());
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        var profile = this.profileRepository.findByCodeForUpdate(SystemRoleCode.GESTOR.name()).orElseThrow();
        if (this.userRepository.existsByProfile_Code(SystemRoleCode.GESTOR.name())) {
            return;
        }
        if (!this.validator.validate(this.request).isEmpty()) {
            throw new IllegalArgumentException("Invalid initial manager configuration");
        }
        String email = this.request.email().trim().toLowerCase(Locale.ROOT);
        User user = this.userRepository.findByEmailForUpdate(email)
                .orElseGet(() -> new User(this.request.name().trim(), email, ""));
        user.setPassword(this.passwordEncoder.encode(this.request.password()));
        user.setProfile(profile);
        this.accountSecurityService.applyAdministrativeUpdate(user, UserAccountStatus.INVITED, true);
        this.userRepository.save(user);
    }
}
