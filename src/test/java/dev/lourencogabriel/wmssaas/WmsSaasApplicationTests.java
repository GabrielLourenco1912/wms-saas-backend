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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class WmsSaasApplicationTests {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired AccessProfileRepository profiles;
    @Autowired TokenService tokens;

    @Test
    void contextLoads() {
    }

    @Test
    void shouldReturnNotFoundForAnAuthorizedModuleWithoutAController() throws Exception {
        User user = new User("Manager", "missing-module@example.com", "unused-test-hash");
        user.setProfile(profiles.findById("GESTOR").orElseThrow());
        user.setStatus(UserAccountStatus.ACTIVE);
        user.setEmailVerifiedAt(Instant.now());
        user = users.saveAndFlush(user);
        try {
            String token = tokens.generateTokens(user.getUlid()).token();
            mvc.perform(get("/api/stock").header("Authorization", "Bearer " + token))
                    .andExpect(status().isNotFound());
        } finally {
            users.deleteById(user.getUlid());
        }
    }

}
