package dev.lourencogabriel.wmssaas.controller;

import dev.lourencogabriel.wmssaas.dto.ApiResponse;
import dev.lourencogabriel.wmssaas.dto.AuthResponse;
import dev.lourencogabriel.wmssaas.dto.LoginRequest;
import dev.lourencogabriel.wmssaas.dto.RegisterRequest;
import dev.lourencogabriel.wmssaas.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@RequestBody LoginRequest loginRequest) {
        AuthResponse authResponse = this.authService.authenticate(loginRequest.email(), loginRequest.password());

        ApiResponse<AuthResponse> apiResponse = new ApiResponse<>(authResponse, "Authentication Success");

        return ResponseEntity.ok(apiResponse);
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@RequestBody RegisterRequest registerRequest) {
        AuthResponse authResponse = this.authService.register(registerRequest);

        ApiResponse<AuthResponse> apiResponse = new ApiResponse<>(authResponse, "Register and authentication Success");

        return ResponseEntity.status(HttpStatus.CREATED).body(apiResponse);
    }

}
