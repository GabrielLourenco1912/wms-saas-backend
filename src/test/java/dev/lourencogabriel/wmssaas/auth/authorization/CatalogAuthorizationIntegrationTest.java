package dev.lourencogabriel.wmssaas.auth.authorization;

import dev.lourencogabriel.wmssaas.auth.service.TokenService;
import dev.lourencogabriel.wmssaas.partner.repository.PartnerRepository;
import dev.lourencogabriel.wmssaas.product.repository.CategoryRepository;
import dev.lourencogabriel.wmssaas.shared.model.UserAccountStatus;
import dev.lourencogabriel.wmssaas.user.model.User;
import dev.lourencogabriel.wmssaas.user.repository.AccessProfileRepository;
import dev.lourencogabriel.wmssaas.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static dev.lourencogabriel.wmssaas.auth.authorization.WmsPermission.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:wms-catalog-authorization;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class CatalogAuthorizationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired AccessProfileRepository profiles;
    @Autowired TokenService tokens;
    @Autowired CategoryRepository categories;
    @Autowired PartnerRepository partners;

    @Test
    void managerReachesTheRealCategoryAndPartnerControllers() throws Exception {
        String bearer = bearer("GESTOR");
        for (String path : List.of("/api/categories", "/api/partners")) {
            mvc.perform(get(path).header("Authorization", bearer))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isArray());
        }
    }

    @Test
    void operatorCanReadCategoriesButCannotManageCatalogsOrReadPartners() throws Exception {
        String bearer = bearer("OPERADOR");
        mvc.perform(get("/api/categories").header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content").isArray());
        mvc.perform(get("/api/partners").header("Authorization", bearer))
                .andExpect(status().isForbidden());
        long categoryCount = categories.count();
        long partnerCount = partners.count();
        for (String path : List.of("/api/categories", "/api/partners", "/api/warehouses", "/api/products")) {
            mvc.perform(post(path).header("Authorization", bearer)
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        }
        assertEquals(categoryCount, categories.count());
        assertEquals(partnerCount, partners.count());
    }

    @Test
    void permissionRemovalTakesEffectWithThePreviouslyIssuedToken() throws Exception {
        String bearer = bearer("GESTOR");
        var profile = profiles.findById("GESTOR").orElseThrow();
        profile.getPermissions().removeAll(List.of(PRODUCT_VIEW, PRODUCT_CREATE, ORDER_VIEW, ORDER_CREATE));
        profiles.saveAndFlush(profile);
        for (String path : List.of("/api/categories", "/api/partners")) {
            mvc.perform(get(path).header("Authorization", bearer))
                    .andExpect(status().isForbidden());
            mvc.perform(post(path).header("Authorization", bearer)
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void anonymousRequestsCannotReadOrCreateCatalogs() throws Exception {
        for (String path : List.of("/api/categories", "/api/partners")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    private String bearer(String profileCode) {
        User user = new User("Catalog authorization", profileCode.toLowerCase() + "@catalog-auth.test", "unused-hash");
        user.setProfile(profiles.findById(profileCode).orElseThrow());
        user.setStatus(UserAccountStatus.ACTIVE);
        user.setEmailVerifiedAt(Instant.now());
        users.saveAndFlush(user);
        return "Bearer " + tokens.generateTokens(user.getUlid()).token();
    }
}
