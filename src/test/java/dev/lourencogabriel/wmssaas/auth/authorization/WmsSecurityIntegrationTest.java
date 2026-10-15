package dev.lourencogabriel.wmssaas.auth.authorization;

import dev.lourencogabriel.wmssaas.auth.dto.AuthResponse;
import dev.lourencogabriel.wmssaas.auth.repository.AuthChallengeRepository;
import dev.lourencogabriel.wmssaas.auth.repository.RefreshTokenRepository;
import dev.lourencogabriel.wmssaas.auth.service.TokenService;
import dev.lourencogabriel.wmssaas.mail.EmailMessage;
import dev.lourencogabriel.wmssaas.mail.EmailDeliveryException;
import dev.lourencogabriel.wmssaas.mail.EmailSender;
import dev.lourencogabriel.wmssaas.shared.model.UserAccountStatus;
import dev.lourencogabriel.wmssaas.user.model.User;
import dev.lourencogabriel.wmssaas.user.repository.AccessProfileRepository;
import dev.lourencogabriel.wmssaas.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static dev.lourencogabriel.wmssaas.auth.authorization.WmsPermission.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:wms-security;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "api.security.challenge.max-attempts=2"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(WmsSecurityIntegrationTest.ProbeConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WmsSecurityIntegrationTest {
    private static final Set<WmsPermission> OPERATOR_PERMISSIONS = Set.of(
            WAREHOUSE_VIEW, PRODUCT_VIEW, RECEIPT_VIEW, RECEIPT_EXECUTE, PUTAWAY_VIEW, PUTAWAY_EXECUTE,
            PICKING_VIEW, PICKING_EXECUTE, PICKING_REPORT_DIVERGENCE, TRANSFER_VIEW, TRANSFER_EXECUTE,
            STOCK_VIEW, SHIPMENT_VIEW, SHIPMENT_EXECUTE, INVENTORY_VIEW, INVENTORY_EXECUTE, TASK_VIEW);
    private static final Set<WmsPermission> OPERATOR_EXECUTION = Set.of(
            RECEIPT_EXECUTE, PUTAWAY_EXECUTE, PICKING_EXECUTE, PICKING_REPORT_DIVERGENCE,
            TRANSFER_EXECUTE, SHIPMENT_EXECUTE, INVENTORY_EXECUTE);
    private static final Pattern CODE = Pattern.compile("(?m)^([0-9]{6})$");
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired AccessProfileRepository profiles;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired AuthChallengeRepository challenges;
    @Autowired TokenService tokens;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper mapper;
    @MockitoBean EmailSender emailSender;
    final AtomicReference<EmailMessage> lastEmail = new AtomicReference<>();
    final Map<String, Set<WmsPermission>> initialPermissions = new HashMap<>();
    User manager;
    User operator;
    AuthResponse managerTokens;
    AuthResponse operatorTokens;
    String passwordHash;

    @BeforeAll
    void rememberDefaults() {
        this.passwordHash = encoder.encode("password-123");
        profiles.findAll().forEach(p -> initialPermissions.put(p.getCode(), Set.copyOf(p.getPermissions())));
        assertEquals(OPERATOR_PERMISSIONS, initialPermissions.get("OPERADOR"));
        var expectedManager = EnumSet.allOf(WmsPermission.class);
        expectedManager.removeAll(OPERATOR_EXECUTION);
        assertEquals(expectedManager, initialPermissions.get("GESTOR"));
    }

    @BeforeEach
    void prepare() {
        challenges.deleteAll();
        refreshTokens.deleteAll();
        users.deleteAll();
        for (var p : profiles.findAll()) {
            p.setActive(true);
            p.setPermissions(new HashSet<>(initialPermissions.get(p.getCode())));
            profiles.saveAndFlush(p);
        }
        manager = activeUser("manager@example.com", "GESTOR");
        operator = activeUser("operator@example.com", "OPERADOR");
        managerTokens = tokens.generateTokens(manager.getUlid());
        operatorTokens = tokens.generateTokens(operator.getUlid());
        reset(emailSender);
        lastEmail.set(null);
        doAnswer(call -> { lastEmail.set(call.getArgument(0)); return null; })
                .when(emailSender).send(any());
    }

    record AccessCase(WmsEndpointPolicy.Rule rule, String profile, boolean allowed) {}

    static Stream<AccessCase> accessCases() {
        // This matrix checks authorization independently from each route's operational request data.
        return WmsEndpointPolicy.RULES.stream()
                .filter(r -> !r.pattern().startsWith("/api/users") && !r.pattern().startsWith("/api/profiles")
                        && !r.pattern().startsWith("/api/auth") && !r.pattern().equals("/api/permissions"))
                .flatMap(r -> Stream.of(
                        new AccessCase(r, "OPERADOR", OPERATOR_PERMISSIONS.contains(r.permission())),
                        new AccessCase(r, "GESTOR", !OPERATOR_EXECUTION.contains(r.permission()))));
    }

    @ParameterizedTest
    @MethodSource("accessCases")
    void shouldApplyTheArticlePermissionMatrixToEveryLogisticsRoute(AccessCase test) throws Exception {
        var path = test.rule().pattern().replaceAll("\\{[^}]+}", "test-id");
        var token = "GESTOR".equals(test.profile()) ? managerTokens.token() : operatorTokens.token();
        var result = mvc.perform(MockMvcRequestBuilders.request(test.rule().method(), path)
                        .header("Authorization", "Bearer " + token)).andReturn();
        int status = result.getResponse().getStatus();
        if (test.allowed()) {
            // A real controller can reject absent payloads or unknown fixture IDs after authorization.
            assertTrue(status >= 200 && status < 500 && status != 401 && status != 403,
                    () -> test.profile() + " should reach " + test.rule().method() + " " + path
                            + " after authorization, but received " + status);
        } else {
            assertEquals(403, status, () -> test.profile() + " must not access " + path);
        }
    }

    @Test
    void shouldRejectAnonymousSignupAndOperatorAdministration() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").exists());
        for (String path : List.of("/api/users", "/api/profiles", "/api/permissions")) {
            mvc.perform(get(path).header("Authorization", bearer(operatorTokens)))
                    .andExpect(status().isForbidden());
            mvc.perform(get(path).header("Authorization", bearer(managerTokens)))
                    .andExpect(status().isOk());
        }
        mvc.perform(get("/api/profiles").header("Authorization", bearer(operatorTokens))
                        .header("X-Client-Type", "WEB"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldExposeOnlySafeCurrentUserData() throws Exception {
        mvc.perform(get("/api/users/me").header("Authorization", bearer(operatorTokens)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.profileCode").value("OPERADOR"))
                .andExpect(jsonPath("$.data.permissions").isArray())
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.tokenVersion").doesNotExist());
    }

    @Test
    void shouldRejectInvalidBearerTokensAndUnknownRoutes() throws Exception {
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized()).andExpect(header().exists("WWW-Authenticate"));
        mvc.perform(get("/api/unclassified-feature").header("Authorization", bearer(managerTokens)))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldDenyEveryDirectLedgerWriteEvenToManagers() throws Exception {
        for (var method : List.of(org.springframework.http.HttpMethod.POST, org.springframework.http.HttpMethod.PUT,
                org.springframework.http.HttpMethod.PATCH, org.springframework.http.HttpMethod.DELETE)) {
            mvc.perform(MockMvcRequestBuilders.request(method, "/api/stock-transactions/test-id")
                            .header("Authorization", bearer(managerTokens)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void shouldDenyAnInactiveAccountImmediately() throws Exception {
        operator.setStatus(UserAccountStatus.DISABLED);
        users.saveAndFlush(operator);
        mvc.perform(get("/api/users/me").header("Authorization", bearer(operatorTokens)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").header("X-Client-Type", "MOBILE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("refreshToken", operatorTokens.refreshToken()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldApplyProfilePermissionChangesToAnAlreadyIssuedToken() throws Exception {
        var permissions = new HashSet<>(initialPermissions.get("OPERADOR"));
        permissions.add(AUDIT_VIEW);
        updateProfile("OPERADOR", true, permissions);
        mvc.perform(get("/api/stock-transactions").header("Authorization", bearer(operatorTokens)))
                .andExpect(status().isOk());
        updateProfile("OPERADOR", true, initialPermissions.get("OPERADOR"));
        mvc.perform(get("/api/stock-transactions").header("Authorization", bearer(operatorTokens)))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRevokeSessionsWhenTheProfileIsDisabledAndKeepThemRevokedAfterReactivation() throws Exception {
        updateProfile("OPERADOR", false, initialPermissions.get("OPERADOR"));
        mvc.perform(get("/api/users/me").header("Authorization", bearer(operatorTokens)))
                .andExpect(status().isUnauthorized());
        updateProfile("OPERADOR", true, initialPermissions.get("OPERADOR"));
        mvc.perform(get("/api/users/me").header("Authorization", bearer(operatorTokens)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").header("X-Client-Type", "MOBILE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("refreshToken", operatorTokens.refreshToken()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldProtectTheLastManagerAndProfileManagementPermissions() throws Exception {
        mvc.perform(put("/api/users/" + manager.getUlid()).header("Authorization", bearer(managerTokens))
                        .contentType(MediaType.APPLICATION_JSON).content(updateUserBody(manager, "OPERADOR", "ACTIVE")))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(put("/api/profiles/GESTOR").header("Authorization", bearer(managerTokens))
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of(
                                "name", "Gestor", "active", false, "permissions", initialPermissions.get("GESTOR")))))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void shouldRequireSeparatePermissionToAssignProfiles() throws Exception {
        var profile = profiles.findById("GESTOR").orElseThrow();
        profile.getPermissions().remove(USER_PROFILE_ASSIGN);
        profiles.saveAndFlush(profile);
        mvc.perform(put("/api/users/" + operator.getUlid()).header("Authorization", bearer(managerTokens))
                        .contentType(MediaType.APPLICATION_JSON).content(updateUserBody(operator, "GESTOR", "ACTIVE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldInvalidateTokensWhenTheUserProfileChanges() throws Exception {
        mvc.perform(put("/api/users/" + operator.getUlid()).header("Authorization", bearer(managerTokens))
                        .contentType(MediaType.APPLICATION_JSON).content(updateUserBody(operator, "GESTOR", "ACTIVE")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/users/me").header("Authorization", bearer(operatorTokens)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldCreateAnInvitedUserAndReleaseTokensOnlyAfterEmailVerification() throws Exception {
        var response = mvc.perform(post("/api/users").header("Authorization", bearer(managerTokens))
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of(
                                "name", "New operator", "email", "NEW@EXAMPLE.COM", "password", "password-123",
                                "profileCode", "OPERADOR"))))
                .andExpect(status().isCreated()).andReturn();
        var challenge = mapper.readTree(response.getResponse().getContentAsString()).path("data");
        User created = users.findByEmail("new@example.com");
        assertEquals(UserAccountStatus.INVITED, created.getStatus());
        assertTrue(encoder.matches("password-123", created.getPassword()));
        String verification = mapper.writeValueAsString(Map.of(
                "challengeId", challenge.path("challengeId").asText(), "code", issuedCode()));
        mvc.perform(post("/api/auth/challenge/verify").header("X-Client-Type", "MOBILE")
                        .contentType(MediaType.APPLICATION_JSON).content(verification))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.refreshToken").isString());
        assertEquals(UserAccountStatus.ACTIVE, users.findByEmail("new@example.com").getStatus());
        mvc.perform(post("/api/auth/challenge/verify").contentType(MediaType.APPLICATION_JSON).content(verification))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldPerformWebMfaRefreshAndLogoutUsingAnHttpOnlyCookie() throws Exception {
        String challengeId = login(operator.getEmail(), "password-123");
        var response = mvc.perform(post("/api/auth/challenge/verify").header("X-Client-Type", "WEB")
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of(
                                "challengeId", challengeId, "code", issuedCode()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("HttpOnly")))
                .andReturn();
        String cookieValue = response.getResponse().getCookie("refresh_token").getValue();
        var refreshed = mvc.perform(post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", cookieValue)))
                .andExpect(status().isOk()).andReturn();
        String rotated = refreshed.getResponse().getCookie("refresh_token").getValue();
        assertNotEquals(cookieValue, rotated);
        mvc.perform(post("/api/auth/logout").cookie(new jakarta.servlet.http.Cookie("refresh_token", rotated)))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));
        mvc.perform(post("/api/auth/refresh").cookie(new jakarta.servlet.http.Cookie("refresh_token", rotated)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldPersistWrongCodeAttemptsEvenWhenVerificationFails() throws Exception {
        String challengeId = login(operator.getEmail(), "password-123");
        String code = issuedCode();
        String wrong = "000000".equals(code) ? "000001" : "000000";
        String body = mapper.writeValueAsString(Map.of("challengeId", challengeId, "code", wrong));
        mvc.perform(post("/api/auth/challenge/verify").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        assertEquals(1, challenges.findById(challengeId).orElseThrow().getFailedAttempts());
        mvc.perform(post("/api/auth/challenge/verify").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        assertNotNull(challenges.findById(challengeId).orElseThrow().getConsumedAt());
    }

    @Test
    void shouldPersistFailedPasswordsAndLockTheAccount() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(Map.of("email", operator.getEmail(), "password", "wrong"))))
                    .andExpect(status().isUnauthorized());
        }
        User locked = users.findById(operator.getUlid()).orElseThrow();
        assertEquals(UserAccountStatus.LOCKED, locked.getStatus());
        assertEquals(5, locked.getFailedAuthenticationAttempts());
        mvc.perform(get("/api/users/me").header("Authorization", bearer(operatorTokens)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldResetPasswordAndInvalidateAllPreviousSessions() throws Exception {
        var response = mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", operator.getEmail()))))
                .andExpect(status().isAccepted()).andReturn();
        String challengeId = mapper.readTree(response.getResponse().getContentAsString())
                .path("data").path("challengeId").asText();
        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("challengeId", challengeId,
                                "code", issuedCode(), "newPassword", "new-password-123"))))
                .andExpect(status().isOk());
        assertTrue(encoder.matches("new-password-123", users.findByEmail(operator.getEmail()).getPassword()));
        mvc.perform(get("/api/users/me").header("Authorization", bearer(operatorTokens)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectPasswordsLongerThanBcryptUtf8Limit() throws Exception {
        mvc.perform(post("/api/users").header("Authorization", bearer(managerTokens))
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of(
                                "name", "Operator", "email", "new@example.com", "password", "á".repeat(37),
                                "profileCode", "OPERADOR"))))
                .andExpect(status().isBadRequest());
        assertNull(users.findByEmail("new@example.com"));
    }

    @Test
    void shouldRollBackRegistrationWhenTheVerificationEmailCannotBeSent() throws Exception {
        doThrow(new EmailDeliveryException(new IllegalStateException("SMTP unavailable")))
                .when(emailSender).send(any());
        mvc.perform(post("/api/users").header("Authorization", bearer(managerTokens))
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of(
                                "name", "Operator", "email", "new@example.com", "password", "password-123",
                                "profileCode", "OPERADOR"))))
                .andExpect(status().isServiceUnavailable());
        assertNull(users.findByEmail("new@example.com"));
        assertEquals(0, challenges.count());
    }

    @Test
    void shouldRequireEmailVerificationAndAnActiveProfileDuringLogin() throws Exception {
        operator.setStatus(UserAccountStatus.INVITED);
        operator.setEmailVerifiedAt(null);
        users.saveAndFlush(operator);
        mvc.perform(get("/api/users/me").header("Authorization", bearer(operatorTokens)))
                .andExpect(status().isUnauthorized());
        String challengeId = login(operator.getEmail(), "password-123");
        assertEquals("EMAIL_VERIFICATION", challenges.findById(challengeId).orElseThrow().getPurpose().name());
        updateProfile("OPERADOR", false, initialPermissions.get("OPERADOR"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", operator.getEmail(), "password", "password-123"))))
                .andExpect(status().isUnauthorized());
    }

    private User activeUser(String email, String code) {
        User user = new User(code, email, passwordHash);
        user.setProfile(profiles.findById(code).orElseThrow());
        user.setStatus(UserAccountStatus.ACTIVE);
        user.setEmailVerifiedAt(Instant.now());
        return users.saveAndFlush(user);
    }

    private String bearer(AuthResponse response) {
        return "Bearer " + response.token();
    }

    private String issuedCode() {
        var match = CODE.matcher(lastEmail.get().textBody());
        assertTrue(match.find());
        return match.group(1);
    }

    private String login(String email, String password) throws Exception {
        var result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.data.token").doesNotExist()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).path("data").path("challengeId").asText();
    }

    private String updateUserBody(User user, String code, String status) {
        return mapper.writeValueAsString(Map.of("name", user.getName(), "email", user.getEmail(),
                "profileCode", code, "status", status));
    }

    private void updateProfile(String code, boolean active, Set<WmsPermission> permissions) throws Exception {
        mvc.perform(put("/api/profiles/" + code).header("Authorization", bearer(managerTokens))
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of(
                                "name", code, "active", active, "permissions", permissions))))
                .andExpect(status().isOk());
    }

    @TestConfiguration
    static class ProbeConfiguration {
        @Bean PolicyProbeController policyProbeController() { return new PolicyProbeController(); }
    }

    /** Fallback for policy-only paths; concrete controllers take precedence when a route is implemented. */
    @RestController
    static class PolicyProbeController {
        @RequestMapping("/api/**")
        public Map<String, String> authorized() { return Map.of("result", "authorized"); }
    }
}
