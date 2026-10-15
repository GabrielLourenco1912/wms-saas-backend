package dev.lourencogabriel.wmssaas;

import dev.lourencogabriel.wmssaas.auth.service.TokenService;
import dev.lourencogabriel.wmssaas.shared.model.UserAccountStatus;
import dev.lourencogabriel.wmssaas.user.model.User;
import dev.lourencogabriel.wmssaas.user.repository.AccessProfileRepository;
import dev.lourencogabriel.wmssaas.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class WmsSaasApplicationTests {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired AccessProfileRepository profiles;
    @Autowired TokenService tokens;
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings;

    @Test
    void everyProductionApiRouteHasAnExplicitAuthorizationPolicy() {
        Set<String> publicPosts = Set.of("/api/auth/login", "/api/auth/forgot-password", "/api/auth/reset-password",
                "/api/auth/challenge/verify", "/api/auth/refresh", "/api/auth/logout");
        mappings.getHandlerMethods().forEach((mapping, method) -> {
            for (String path : mapping.getPatternValues()) {
                if (!path.startsWith("/api/")) continue;
                for (var verb : mapping.getMethodsCondition().getMethods()) {
                    if (verb.name().equals("POST") && publicPosts.contains(path)) continue;
                    if (verb.name().equals("GET") && path.equals("/api/users/me")) continue;
                    assertTrue(dev.lourencogabriel.wmssaas.auth.authorization.WmsEndpointPolicy.RULES.stream()
                            .anyMatch(rule -> rule.method().name().equals(verb.name())
                                    && rule.pattern().replaceAll("\\{[^}]+}", "{}").equals(path.replaceAll("\\{[^}]+}", "{}"))),
                            () -> "Missing authorization policy: " + verb + " " + path);
                }
            }
        });
    }

    @Test
    void contextLoads() {
    }

    @Test
    void shouldExposeTheStockControllerToAnAuthorizedAccount() throws Exception {
        User user = new User("Manager", "missing-module@example.com", "unused-test-hash");
        user.setProfile(profiles.findById("GESTOR").orElseThrow());
        user.setStatus(UserAccountStatus.ACTIVE);
        user.setEmailVerifiedAt(Instant.now());
        user = users.saveAndFlush(user);
        try {
            String token = tokens.generateTokens(user.getUlid()).token();
            mvc.perform(get("/api/stock").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.content").isArray());
        } finally {
            users.deleteById(user.getUlid());
        }
    }

}
