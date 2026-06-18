package dev.lourencogabriel.wmssaas.controller;

import dev.lourencogabriel.wmssaas.dto.*;
import dev.lourencogabriel.wmssaas.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
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

        ApiResponse<AuthResponse> apiResponse = new ApiResponse<>(authResponse, "Register and authentication Success", HttpStatus.CREATED);

        return ResponseEntity.status(HttpStatus.CREATED).body(apiResponse);
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@RequestBody RefreshRequest refreshRequest, HttpServletRequest request) {
        String jwt = request.getHeader("Authorization");

        if (jwt != null && jwt.startsWith("Bearer ")) {
            jwt = jwt.substring(7);
        } else {
            throw new RuntimeException("Invalid access token in refresh route.");
        }

        AuthResponse authResponse = this.authService.refresh(refreshRequest, jwt);

        ApiResponse<AuthResponse> apiResponse = new ApiResponse<>(authResponse, "Refresh Success");

        return ResponseEntity.ok(apiResponse);
    }
}
