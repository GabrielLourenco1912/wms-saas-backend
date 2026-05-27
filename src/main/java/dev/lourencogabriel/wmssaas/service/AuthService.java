package dev.lourencogabriel.wmssaas.service;

import dev.lourencogabriel.wmssaas.dto.AuthResponse;
import dev.lourencogabriel.wmssaas.dto.LoginRequest;
import dev.lourencogabriel.wmssaas.dto.RegisterRequest;
import dev.lourencogabriel.wmssaas.model.User;
import dev.lourencogabriel.wmssaas.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authManager;
    private final TokenService tokenService;

    public AuthResponse authenticate(String email, String password) {
        var authToken = new UsernamePasswordAuthenticationToken(email, password);

        var auth = this.authManager.authenticate(authToken);

        return this.tokenService.generateToken(auth);
    }

    @Transactional
    public AuthResponse register(RegisterRequest registerRequest) {
        User newUser = new User(registerRequest.name(), registerRequest.email(), this.passwordEncoder.encode(registerRequest.password()));

        newUser = this.userRepository.save(newUser);

        return this.authenticate(newUser.getEmail(), registerRequest.password());
    }
}