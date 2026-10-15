package dev.lourencogabriel.wmssaas.auth.http;

import dev.lourencogabriel.wmssaas.auth.dto.AccessTokenResponse;
import dev.lourencogabriel.wmssaas.auth.dto.AuthResponse;
import dev.lourencogabriel.wmssaas.web.dto.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthResponseFactoryTest {

    @Test
    void shouldReturnRefreshTokenInHttpOnlyCookieForWeb() {
        AuthResponse authResponse = this.authResponse(Instant.now().plusSeconds(3600));
        AuthResponseFactory factory = new AuthResponseFactory(true);

        ResponseEntity<ApiResponse<?>> response = factory.build(
                authResponse,
                AuthClientType.WEB,
                "Authentication Success",
                HttpStatus.OK
        );

        ApiResponse<?> body = response.getBody();
        String cookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);

        assertNotNull(body);
        assertNotNull(cookie);
        assertAll(
                () -> assertEquals(HttpStatus.OK, response.getStatusCode()),
                () -> assertInstanceOf(AccessTokenResponse.class, body.data()),
                () -> assertEquals("Authentication Success", body.message()),
                () -> assertTrue(cookie.contains("refresh_token=refresh-token")),
                () -> assertTrue(cookie.contains("Path=/api/auth")),
                () -> assertTrue(cookie.contains("Secure")),
                () -> assertTrue(cookie.contains("HttpOnly")),
                () -> assertTrue(cookie.contains("SameSite=Lax"))
        );
    }

    @Test
    void shouldReturnCompleteTokenResponseWithoutCookieForMobile() {
        AuthResponse authResponse = this.authResponse(Instant.now().plusSeconds(3600));
        AuthResponseFactory factory = new AuthResponseFactory(true);

        ResponseEntity<ApiResponse<?>> response = factory.build(
                authResponse,
                AuthClientType.MOBILE,
                "Refresh Success",
                HttpStatus.OK
        );

        ApiResponse<?> body = response.getBody();

        assertNotNull(body);
        assertAll(
                () -> assertSame(authResponse, body.data()),
                () -> assertNull(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
        );
    }

    @Test
    void shouldExpireCookieImmediatelyWhenRefreshTokenIsExpired() {
        AuthResponse authResponse = this.authResponse(Instant.now().minusSeconds(1));
        AuthResponseFactory factory = new AuthResponseFactory(false);

        ResponseEntity<ApiResponse<?>> response = factory.build(
                authResponse,
                AuthClientType.WEB,
                "Refresh Success",
                HttpStatus.OK
        );

        String cookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);

        assertNotNull(cookie);
        assertAll(
                () -> assertTrue(cookie.contains("Max-Age=0")),
                () -> assertFalse(cookie.contains("Secure"))
        );
    }

    @Test
    void shouldExpireRefreshTokenCookieOnWebLogout() {
        AuthResponseFactory factory = new AuthResponseFactory(true);

        ResponseEntity<ApiResponse<?>> response = factory.buildLogout(AuthClientType.WEB);

        ApiResponse<?> body = response.getBody();
        String cookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);

        assertNotNull(body);
        assertNotNull(cookie);
        assertAll(
                () -> assertEquals(HttpStatus.OK, response.getStatusCode()),
                () -> assertEquals("Logout Success", body.message()),
                () -> assertNull(body.data()),
                () -> assertTrue(cookie.contains("refresh_token=")),
                () -> assertTrue(cookie.contains("Path=/api/auth")),
                () -> assertTrue(cookie.contains("Max-Age=0")),
                () -> assertTrue(cookie.contains("Secure")),
                () -> assertTrue(cookie.contains("HttpOnly")),
                () -> assertTrue(cookie.contains("SameSite=Lax"))
        );
    }

    @Test
    void shouldNotSetCookieOnMobileLogout() {
        AuthResponseFactory factory = new AuthResponseFactory(true);

        ResponseEntity<ApiResponse<?>> response = factory.buildLogout(AuthClientType.MOBILE);

        assertNull(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE));
    }

    private AuthResponse authResponse(Instant refreshTokenExpiresAt) {
        Instant issuedAt = Instant.now();

        return new AuthResponse(
                "inat-ead-backend-test",
                issuedAt,
                issuedAt.plusSeconds(900),
                "user-id",
                "access-token",
                "refresh-token",
                refreshTokenExpiresAt
        );
    }
}
