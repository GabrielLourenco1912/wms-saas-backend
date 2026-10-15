package dev.lourencogabriel.wmssaas.auth.http;

import dev.lourencogabriel.wmssaas.auth.dto.AccessTokenResponse;
import dev.lourencogabriel.wmssaas.auth.dto.AuthResponse;
import dev.lourencogabriel.wmssaas.web.dto.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class AuthResponseFactory {

    public static final String REFRESH_TOKEN_COOKIE = "refresh_token";

    private static final String REFRESH_TOKEN_COOKIE_PATH = "/api/auth";

    private final boolean refreshCookieSecure;

    public AuthResponseFactory(@Value("${api.security.refresh-cookie.secure:true}") boolean refreshCookieSecure) {
        this.refreshCookieSecure = refreshCookieSecure;
    }

    public ResponseEntity<ApiResponse<?>> build(
            AuthResponse authResponse,
            AuthClientType clientType,
            String message,
            HttpStatus status
    ) {
        Object responseBody = clientType.shouldReturnRefreshTokenInBody()
                ? authResponse
                : new AccessTokenResponse(authResponse);

        ApiResponse<?> apiResponse = new ApiResponse<>(responseBody, message, status);
        ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.status(status);

        if (clientType == AuthClientType.WEB) {
            responseBuilder.header(HttpHeaders.SET_COOKIE, this.buildRefreshTokenCookie(authResponse).toString());
        }

        return responseBuilder.body(apiResponse);
    }

    public ResponseEntity<ApiResponse<?>> buildLogout(AuthClientType clientType) {
        ApiResponse<?> apiResponse = new ApiResponse<>(null, "Logout Success", HttpStatus.OK);
        ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.ok();

        if (clientType == AuthClientType.WEB) {
            responseBuilder.header(HttpHeaders.SET_COOKIE, this.buildExpiredRefreshTokenCookie().toString());
        }

        return responseBuilder.body(apiResponse);
    }

    private ResponseCookie buildRefreshTokenCookie(AuthResponse authResponse) {
        Duration maxAge = Duration.between(Instant.now(), authResponse.refreshTokenExpiresAt());

        if (maxAge.isNegative()) {
            maxAge = Duration.ZERO;
        }

        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, authResponse.refreshToken())
                .httpOnly(true)
                .secure(this.refreshCookieSecure)
                .sameSite("Lax")
                .path(REFRESH_TOKEN_COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }

    private ResponseCookie buildExpiredRefreshTokenCookie() {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(this.refreshCookieSecure)
                .sameSite("Lax")
                .path(REFRESH_TOKEN_COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .build();
    }
}
