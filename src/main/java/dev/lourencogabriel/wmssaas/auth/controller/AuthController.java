package dev.lourencogabriel.wmssaas.auth.controller;

import dev.lourencogabriel.wmssaas.auth.dto.AuthResponse;
import dev.lourencogabriel.wmssaas.auth.dto.AuthChallengeResponse;
import dev.lourencogabriel.wmssaas.auth.dto.ForgotPasswordRequest;
import dev.lourencogabriel.wmssaas.auth.dto.LoginRequest;
import dev.lourencogabriel.wmssaas.auth.dto.RefreshRequest;
import dev.lourencogabriel.wmssaas.auth.dto.RegisterRequest;
import dev.lourencogabriel.wmssaas.auth.dto.ResetPasswordRequest;
import dev.lourencogabriel.wmssaas.auth.dto.VerifyAuthChallengeRequest;
import dev.lourencogabriel.wmssaas.auth.http.AuthRequestContext;
import dev.lourencogabriel.wmssaas.auth.http.AuthRequestResolver;
import dev.lourencogabriel.wmssaas.auth.http.AuthResponseFactory;
import dev.lourencogabriel.wmssaas.auth.service.AuthService;
import dev.lourencogabriel.wmssaas.web.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static dev.lourencogabriel.wmssaas.auth.http.AuthRequestResolver.CLIENT_TYPE_HEADER;
import static dev.lourencogabriel.wmssaas.auth.http.AuthResponseFactory.REFRESH_TOKEN_COOKIE;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthRequestResolver requestResolver;
    private final AuthResponseFactory responseFactory;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<?>> login(
            @Valid @RequestBody LoginRequest loginRequest,
            @RequestHeader(value = CLIENT_TYPE_HEADER, required = false) String clientTypeHeader,
            HttpServletRequest request
    ) {
        AuthRequestContext requestContext = this.requestResolver.resolve(clientTypeHeader, request);
        AuthChallengeResponse challenge = this.authService.authenticate(
                loginRequest.email(),
                loginRequest.password(),
                requestContext.userAgent(),
                requestContext.ipAddress()
        );

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new ApiResponse<>(
                challenge,
                "Authentication code sent",
                HttpStatus.ACCEPTED
        ));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<?>> register(
            @Valid @RequestBody RegisterRequest registerRequest,
            @RequestHeader(value = CLIENT_TYPE_HEADER, required = false) String clientTypeHeader,
            HttpServletRequest request
    ) {
        AuthRequestContext requestContext = this.requestResolver.resolve(clientTypeHeader, request);
        AuthChallengeResponse challenge = this.authService.register(
                registerRequest,
                requestContext.userAgent(),
                requestContext.ipAddress()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(
                challenge,
                "Registration created; verification code sent",
                HttpStatus.CREATED
        ));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<?>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest forgotPasswordRequest,
            @RequestHeader(value = CLIENT_TYPE_HEADER, required = false) String clientTypeHeader,
            HttpServletRequest request
    ) {
        AuthRequestContext requestContext = this.requestResolver.resolve(clientTypeHeader, request);
        AuthChallengeResponse challenge = this.authService.requestPasswordReset(
                forgotPasswordRequest.email(),
                requestContext.userAgent(),
                requestContext.ipAddress()
        );

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new ApiResponse<>(
                challenge,
                "Password reset code sent",
                HttpStatus.ACCEPTED
        ));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<?>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest resetPasswordRequest
    ) {
        this.authService.resetPassword(
                resetPasswordRequest.challengeId(),
                resetPasswordRequest.code(),
                resetPasswordRequest.newPassword()
        );

        return ResponseEntity.ok(new ApiResponse<>(
                null,
                "Password reset successfully",
                HttpStatus.OK
        ));
    }

    @PostMapping("/challenge/verify")
    public ResponseEntity<ApiResponse<?>> verifyChallenge(
            @Valid @RequestBody VerifyAuthChallengeRequest verificationRequest,
            @RequestHeader(value = CLIENT_TYPE_HEADER, required = false) String clientTypeHeader,
            HttpServletRequest request
    ) {
        AuthRequestContext requestContext = this.requestResolver.resolve(clientTypeHeader, request);
        AuthResponse authResponse = this.authService.verifyChallenge(
                verificationRequest.challengeId(),
                verificationRequest.code(),
                requestContext.userAgent(),
                requestContext.ipAddress()
        );

        return this.responseFactory.build(
                authResponse,
                requestContext.clientType(),
                "Authentication successful",
                HttpStatus.OK
        );
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<?>> refresh(
            @Valid @RequestBody(required = false) RefreshRequest refreshRequest,
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshTokenCookie,
            @RequestHeader(value = CLIENT_TYPE_HEADER, required = false) String clientTypeHeader,
            HttpServletRequest request
    ) {
        AuthRequestContext requestContext = this.requestResolver.resolve(clientTypeHeader, request);
        String refreshToken = this.requestResolver.resolveRefreshToken(
                requestContext.clientType(),
                refreshRequest,
                refreshTokenCookie
        );

        AuthResponse authResponse = this.authService.refresh(
                refreshToken,
                requestContext.userAgent(),
                requestContext.ipAddress()
        );

        return this.responseFactory.build(
                authResponse,
                requestContext.clientType(),
                "Refresh Success",
                HttpStatus.OK
        );
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<?>> logout(
            @Valid @RequestBody(required = false) RefreshRequest refreshRequest,
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshTokenCookie,
            @RequestHeader(value = CLIENT_TYPE_HEADER, required = false) String clientTypeHeader,
            HttpServletRequest request
    ) {
        AuthRequestContext requestContext = this.requestResolver.resolve(clientTypeHeader, request);
        String refreshToken = this.requestResolver.resolveRefreshToken(
                requestContext.clientType(),
                refreshRequest,
                refreshTokenCookie
        );

        this.authService.logout(refreshToken);

        return this.responseFactory.buildLogout(requestContext.clientType());
    }
}
