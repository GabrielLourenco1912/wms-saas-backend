package dev.lourencogabriel.wmssaas.user;

import dev.lourencogabriel.wmssaas.auth.authorization.WmsEndpointPolicy;
import dev.lourencogabriel.wmssaas.auth.service.TokenService;
import dev.lourencogabriel.wmssaas.shared.model.UserAccountStatus;
import dev.lourencogabriel.wmssaas.user.model.User;
import dev.lourencogabriel.wmssaas.user.repository.AccessProfileRepository;
import dev.lourencogabriel.wmssaas.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:pagination-search;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class PaginationSearchIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired AccessProfileRepository profiles;
    @Autowired TokenService tokens;
    String manager;

    @BeforeEach
    void authenticateManager() {
        manager = bearer(user("Gestor", "pagination-manager@example.com", "GESTOR", UserAccountStatus.ACTIVE));
    }

    @Test
    void everyCollectionAndSearchAliasUsesTheSameValidatedPagingContract() throws Exception {
        for (var rule : WmsEndpointPolicy.RULES) {
            if (!rule.pattern().startsWith("/api/search/")) continue;
            String search = rule.pattern();
            String collection = search.replace("/api/search/", "/api/");
            // Packaging also has a collection scoped by its parent product; the global query is its search route.
            for (String path : collection.endsWith("/product-packages")
                    ? new String[]{search} : new String[]{collection, search}) {
                mvc.perform(get(path).param("q", "contract-proof").header("Authorization", manager))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.data.page").value(0))
                        .andExpect(jsonPath("$.data.size").value(20)).andExpect(jsonPath("$.data.content").isArray())
                        .andExpect(jsonPath("$.data.totalElements").isNumber())
                        .andExpect(jsonPath("$.data.first").value(true));
                mvc.perform(get(path).param("page", "1").param("size", "3").header("Authorization", manager))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.data.page").value(1))
                        .andExpect(jsonPath("$.data.size").value(3));
                mvc.perform(get(path).param("size", "101").header("Authorization", manager))
                        .andExpect(status().isBadRequest());
                mvc.perform(get(path).param("page", "-1").header("Authorization", manager))
                        .andExpect(status().isBadRequest());
            }
        }
    }

    @Test
    void everyDropdownUsesSmallPagesAndRejectsOversizedRequests() throws Exception {
        for (var rule : WmsEndpointPolicy.RULES) {
            if (!rule.pattern().startsWith("/api/lookups/")) continue;
            mvc.perform(get(rule.pattern()).param("q", "contract-proof").header("Authorization", manager))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.page").value(0))
                    .andExpect(jsonPath("$.data.size").value(5)).andExpect(jsonPath("$.data.content").isArray());
            mvc.perform(get(rule.pattern()).param("size", "6").header("Authorization", manager))
                    .andExpect(status().isBadRequest());
            mvc.perform(get(rule.pattern()).param("size", "0").header("Authorization", manager))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void userSearchCombinesTextProfileAndStatusAndTraversesAllPagesWithoutDuplicates() throws Exception {
        for (int i = 0; i < 23; i++) user("Operador " + i, "worker" + i + "@example.com", "OPERADOR", UserAccountStatus.ACTIVE);
        user("Operador inativo", "inactive-worker@example.com", "OPERADOR", UserAccountStatus.DISABLED);
        mvc.perform(get("/api/users").param("q", "  OPERADOR ").param("profileCode", "OPERADOR")
                        .param("status", "ACTIVE").header("Authorization", manager))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content", hasSize(20)))
                .andExpect(jsonPath("$.data.totalElements").value(23)).andExpect(jsonPath("$.data.totalPages").value(2));
        var first = mvc.perform(get("/api/search/users").param("q", "worker").param("status", "ACTIVE")
                        .param("profileCode", "OPERADOR").param("page", "0").param("size", "22")
                        .header("Authorization", manager))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content", hasSize(22)))
                .andExpect(jsonPath("$.data.last").value(false)).andReturn().getResponse().getContentAsString();
        var second = mvc.perform(get("/api/search/users").param("q", "worker").param("status", "ACTIVE")
                        .param("profileCode", "OPERADOR").param("page", "1").param("size", "22")
                        .header("Authorization", manager))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.last").value(true)).andReturn().getResponse().getContentAsString();
        var mapper = new tools.jackson.databind.ObjectMapper();
        var lastId = mapper.readTree(second).path("data").path("content").get(0).path("id").asText();
        org.junit.jupiter.api.Assertions.assertFalse(first.contains(lastId));
        mvc.perform(get("/api/users").param("page", "99").param("size", "22").param("q", "worker")
                        .header("Authorization", manager))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content", hasSize(0)))
                .andExpect(jsonPath("$.data.totalElements").value(24));
    }

    @Test
    void userDropdownOnlyExposesCompactOptionsAndDefaultsToVerifiedActiveAccounts() throws Exception {
        for (int i = 0; i < 7; i++) user("Seletor " + i, "selector" + i + "@example.com", "OPERADOR", UserAccountStatus.ACTIVE);
        user("Seletor desativado", "selector-disabled@example.com", "OPERADOR", UserAccountStatus.DISABLED);
        User unverified = user("Seletor pendente", "selector-unverified@example.com", "OPERADOR", UserAccountStatus.ACTIVE);
        unverified.setEmailVerifiedAt(null); users.saveAndFlush(unverified);
        mvc.perform(get("/api/lookups/users").param("q", "seletor").param("profileCode", "OPERADOR")
                        .header("Authorization", manager))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content", hasSize(5)))
                .andExpect(jsonPath("$.data.totalElements").value(7))
                .andExpect(jsonPath("$.data.content[0].id").isString()).andExpect(jsonPath("$.data.content[0].label").isString())
                .andExpect(jsonPath("$.data.content[0].email").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].permissions").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].password").doesNotExist());
        mvc.perform(get("/api/lookups/users").param("q", "seletor").param("page", "1").header("Authorization", manager))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content", hasSize(2)));
    }

    @Test
    void profilesAndPermissionsAreSearchableAndPaginated() throws Exception {
        mvc.perform(get("/api/profiles").param("size", "1").header("Authorization", manager))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.totalElements").value(2)).andExpect(jsonPath("$.data.totalPages").value(2));
        mvc.perform(get("/api/search/profiles").param("q", "oper").param("active", "true")
                        .header("Authorization", manager))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].code").value("OPERADOR"));
        mvc.perform(get("/api/lookups/profiles").param("q", "gest").header("Authorization", manager))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].id").value("GESTOR"));
        mvc.perform(get("/api/permissions").header("Authorization", manager))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content", hasSize(20)));
        mvc.perform(get("/api/search/permissions").param("q", "view").param("resource", "product")
                        .param("functionalRequirement", "RF004").header("Authorization", manager))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].code").value("PRODUCT_VIEW"));
        mvc.perform(get("/api/lookups/permissions").param("q", "rf016").header("Authorization", manager))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content", hasSize(5)))
                .andExpect(jsonPath("$.data.content[0].id").isString());
    }

    @Test
    void literalSearchAndInvalidQueryValuesNeverBecomeWildcardQueriesOrServerErrors() throws Exception {
        user("Literal_100%", "literal@example.com", "OPERADOR", UserAccountStatus.ACTIVE);
        for (String literal : new String[]{"_", "%", "Literal_100%"}) {
            mvc.perform(get("/api/search/users").param("q", literal).header("Authorization", manager))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        }
        for (String[] parameter : new String[][]{{"page", "bad"}, {"size", "0"}, {"size", "-1"},
                {"size", "101"}, {"page", "2147483647"}, {"q", "a".repeat(161)}, {"status", "INVALID"}}) {
            mvc.perform(get("/api/search/users").param(parameter[0], parameter[1]).header("Authorization", manager))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void searchAndLookupCannotBypassUserAndProfilePermissions() throws Exception {
        String operator = bearer(user("Operador", "search-operator@example.com", "OPERADOR", UserAccountStatus.ACTIVE));
        for (String resource : new String[]{"users", "profiles", "permissions", "stock-transactions"}) {
            for (String kind : new String[]{"search", "lookups"}) {
                mvc.perform(get("/api/" + kind + "/" + resource).header("Authorization", operator))
                        .andExpect(status().isForbidden());
                mvc.perform(get("/api/" + kind + "/" + resource)).andExpect(status().isUnauthorized());
            }
        }
    }

    private User user(String name, String email, String profile, UserAccountStatus status) {
        User user = new User(name, email, "unused-test-hash");
        user.setProfile(profiles.findById(profile).orElseThrow()); user.setStatus(status);
        user.setEmailVerifiedAt(Instant.now());
        return users.saveAndFlush(user);
    }

    private String bearer(User user) {
        return "Bearer " + tokens.generateTokens(user.getUlid()).token();
    }
}
