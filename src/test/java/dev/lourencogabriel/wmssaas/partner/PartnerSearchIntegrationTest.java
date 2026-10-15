package dev.lourencogabriel.wmssaas.partner;

import dev.lourencogabriel.wmssaas.auth.service.TokenService;
import dev.lourencogabriel.wmssaas.partner.model.Partner;
import dev.lourencogabriel.wmssaas.partner.repository.PartnerRepository;
import dev.lourencogabriel.wmssaas.shared.model.UserAccountStatus;
import dev.lourencogabriel.wmssaas.user.model.User;
import dev.lourencogabriel.wmssaas.user.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:partner-search;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class PartnerSearchIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired AccessProfileRepository profiles;
    @Autowired TokenService tokens;
    @Autowired PartnerRepository partners;
    String manager;
    String operator;

    @BeforeEach
    void authenticate() { manager = token("manager@partner-search.test", "GESTOR"); operator = token("operator@partner-search.test", "OPERADOR"); }

    @Test
    void shouldPagePartnersAndCombineNameDocumentIdTypeAndActiveFilters() throws Exception {
        Partner last = null;
        for (int i = 0; i < 25; i++) last = partner("Acme " + i, String.format("%011d", i), i < 23 ? "CLIENTE" : "FORNECEDOR", i != 0);
        query("/api/partners").andExpect(status().isOk()).andExpect(jsonPath("$.data.content", hasSize(20)))
                .andExpect(jsonPath("$.data.totalElements").value(25));
        query("/api/search/partners?page=1").andExpect(status().isOk()).andExpect(jsonPath("$.data.content", hasSize(5)));
        query("/api/search/partners?q=ACME&type=FORNECEDOR&active=true").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
        query("/api/search/partners?taxId=00000000024&type=FORNECEDOR&q=acme").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(last.getId()));
        query("/api/partners?q=" + last.getId()).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void shouldTreatLikeMetacharactersAsLiteralTextAndApplyEveryFilterTogether() throws Exception {
        Partner expected = partner("Acme 50%_!", "11111111111", "CLIENTE", true);
        partner("Acme 50XX1", "22222222222", "CLIENTE", true);
        mvc.perform(get("/api/search/partners").header("Authorization", manager).param("q", "%_!").param("type", "CLIENTE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(expected.getId()));
        mvc.perform(get("/api/partners").header("Authorization", manager).param("q", "%_!").param("type", "FORNECEDOR"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void shouldExposeOnlyFiveActiveCompactOptionsAndEnforceThePartnerPermission() throws Exception {
        for (int i = 0; i < 9; i++) partner("Partner " + i, null, "CLIENTE", i < 7);
        query("/api/lookups/partners").andExpect(status().isOk()).andExpect(jsonPath("$.data.content", hasSize(5)))
                .andExpect(jsonPath("$.data.totalElements").value(7)).andExpect(jsonPath("$.data.content[0].label").isString())
                .andExpect(jsonPath("$.data.content[0].taxId").doesNotExist());
        query("/api/lookups/partners?active=false").andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2));
        query("/api/lookups/partners?size=6").andExpect(status().isBadRequest());
        mvc.perform(get("/api/search/partners").header("Authorization", operator)).andExpect(status().isForbidden());
        mvc.perform(get("/api/lookups/partners").header("Authorization", operator)).andExpect(status().isForbidden());
        mvc.perform(get("/api/lookups/partners")).andExpect(status().isUnauthorized());
    }

    private ResultActions query(String path) throws Exception { return mvc.perform(get(path).header("Authorization", manager)); }
    private Partner partner(String name, String taxId, String type, boolean active) {
        Partner value = new Partner(); value.setLegalName(name); value.setTaxId(taxId); value.setType(type); value.setActive(active); return partners.saveAndFlush(value);
    }
    private String token(String email, String profile) {
        User value = new User(profile, email, "unused"); value.setProfile(profiles.findById(profile).orElseThrow()); value.setStatus(UserAccountStatus.ACTIVE);
        value.setEmailVerifiedAt(Instant.now()); return "Bearer " + tokens.generateTokens(users.saveAndFlush(value).getUlid()).token();
    }
}
