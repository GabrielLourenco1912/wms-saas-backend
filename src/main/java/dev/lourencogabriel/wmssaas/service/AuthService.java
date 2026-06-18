package dev.lourencogabriel.wmssaas.service;

import dev.lourencogabriel.wmssaas.dto.AuthResponse;
import dev.lourencogabriel.wmssaas.dto.RefreshRequest;
import dev.lourencogabriel.wmssaas.dto.RegisterRequest;
import dev.lourencogabriel.wmssaas.exception.EmailAlreadyInUseException;
import dev.lourencogabriel.wmssaas.model.User;
import dev.lourencogabriel.wmssaas.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authManager;
    private final TokenService tokenService;

    public AuthResponse authenticate(String email, String password) {
        User currentUser = this.authenticateUser(email, password);
        return this.tokenService.generateTokens(currentUser.getUlid());
    }

    @Transactional
    public AuthResponse register(RegisterRequest registerRequest) {
        this.ensureEmailIsAvailable(registerRequest.email());

        User newUser = this.buildUser(registerRequest);
        User savedUser = this.userRepository.save(newUser);

        return this.tokenService.generateTokens(savedUser.getUlid());
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest refreshRequest, String accessToken) {
        Objects.requireNonNull(refreshRequest, "Refresh request is required");
        Objects.requireNonNull(refreshRequest.refreshToken(), "Refresh token is required");

        String subject = this.tokenService.extractSubject(accessToken);
        return this.tokenService.generateTokens(subject);
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

    private User buildUser(RegisterRequest registerRequest) {
        String encodedPassword = this.passwordEncoder.encode(registerRequest.password());
        return new User(registerRequest.name(), registerRequest.email(), encodedPassword);
    }

    private void ensureEmailIsAvailable(String email) {
        if (this.userRepository.existsByEmail(email)) {
            throw new EmailAlreadyInUseException(email);
        }
    }
}
