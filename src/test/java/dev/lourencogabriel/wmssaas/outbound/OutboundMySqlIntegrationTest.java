package dev.lourencogabriel.wmssaas.outbound;

import dev.lourencogabriel.wmssaas.audit.repository.StockTransactionRepository;
import dev.lourencogabriel.wmssaas.auth.service.TokenService;
import dev.lourencogabriel.wmssaas.order.repository.OrderItemRepository;
import dev.lourencogabriel.wmssaas.partner.model.Partner;
import dev.lourencogabriel.wmssaas.partner.repository.PartnerRepository;
import dev.lourencogabriel.wmssaas.product.model.Category;
import dev.lourencogabriel.wmssaas.product.model.Product;
import dev.lourencogabriel.wmssaas.product.repository.CategoryRepository;
import dev.lourencogabriel.wmssaas.product.repository.ProductRepository;
import dev.lourencogabriel.wmssaas.shared.model.UserAccountStatus;
import dev.lourencogabriel.wmssaas.stock.model.Lpn;
import dev.lourencogabriel.wmssaas.stock.model.Stock;
import dev.lourencogabriel.wmssaas.stock.repository.LpnRepository;
import dev.lourencogabriel.wmssaas.stock.repository.StockRepository;
import dev.lourencogabriel.wmssaas.stock.repository.StockReservationRepository;
import dev.lourencogabriel.wmssaas.task.repository.TaskHistoryRepository;
import dev.lourencogabriel.wmssaas.task.repository.WarehouseTaskRepository;
import dev.lourencogabriel.wmssaas.user.model.User;
import dev.lourencogabriel.wmssaas.user.repository.AccessProfileRepository;
import dev.lourencogabriel.wmssaas.user.repository.UserRepository;
import dev.lourencogabriel.wmssaas.warehouse.model.Position;
import dev.lourencogabriel.wmssaas.warehouse.model.Warehouse;
import dev.lourencogabriel.wmssaas.warehouse.model.Zone;
import dev.lourencogabriel.wmssaas.warehouse.repository.PositionRepository;
import dev.lourencogabriel.wmssaas.warehouse.repository.WarehouseRepository;
import dev.lourencogabriel.wmssaas.warehouse.repository.ZoneRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** Validates the real MySQL schema, row locks and Flyway-installed history triggers. */
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OutboundMySqlIntegrationTest {
    @Container
    @ServiceConnection(type = org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails.class)
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4.0")
            .withDatabaseName("wms_outbound_test").withUsername("test").withPassword("test");

    @DynamicPropertySource
    static void migrationCredentials(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.url", MYSQL::getJdbcUrl);
        registry.add("spring.flyway.user", () -> "root");
        registry.add("spring.flyway.password", MYSQL::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired TokenService tokens;
    @Autowired UserRepository users;
    @Autowired AccessProfileRepository profiles;
    @Autowired PasswordEncoder passwords;
    @Autowired WarehouseRepository warehouses;
    @Autowired ZoneRepository zones;
    @Autowired PositionRepository positions;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired PartnerRepository partners;
    @Autowired StockRepository stocks;
    @Autowired LpnRepository lpns;
    @Autowired OrderItemRepository items;
    @Autowired WarehouseTaskRepository tasks;
    @Autowired StockReservationRepository reservations;
    @Autowired StockTransactionRepository ledger;
    @Autowired TaskHistoryRepository history;

    @Test
    void shouldSerializeDifferentActorsAndRejectEverySqlMutationOfOperationalHistory() throws Exception {
        // A fresh container is the fixture; immutable history is never deleted for cleanup.
        try (var connection = Objects.requireNonNull(jdbc.getDataSource()).getConnection()) {
            assertEquals("MySQL", connection.getMetaData().getDatabaseProductName());
        }
        assertEquals(4, jdbc.queryForObject("select count(*) from information_schema.TRIGGERS where TRIGGER_SCHEMA = database() and TRIGGER_NAME in ('wms_transacoes_estoque_no_update','wms_transacoes_estoque_no_delete','wms_historico_tarefas_no_update','wms_historico_tarefas_no_delete')", Integer.class));
        String manager = token("mysql.first@example.com", "GESTOR");
        String secondManager = token("mysql.second@example.com", "GESTOR");
        String operator = token("mysql.operator@example.com", "OPERADOR");
        var warehouse = new Warehouse(); warehouse.setName("MySQL test warehouse"); warehouse = warehouses.saveAndFlush(warehouse);
        var zone = new Zone(); zone.setWarehouseId(warehouse.getId()); zone.setName("MySQL test zone"); zone = zones.saveAndFlush(zone);
        var source = new Position(); source.setZoneId(zone.getId()); source.setCode("MYSQL-STORAGE"); source = positions.saveAndFlush(source);
        var dock = new Position(); dock.setZoneId(zone.getId()); dock.setCode("MYSQL-DOCK"); dock.setPurpose("EXPEDICAO"); dock = positions.saveAndFlush(dock);
        var category = new Category(); category.setName("MySQL test category"); category = categories.saveAndFlush(category);
        var product = new Product(); product.setCategoryId(category.getId()); product.setSku("MYSQL-SKU"); product.setName("MySQL product");
        product.setBaseUnit("UN"); product.setLotControlled(false); product.setExpiryControlled(false);
        product.setUnitWeightKg(BigDecimal.ONE); product.setUnitVolumeM3(new BigDecimal("0.01")); product = products.saveAndFlush(product);
        var client = new Partner(); client.setLegalName("MySQL client"); client.setType("CLIENTE"); client = partners.saveAndFlush(client);
        var lpn = new Lpn(); lpn.setTrackingCode("MYSQL-INPUT-LPN"); lpn.setPositionId(source.getId()); lpn.setStatus("ARMAZENADO"); lpn = lpns.saveAndFlush(lpn);
        var stock = new Stock(); stock.setProductId(product.getId()); stock.setLpnId(lpn.getId()); stock.setPositionId(source.getId());
        stock.setQuantity(new BigDecimal("20")); stock.setEntryAt(Instant.parse("2026-01-01T00:00:00Z")); stock = stocks.saveAndFlush(stock);
        String order = created("/api/orders", Map.of("warehouseId", warehouse.getId(), "partnerId", client.getId(),
                "documentNumber", "MYSQL-SALE", "items", List.of(Map.of("productId", product.getId(), "quantity", 8))), manager).path("id").asText();
        mvc.perform(get("/api/search/orders").header("Authorization",manager).param("q","mysql-sale")
                .param("warehouseId",warehouse.getId()).param("documentNumber","MYSQL-SALE").param("productId",product.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(order));
        mvc.perform(get("/api/lookups/orders").header("Authorization",manager).param("q","mysql-sale").param("warehouseId",warehouse.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.size").value(5))
                .andExpect(jsonPath("$.data.content[0].id").value(order)).andExpect(jsonPath("$.data.content[0].label").isString())
                .andExpect(jsonPath("$.data.content[0].items").doesNotExist());
        var firstWave = Map.of("warehouseId", warehouse.getId(), "code", "MYSQL-WAVE-A", "orderIds", List.of(order), "idempotencyKey", UUID.randomUUID().toString());
        var secondWave = Map.of("warehouseId", warehouse.getId(), "code", "MYSQL-WAVE-B", "orderIds", List.of(order), "idempotencyKey", UUID.randomUUID().toString());
        var statuses = concurrently(firstWave, manager, secondWave, secondManager);
        assertEquals(1, statuses.stream().filter(code -> code == 201).count(), () -> "One wave must win: " + statuses);
        assertTrue(statuses.stream().allMatch(code -> code == 201 || code == 422 || code == 409), () -> "Unexpected concurrent status: " + statuses);
        var item = items.findByOrderId(order).getFirst();
        var reserved = reservations.findByOrderItemIdAndStatus(item.getId(), "ATIVA").stream()
                .map(reservation -> reservation.remainingQuantity()).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("8").compareTo(reserved));
        assertTrue(reserved.compareTo(stock.getQuantity()) <= 0);
        assertTrue(reserved.add(item.getPickedQuantity()).add(item.getFulfilledQuantity()).compareTo(item.getRequestedQuantity()) <= 0);
        var task = tasks.findByOrderId(order).getFirst();
        mvc.perform(post("/api/picking-tasks/" + task.getId() + "/start").header("Authorization", operator)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("idempotencyKey", UUID.randomUUID().toString()))))
                .andExpect(status().isOk());
        var picking = Map.of("actualQuantity", 8, "sourceLpnCode", lpn.getTrackingCode(), "sourcePositionCode", source.getCode(),
                "productCode", product.getSku(), "destinationLpnCode", "MYSQL-OUTPUT-LPN", "destinationPositionId", dock.getId(),
                "destinationPositionCode", dock.getCode(), "idempotencyKey", UUID.randomUUID().toString());
        mvc.perform(post("/api/picking-tasks/" + task.getId() + "/complete").header("Authorization", operator)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(picking))).andExpect(status().isOk());
        assertEquals(1, ledger.count());
        var entry = ledger.findAll().getFirst();
        var event = history.findByTaskIdOrderByRecordedAtAsc(task.getId()).getFirst();
        assertHistoryRejectsSql("UPDATE transacoes_estoque SET quantidade = 999 WHERE id = ?", entry.getId());
        assertHistoryRejectsSql("DELETE FROM transacoes_estoque WHERE id = ?", entry.getId());
        assertHistoryRejectsSql("UPDATE historico_tarefas SET motivo = 'tampered' WHERE id = ?", event.getId());
        assertHistoryRejectsSql("DELETE FROM historico_tarefas WHERE id = ?", event.getId());
        assertEquals(1, ledger.count());
        assertEquals(0, new BigDecimal("8").compareTo(ledger.findById(entry.getId()).orElseThrow().getQuantity()));
        assertEquals(3, history.findByTaskIdOrderByRecordedAtAsc(task.getId()).size());
    }

    private String token(String email, String profile) {
        var user = new User(profile, email, passwords.encode("mysql-test-password-123"));
        user.setProfile(profiles.findById(profile).orElseThrow()); user.setStatus(UserAccountStatus.ACTIVE); user.setEmailVerifiedAt(Instant.now());
        user = users.saveAndFlush(user);
        return "Bearer " + tokens.generateTokens(user.getUlid()).token();
    }

    private JsonNode created(String path, Object body, String token) throws Exception {
        var result = mvc.perform(post(path).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body))).andExpect(status().isCreated()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private List<Integer> concurrently(Object firstBody, String firstToken, Object secondBody, String secondToken) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> waveStatus(firstBody, firstToken, ready, start));
            var second = executor.submit(() -> waveStatus(secondBody, secondToken, ready, start));
            assertTrue(ready.await(5, SECONDS)); start.countDown();
            return List.of(first.get(30, SECONDS), second.get(30, SECONDS));
        } finally {
            start.countDown(); executor.shutdownNow(); assertTrue(executor.awaitTermination(5, SECONDS));
        }
    }

    private int waveStatus(Object body, String token, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown(); assertTrue(start.await(5, SECONDS));
        return mvc.perform(post("/api/waves").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body))).andReturn().getResponse().getStatus();
    }

    private void assertHistoryRejectsSql(String sql, String id) {
        var failure = assertThrows(DataAccessException.class, () -> jdbc.update(sql, id));
        Throwable cause = failure;
        while (cause != null && !(cause instanceof SQLException)) cause = cause.getCause();
        assertInstanceOf(SQLException.class, cause, "The Flyway trigger should reject the SQL statement");
        assertEquals("45000", ((SQLException) cause).getSQLState());
    }
}
