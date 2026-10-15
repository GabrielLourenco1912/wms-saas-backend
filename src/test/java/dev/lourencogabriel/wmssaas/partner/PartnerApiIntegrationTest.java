package dev.lourencogabriel.wmssaas.partner;

import dev.lourencogabriel.wmssaas.auth.service.TokenService;
import dev.lourencogabriel.wmssaas.partner.dto.PartnerDtos;
import dev.lourencogabriel.wmssaas.partner.model.Partner;
import dev.lourencogabriel.wmssaas.partner.repository.PartnerRepository;
import dev.lourencogabriel.wmssaas.shared.model.UserAccountStatus;
import dev.lourencogabriel.wmssaas.user.model.User;
import dev.lourencogabriel.wmssaas.user.repository.AccessProfileRepository;
import dev.lourencogabriel.wmssaas.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:partner-api-integration;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class PartnerApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired PartnerRepository partners;
    @Autowired UserRepository users;
    @Autowired AccessProfileRepository profiles;
    @Autowired TokenService tokens;

    private String managerToken;
    private String operatorToken;

    @BeforeEach
    void authenticateActiveProfiles() {
        managerToken = token("GESTOR", "partner-manager@example.com");
        operatorToken = token("OPERADOR", "partner-operator@example.com");
    }

    @Test
    void shouldCreateConsultListEditAndDeactivateAPartnerWithoutDeletingIt() throws Exception {
        long initialCount = partners.count();
        String id = create(new PartnerDtos.PartnerRequest("  Distribuidora Principal  ", "12345678000190", "AMBOS", true));
        assertEquals(initialCount + 1, partners.count());

        mvc.perform(get("/api/partners/{id}", id).header(HttpHeaders.AUTHORIZATION, managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.legalName").value("Distribuidora Principal"))
                .andExpect(jsonPath("$.data.taxId").value("12345678000190"))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data.updatedAt").isNotEmpty());

        mvc.perform(get("/api/partners").param("page", "0").param("size", "20")
                        .header(HttpHeaders.AUTHORIZATION, managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(initialCount + 1))
                .andExpect(jsonPath("$.data.content[0].id").value(id));

        var update = new PartnerDtos.PartnerRequest("Distribuidora Revisada", "12345678000190", "FORNECEDOR", false);
        mvc.perform(put("/api/partners/{id}", id).header(HttpHeaders.AUTHORIZATION, managerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.legalName").value("Distribuidora Revisada"))
                .andExpect(jsonPath("$.data.type").value("FORNECEDOR"))
                .andExpect(jsonPath("$.data.active").value(false));

        Partner persisted = partners.findById(id).orElseThrow();
        assertEquals("Distribuidora Revisada", persisted.getLegalName());
        assertFalse(persisted.getActive());
        assertEquals(initialCount + 1, partners.count());
    }

    @Test
    void shouldRejectDuplicateTaxIdsOnCreationAndEditionWithoutChangingTheOriginalData() throws Exception {
        String firstId = create(new PartnerDtos.PartnerRequest("Primeiro", "12345678901", "CLIENTE", true));
        String secondId = create(new PartnerDtos.PartnerRequest("Segundo", "10987654321", "FORNECEDOR", true));
        long initialCount = partners.count();
        var duplicate = new PartnerDtos.PartnerRequest("Duplicado", "12345678901", "AMBOS", false);

        mvc.perform(post("/api/partners").header(HttpHeaders.AUTHORIZATION, managerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(duplicate)))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(put("/api/partners/{id}", secondId).header(HttpHeaders.AUTHORIZATION, managerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(duplicate)))
                .andExpect(status().isUnprocessableContent());

        assertEquals(initialCount, partners.count());
        assertEquals("12345678901", partners.findById(firstId).orElseThrow().getTaxId());
        Partner second = partners.findById(secondId).orElseThrow();
        assertEquals("10987654321", second.getTaxId());
        assertEquals("Segundo", second.getLegalName());
        assertTrue(second.getActive());
    }

    @Test
    void shouldRejectInvalidTypeDocumentAndBlankNameWithBadRequest() throws Exception {
        long initialCount = partners.count();
        var invalidRequests = java.util.List.of(
                new PartnerDtos.PartnerRequest("Inválido", "12345678901", "TRANSPORTADOR", true),
                new PartnerDtos.PartnerRequest("Inválido", "documento-invalido", "CLIENTE", true),
                new PartnerDtos.PartnerRequest("   ", "12345678901", "FORNECEDOR", true));
        for (var request : invalidRequests) {
            mvc.perform(post("/api/partners").header(HttpHeaders.AUTHORIZATION, managerToken)
                            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
        assertEquals(initialCount, partners.count());
    }

    @Test
    void shouldReturnNotFoundWhenConsultingOrEditingAnUnknownPartner() throws Exception {
        String missingId = "01ZZZZZZZZZZZZZZZZZZZZZZZZ";
        mvc.perform(get("/api/partners/{id}", missingId).header(HttpHeaders.AUTHORIZATION, managerToken))
                .andExpect(status().isNotFound());
        var request = new PartnerDtos.PartnerRequest("Ausente", null, "CLIENTE", true);
        mvc.perform(put("/api/partners/{id}", missingId).header(HttpHeaders.AUTHORIZATION, managerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
        assertTrue(partners.findById(missingId).isEmpty());
    }

    @Test
    void shouldDenyOperatorAccessToTheRealControllerWithoutPersistingChanges() throws Exception {
        String id = create(new PartnerDtos.PartnerRequest("Protegido", "12345678901", "CLIENTE", true));
        long initialCount = partners.count();
        var request = new PartnerDtos.PartnerRequest("Não permitido", "10987654321", "AMBOS", false);
        mvc.perform(get("/api/partners").header(HttpHeaders.AUTHORIZATION, operatorToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/partners/{id}", id).header(HttpHeaders.AUTHORIZATION, operatorToken))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/partners").header(HttpHeaders.AUTHORIZATION, operatorToken)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/partners/{id}", id).header(HttpHeaders.AUTHORIZATION, operatorToken)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
        assertEquals(initialCount, partners.count());
        Partner original = partners.findById(id).orElseThrow();
        assertEquals("Protegido", original.getLegalName());
        assertTrue(original.getActive());
        assertFalse(partners.existsByTaxId("10987654321"));
    }

    @Test
    void shouldAllowMultiplePartnersWithoutAnOptionalTaxId() throws Exception {
        String firstId = create(new PartnerDtos.PartnerRequest("Sem documento A", null, "CLIENTE", true));
        String secondId = create(new PartnerDtos.PartnerRequest("Sem documento B", null, "FORNECEDOR", true));
        assertNotEquals(firstId, secondId);
        assertNull(partners.findById(firstId).orElseThrow().getTaxId());
        assertNull(partners.findById(secondId).orElseThrow().getTaxId());
    }

    private String create(PartnerDtos.PartnerRequest request) throws Exception {
        String json = mvc.perform(post("/api/partners").header(HttpHeaders.AUTHORIZATION, managerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.status").value("201 CREATED"))
                .andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(json).path("data").path("id").asText();
        assertEquals(26, id.length());
        return id;
    }

    private String token(String profile, String email) {
        User actor = new User("Partner test " + profile, email, "unused-test-hash");
        actor.setProfile(profiles.findById(profile).orElseThrow());
        actor.setStatus(UserAccountStatus.ACTIVE);
        actor.setEmailVerifiedAt(Instant.now());
        actor = users.saveAndFlush(actor);
        return "Bearer " + tokens.generateTokens(actor.getUlid()).token();
    }
}
