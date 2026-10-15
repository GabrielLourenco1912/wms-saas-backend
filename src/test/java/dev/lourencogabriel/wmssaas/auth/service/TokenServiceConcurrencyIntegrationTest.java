package dev.lourencogabriel.wmssaas.auth.service;

import dev.lourencogabriel.wmssaas.auth.dto.AuthResponse;
import dev.lourencogabriel.wmssaas.auth.model.RefreshToken;
import dev.lourencogabriel.wmssaas.auth.repository.RefreshTokenRepository;
import dev.lourencogabriel.wmssaas.user.model.User;
import dev.lourencogabriel.wmssaas.user.repository.AccessProfileRepository;
import dev.lourencogabriel.wmssaas.shared.model.UserAccountStatus;
import java.time.Instant;
import dev.lourencogabriel.wmssaas.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.concurrent.*;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TokenServiceConcurrencyIntegrationTest {

    private static final String USER_EMAIL = "concurrency@example.com";

    @Container
    @ServiceConnection(type = org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails.class)
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4.0")
            .withDatabaseName("wms_saas_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void migrationCredentials(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.url", MYSQL::getJdbcUrl);
        registry.add("spring.flyway.user", () -> "root");
        registry.add("spring.flyway.password", MYSQL::getPassword);
    }

    @Autowired
    private AccessProfileRepository profileRepository;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        this.refreshTokenRepository.deleteAll();
        this.userRepository.deleteAll();
    }

    @Test
    void shouldAllowOnlyOneRotationWhenSameRefreshTokenIsUsedConcurrently() throws Exception {
        User user = new User("Concurrent User", USER_EMAIL, "encoded-password");
        user.setStatus(UserAccountStatus.ACTIVE);
        user.setEmailVerifiedAt(Instant.now());
        user.setProfile(this.profileRepository.findById("OPERADOR").orElseThrow());
        user = this.userRepository.saveAndFlush(user);
        AuthResponse initialTokens = this.tokenService.generateTokens(
                user.getUlid(),
                "initial-agent",
                "203.0.113.1"
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstRotationFinished = new CountDownLatch(1);
        CountDownLatch allowFirstCommit = new CountDownLatch(1);
        CountDownLatch secondRotationStarted = new CountDownLatch(1);

        try {
            Future<AuthResponse> firstResult = executor.submit(() -> {
                TransactionTemplate transaction = new TransactionTemplate(this.transactionManager);
                return transaction.execute(status -> {
                    AuthResponse response = this.tokenService.refreshTokens(
                            initialTokens.refreshToken(),
                            "first-agent",
                            "203.0.113.2"
                    );
                    firstRotationFinished.countDown();
                    this.await(allowFirstCommit);
                    return response;
                });
            });

            assertTrue(
                    firstRotationFinished.await(10, SECONDS),
                    "The first rotation did not acquire and hold the token lock"
            );

            Future<AuthResponse> secondResult = executor.submit(() -> {
                secondRotationStarted.countDown();
                return this.tokenService.refreshTokens(
                        initialTokens.refreshToken(),
                        "second-agent",
                        "203.0.113.3"
                );
            });

            assertTrue(secondRotationStarted.await(5, SECONDS));

            try {
                assertThrows(
                        TimeoutException.class,
                        () -> secondResult.get(500, MILLISECONDS),
                        "The second rotation should wait while the first transaction holds the lock"
                );
            } finally {
                allowFirstCommit.countDown();
            }

            AuthResponse winningResponse = firstResult.get(10, SECONDS);
            ExecutionException losingResult = assertThrows(
                    ExecutionException.class,
                    () -> secondResult.get(10, SECONDS)
            );

            JwtException losingException = assertInstanceOf(
                    JwtException.class,
                    losingResult.getCause()
            );
            RefreshToken originalToken = this.refreshTokenRepository
                    .findByTokenHash(this.hash(initialTokens.refreshToken()))
                    .orElseThrow();
            RefreshToken replacementToken = this.refreshTokenRepository
                    .findByTokenHash(this.hash(winningResponse.refreshToken()))
                    .orElseThrow();

            assertAll(
                    () -> assertEquals("Refresh token has been revoked", losingException.getMessage()),
                    () -> assertNotNull(originalToken.getRevokedAt()),
                    () -> assertEquals(
                            this.hash(winningResponse.refreshToken()),
                            originalToken.getReplacedByToken()
                    ),
                    () -> assertNull(replacementToken.getRevokedAt()),
                    () -> assertEquals(2, this.refreshTokenRepository.count())
            );
        } finally {
            allowFirstCommit.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, SECONDS));
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, SECONDS)) {
                throw new IllegalStateException("Timed out while coordinating concurrent transactions");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent test was interrupted", exception);
        }
    }

    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
