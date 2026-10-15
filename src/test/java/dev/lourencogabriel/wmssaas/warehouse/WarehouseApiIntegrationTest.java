package dev.lourencogabriel.wmssaas.warehouse;

import dev.lourencogabriel.wmssaas.auth.service.TokenService;
import dev.lourencogabriel.wmssaas.product.model.Category;
import dev.lourencogabriel.wmssaas.product.model.Product;
import dev.lourencogabriel.wmssaas.product.repository.CategoryRepository;
import dev.lourencogabriel.wmssaas.product.repository.ProductRepository;
import dev.lourencogabriel.wmssaas.shared.model.UserAccountStatus;
import dev.lourencogabriel.wmssaas.stock.model.Stock;
import dev.lourencogabriel.wmssaas.stock.repository.StockRepository;
import dev.lourencogabriel.wmssaas.user.model.User;
import dev.lourencogabriel.wmssaas.user.repository.AccessProfileRepository;
import dev.lourencogabriel.wmssaas.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:warehouse-api;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class WarehouseApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired AccessProfileRepository profiles;
    @Autowired TokenService tokens;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired StockRepository stocks;
    String authorization;

    @BeforeEach
    void authenticateManager() {
        authorization = token("manager-warehouse@example.com", "GESTOR");
    }

    @Test
    void shouldCreateListReadAndEditTheCompleteWarehouseStructureThroughHttp() throws Exception {
        Structure structure = structure();
        mvc.perform(get("/api/warehouses").header("Authorization", authorization))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].id").value(structure.warehouseId()));
        mvc.perform(get("/api/zones").param("warehouseId", structure.warehouseId()).header("Authorization", authorization))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].warehouseId").value(structure.warehouseId()));
        mvc.perform(get("/api/positions").param("zoneId", structure.zoneId()).header("Authorization", authorization))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].id").value(structure.positionId()));
        mvc.perform(get("/api/warehouses/" + structure.warehouseId()).header("Authorization", authorization))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("Principal"));
        mvc.perform(get("/api/zones/" + structure.zoneId()).header("Authorization", authorization))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("Zona A"));
        mvc.perform(get("/api/positions/" + structure.positionId()).header("Authorization", authorization))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.code").value("A-01"));

        update("/api/warehouses/" + structure.warehouseId(), warehouseBody("Atualizado", true))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("Atualizado"));
        update("/api/zones/" + structure.zoneId(), zoneBody(structure.warehouseId(), "Zona B", true))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("Zona B"));
        update("/api/positions/" + structure.positionId(), positionBody(structure.zoneId(), "A-02", "120", "80"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.code").value("A-02"))
                .andExpect(jsonPath("$.data.weightCapacityKg").value(120));
        mvc.perform(get("/api/positions/" + structure.positionId()).header("Authorization", authorization))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.code").value("A-02"));
    }

    @Test
    void shouldCountBlockedPhysicalStockWhenReducingWeightOrVolumeCapacity() throws Exception {
        Structure structure = structure();
        Category category = new Category(); category.setName("Geral"); categories.saveAndFlush(category);
        Product product = new Product(); product.setCategoryId(category.getId()); product.setSku("SKU-CAPACITY");
        product.setName("Produto pesado"); product.setUnitWeightKg(new BigDecimal("2")); product.setUnitVolumeM3(new BigDecimal("3"));
        products.saveAndFlush(product);
        Stock stock = new Stock(); stock.setProductId(product.getId()); stock.setPositionId(structure.positionId());
        stock.setQuantity(new BigDecimal("5")); stock.setEntryAt(Instant.now()); stock.setBlockStatus("QUALIDADE"); stocks.saveAndFlush(stock);

        update("/api/positions/" + structure.positionId(), positionBody(structure.zoneId(), "A-01", "9", "100"))
                .andExpect(status().isUnprocessableContent());
        update("/api/positions/" + structure.positionId(), positionBody(structure.zoneId(), "A-01", "100", "14"))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(get("/api/positions/" + structure.positionId()).header("Authorization", authorization))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.weightCapacityKg").value(100));
        update("/api/positions/" + structure.positionId(), positionBody(structure.zoneId(), "A-01", "10", "15"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.weightCapacityKg").value(10))
                .andExpect(jsonPath("$.data.volumeCapacityM3").value(15));
    }

    @Test
    void shouldRejectInactiveParentsAndAnUnknownZone() throws Exception {
        String inactiveWarehouse = create("/api/warehouses", warehouseBody("Inativo", false));
        postBody("/api/zones", zoneBody(inactiveWarehouse, "Zona", true))
                .andExpect(status().isUnprocessableContent());
        postBody("/api/positions", positionBody("0".repeat(26), "INVALID", "10", "10"))
                .andExpect(status().isNotFound());
        String activeWarehouse = create("/api/warehouses", warehouseBody("Ativo", true));
        String inactiveZone = create("/api/zones", zoneBody(activeWarehouse, "Inativa", false));
        postBody("/api/positions", positionBody(inactiveZone, "INACTIVE", "10", "10"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void shouldRejectInvalidWarehouseZoneAndPositionPayloadsBeforePersistence() throws Exception {
        postBody("/api/warehouses", warehouseBody(" ", true)).andExpect(status().isBadRequest());
        postBody("/api/zones", zoneBody("short-id", "Zona", true)).andExpect(status().isBadRequest());
        postBody("/api/positions", positionBody("0".repeat(26), "A-01", "-1", "10"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/warehouses").header("Authorization", authorization))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void shouldReturnNotFoundForUnknownStructureIds() throws Exception {
        String missingId = "0".repeat(26);
        for (String resource : new String[]{"warehouses", "zones", "positions"}) {
            mvc.perform(get("/api/" + resource + "/" + missingId).header("Authorization", authorization))
                    .andExpect(status().isNotFound());
        }
        update("/api/warehouses/" + missingId, warehouseBody("Não existe", true))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldAllowOperatorReadsWhileRejectingUnauthorizedWritesAndAnonymousAccess() throws Exception {
        Structure structure = structure();
        String operator = token("operator-warehouse@example.com", "OPERADOR");
        mvc.perform(get("/api/warehouses/" + structure.warehouseId()).header("Authorization", operator))
                .andExpect(status().isOk());
        mvc.perform(post("/api/warehouses").header("Authorization", operator).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(warehouseBody("Proibido", true))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/warehouses")).andExpect(status().isUnauthorized());
    }

    private Structure structure() throws Exception {
        String warehouseId = create("/api/warehouses", warehouseBody("Principal", true));
        String zoneId = create("/api/zones", zoneBody(warehouseId, "Zona A", true));
        String positionId = create("/api/positions", positionBody(zoneId, "A-01", "100", "100"));
        return new Structure(warehouseId, zoneId, positionId);
    }

    private String create(String path, Object body) throws Exception {
        var result = postBody(path, body).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("201 CREATED")).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asText();
    }

    private ResultActions postBody(String path, Object body) throws Exception {
        return mvc.perform(post(path).header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)));
    }

    private ResultActions update(String path, Object body) throws Exception {
        return mvc.perform(put(path).header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)));
    }

    private String token(String email, String profile) {
        User user = new User(profile, email, "unused-test-hash"); user.setProfile(profiles.findById(profile).orElseThrow());
        user.setStatus(UserAccountStatus.ACTIVE); user.setEmailVerifiedAt(Instant.now()); user = users.saveAndFlush(user);
        return "Bearer " + tokens.generateTokens(user.getUlid()).token();
    }

    private static Map<String, Object> warehouseBody(String name, boolean active) {
        return Map.of("name", name, "address", "Rua principal", "active", active);
    }

    private static Map<String, Object> zoneBody(String warehouseId, String name, boolean active) {
        return Map.of("warehouseId", warehouseId, "name", name, "controlType", "PADRAO", "active", active);
    }

    private static Map<String, Object> positionBody(String zoneId, String code, String weight, String volume) {
        return Map.of("zoneId", zoneId, "code", code, "type", "PRATELEIRA", "weightCapacityKg", new BigDecimal(weight),
                "volumeCapacityM3", new BigDecimal(volume), "purpose", "ARMAZENAGEM", "active", true);
    }

    private record Structure(String warehouseId, String zoneId, String positionId) {}
}
