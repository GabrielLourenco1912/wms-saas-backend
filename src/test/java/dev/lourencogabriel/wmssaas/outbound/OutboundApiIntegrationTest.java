package dev.lourencogabriel.wmssaas.outbound;

import dev.lourencogabriel.wmssaas.audit.repository.StockTransactionRepository;
import dev.lourencogabriel.wmssaas.auth.service.TokenService;
import dev.lourencogabriel.wmssaas.order.repository.OrderItemRepository;
import dev.lourencogabriel.wmssaas.order.repository.WarehouseOrderRepository;
import dev.lourencogabriel.wmssaas.order.model.WarehouseOrder;
import dev.lourencogabriel.wmssaas.order.model.OrderItem;
import dev.lourencogabriel.wmssaas.outbound.repository.WaveRepository;
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
import dev.lourencogabriel.wmssaas.task.model.WarehouseTask;
import dev.lourencogabriel.wmssaas.task.repository.OperationalDivergenceRepository;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Each HTTP request commits or rolls back its own real service transaction. No route probes. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:wms-outbound-api;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class OutboundApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
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
    @Autowired WarehouseOrderRepository orders;
    @Autowired OrderItemRepository items;
    @Autowired WaveRepository waves;
    @Autowired WarehouseTaskRepository tasks;
    @Autowired StockReservationRepository reservations;
    @Autowired OperationalDivergenceRepository divergences;
    @Autowired TaskHistoryRepository history;
    @Autowired StockTransactionRepository ledger;
    @Autowired JdbcTemplate jdbc;
    String managerToken;
    String operatorToken;
    String managerId;
    Warehouse warehouse;
    Zone zone;
    Position sourcePosition;
    Position alternatePosition;
    Position dock;
    Category category;
    Product product;
    Partner client;

    @BeforeEach
    void setUp() throws Exception {
        // This cleaner is restricted to this test's dedicated in-memory H2 database.
        try (var connection = Objects.requireNonNull(jdbc.getDataSource()).getConnection()) {
            assertTrue(connection.getMetaData().getURL().startsWith("jdbc:h2:mem:wms-outbound-api"));
        }
        var tables = jdbc.queryForList("select table_name from information_schema.tables where table_schema = 'PUBLIC' and table_type = 'BASE TABLE'", String.class);
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        try {
            for (String table : tables) {
                if (Set.of("access_profiles", "profile_permissions", "flyway_schema_history").contains(table.toLowerCase(Locale.ROOT))) continue;
                if (!table.matches("[A-Za-z_][A-Za-z_0-9]*")) throw new IllegalStateException("Unexpected H2 table name");
                jdbc.execute("DELETE FROM \"" + table + "\"");
            }
        } finally {
            jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }
        var manager = actor("outbound.manager@example.com", "GESTOR");
        managerId = manager.getUlid();
        managerToken = "Bearer " + tokens.generateTokens(managerId).token();
        operatorToken = "Bearer " + tokens.generateTokens(actor("outbound.operator@example.com", "OPERADOR").getUlid()).token();
        warehouse = new Warehouse(); warehouse.setName("Outbound test warehouse"); warehouse = warehouses.saveAndFlush(warehouse);
        zone = new Zone(); zone.setWarehouseId(warehouse.getId()); zone.setName("Test zone"); zone = zones.saveAndFlush(zone);
        sourcePosition = position("STORAGE-A", "ARMAZENAGEM");
        alternatePosition = position("STORAGE-B", "ARMAZENAGEM");
        dock = position("DOCK-OUT", "EXPEDICAO");
        category = new Category(); category.setName("Test products"); category = categories.saveAndFlush(category);
        product = product("SKU-TEST");
        client = new Partner(); client.setLegalName("Test client"); client.setType("CLIENTE"); client = partners.saveAndFlush(client);
    }

    @Test
    void shouldCreateListReadAndEditDocumentaryOutboundOrders() throws Exception {
        var source = stock(product, sourcePosition, "10", null, "2026-01-01T00:00:00Z");
        String order = order("3");
        getJson("/api/orders/" + order, managerToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("SAIDA"))
                .andExpect(jsonPath("$.data.items[0].requestedQuantity").value(3));
        getJson("/api/orders?warehouseId=" + warehouse.getId(), managerToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
        var update = orderBody(product, "4"); update.put("documentNumber", "EDITED-DOCUMENT");
        putJson("/api/orders/" + order, update, managerToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documentNumber").value("EDITED-DOCUMENT"))
                .andExpect(jsonPath("$.data.items[0].requestedQuantity").value(4));
        equalQuantity("10", stocks.findById(source.getId()).orElseThrow().getQuantity());
        assertEquals(0, ledger.count());
    }

    @Test
    void shouldSearchOrdersInTheDatabaseWithStablePagesAndCompactLookupOptions() throws Exception {
        for (int index = 0; index < 21; index++) searchableOrder("SALE-" + String.format("%02d", index));
        String literal = searchableOrder("SALE-%-LITERAL");
        searchableOrder("SALE-X-LITERAL");
        mvc.perform(get("/api/orders").header("Authorization", managerToken).param("warehouseId", warehouse.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.content.length()").value(20)).andExpect(jsonPath("$.data.totalElements").value(23));
        var first = data(mvc.perform(get("/api/search/orders").header("Authorization", managerToken)
                .param("q", "sale-").param("size", "7").param("warehouseId", warehouse.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content.length()").value(7)));
        var second = data(mvc.perform(get("/api/search/orders").header("Authorization", managerToken)
                .param("q", "SALE-").param("size", "7").param("page", "1").param("warehouseId", warehouse.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content.length()").value(7)));
        var firstIds = new ArrayList<String>(); first.path("content").forEach(item -> firstIds.add(item.path("id").asText()));
        var secondIds = new ArrayList<String>(); second.path("content").forEach(item -> secondIds.add(item.path("id").asText()));
        assertEquals(firstIds.stream().sorted().toList(), firstIds);
        assertTrue(Collections.disjoint(firstIds, secondIds));
        mvc.perform(get("/api/search/orders").header("Authorization", managerToken).param("q", "%")
                .param("warehouseId", warehouse.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1)).andExpect(jsonPath("$.data.content[0].id").value(literal));
        mvc.perform(get("/api/search/orders").header("Authorization", managerToken).param("documentNumber", "SALE-03")
                .param("partnerId", client.getId()).param("productId", product.getId()).param("status", "PENDENTE")
                .param("warehouseId", warehouse.getId())).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(get("/api/lookups/orders").header("Authorization", managerToken).param("q", "sale-")
                .param("warehouseId", warehouse.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(5)).andExpect(jsonPath("$.data.content.length()").value(5))
                .andExpect(jsonPath("$.data.content[0].id").isString()).andExpect(jsonPath("$.data.content[0].label").isString())
                .andExpect(jsonPath("$.data.content[0].items").doesNotExist());
        mvc.perform(get("/api/lookups/orders").header("Authorization", managerToken).param("size", "6"))
                .andExpect(status().isBadRequest());
        getJson("/api/lookups/orders", operatorToken).andExpect(status().isForbidden());
    }

    @Test
    void shouldAllocateFefoThenFifoAndReserveOnlyResidualDemand() throws Exception {
        var earlierEntry = stock(product, sourcePosition, "2", "2026-11-01", "2026-01-01T00:00:00Z");
        var laterEntry = stock(product, alternatePosition, "3", "2026-11-01", "2026-02-01T00:00:00Z");
        var laterExpiry = stock(product, alternatePosition, "10", "2026-12-01", "2025-01-01T00:00:00Z");
        var blocked = stock(product, sourcePosition, "100", "2026-10-20", "2024-01-01T00:00:00Z");
        blocked.setBlockStatus("AVARIA"); stocks.saveAndFlush(blocked);
        var fifoProduct = product("SKU-FIFO");
        var fifoEarlier = stock(fifoProduct, sourcePosition, "2", null, "2026-01-01T00:00:00Z");
        var fifoLater = stock(fifoProduct, alternatePosition, "3", null, "2026-02-01T00:00:00Z");
        var body = orderBody(product, "4");
        body.put("items", List.of(Map.of("productId", product.getId(), "quantity", new BigDecimal("4")),
                Map.of("productId", fifoProduct.getId(), "quantity", new BigDecimal("3"))));
        String order = created(postJson("/api/orders", body, managerToken)).path("id").asText();
        String wave = wave(List.of(order));
        var byStock = tasks.findByWaveId(wave).stream().collect(Collectors.toMap(WarehouseTask::getSourceStockId, WarehouseTask::getTargetQuantity));
        equalQuantity("2", byStock.get(earlierEntry.getId()));
        equalQuantity("2", byStock.get(laterEntry.getId()));
        equalQuantity("2", byStock.get(fifoEarlier.getId()));
        equalQuantity("1", byStock.get(fifoLater.getId()));
        assertFalse(byStock.containsKey(blocked.getId()));
        assertFalse(byStock.containsKey(laterExpiry.getId()));
        assertEquals(4, reservations.count());
        getJson("/api/waves/" + wave, managerToken).andExpect(status().isOk()).andExpect(jsonPath("$.data.tasks.length()").value(4));
        getJson("/api/waves?status=PENDENTE", managerToken).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(get("/api/search/waves").header("Authorization", managerToken).param("q", waves.findById(wave).orElseThrow().getCode().toLowerCase(Locale.ROOT))
                .param("warehouseId", warehouse.getId()).param("orderId", order)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(get("/api/lookups/picking-tasks").header("Authorization", operatorToken)
                .param("warehouseId", warehouse.getId()).param("productId", product.getId()).param("waveId", wave))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2));
        postJson("/api/waves", waveBody(List.of(order)), managerToken).andExpect(status().isUnprocessableContent());
        assertEquals(1, waves.count());
        assertEquals(4, tasks.count());
    }

    @Test
    void shouldMovePickingInternallyAndDepleteStockExactlyOnceAtDispatch() throws Exception {
        var source = stock(product, sourcePosition, "10", null, "2026-01-01T00:00:00Z");
        String order = order("5");
        var task = onlyTask(wave(List.of(order)));
        start(task);
        var picking = pickingBody(task, "5", "OUTBOUND-LPN");
        data(postJson("/api/picking-tasks/" + task.getId() + "/complete", picking, operatorToken).andExpect(status().isOk()));
        data(postJson("/api/picking-tasks/" + task.getId() + "/complete", picking, operatorToken).andExpect(status().isOk()));
        equalQuantity("10", physical());
        equalQuantity("5", items.findByOrderId(order).getFirst().getPickedQuantity());
        equalQuantity("0", items.findByOrderId(order).getFirst().getFulfilledQuantity());
        assertEquals(1, ledger.count());
        var outboundLpn = lpns.findByTrackingCode("OUTBOUND-LPN").orElseThrow();
        String shipment = prepare(order, outboundLpn);
        postJson("/api/shipments/" + shipment + "/dispatch", dispatchBody(), operatorToken).andExpect(status().isUnprocessableContent());
        postJson("/api/shipments/" + shipment + "/confirm", Map.of("lpnCode", "WRONG-LPN", "dockPositionCode", dock.getCode(), "idempotencyKey", key()), operatorToken)
                .andExpect(status().isUnprocessableContent());
        confirm(shipment, outboundLpn);
        var dispatch = dispatchBody();
        postJson("/api/shipments/" + shipment + "/dispatch", dispatch, operatorToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DESPACHADA"));
        postJson("/api/shipments/" + shipment + "/dispatch", dispatch, operatorToken).andExpect(status().isOk());
        postJson("/api/shipments/" + shipment + "/dispatch", dispatchBody(), operatorToken).andExpect(status().isUnprocessableContent());
        equalQuantity("5", physical());
        equalQuantity("5", stocks.findById(source.getId()).orElseThrow().getQuantity());
        var item = items.findByOrderId(order).getFirst();
        equalQuantity("5", item.getFulfilledQuantity()); equalQuantity("0", item.getPickedQuantity());
        assertEquals("ATENDIDO", orders.findById(order).orElseThrow().getStatus());
        assertEquals(2, ledger.count());
        var dispatched = lpns.findById(outboundLpn.getId()).orElseThrow();
        assertNull(dispatched.getPositionId()); assertEquals("EXPEDIDO", dispatched.getStatus());
        getJson("/api/shipments/" + shipment, operatorToken).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].status").value("EXPEDIDO"));
        getJson("/api/shipments?orderId=" + order, operatorToken).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(get("/api/lookups/shipments").header("Authorization", operatorToken).param("orderId", order)
                .param("warehouseId", warehouse.getId()).param("dockPositionId", dock.getId()).param("status", "DESPACHADA"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].id").value(shipment));
    }

    @Test
    void shouldRecordPartialShortageBlockTheResidualAndReallocateAtAnotherPosition() throws Exception {
        var source = stock(product, sourcePosition, "5", "2026-11-01", "2026-01-01T00:00:00Z");
        var samePosition = stock(product, sourcePosition, "7", "2026-11-02", "2026-01-01T00:00:00Z");
        var alternative = stock(product, alternatePosition, "10", "2026-11-03", "2026-01-01T00:00:00Z");
        String order = order("5");
        var task = onlyTask(wave(List.of(order)));
        start(task);
        var request = pickingBody(task, "2", "SHORTAGE-OUT"); request.put("reason", "Falta física confirmada na coleta");
        var response = data(postJson("/api/picking-tasks/" + task.getId() + "/divergences", request, operatorToken)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.task.status").value("DIVERGENTE"))
                .andExpect(jsonPath("$.data.unallocatedQuantity").value(0)));
        postJson("/api/picking-tasks/" + task.getId() + "/divergences", request, operatorToken).andExpect(status().isOk());
        var successors = tasks.findByOriginTaskId(task.getId());
        assertEquals(1, successors.size()); assertEquals(alternative.getId(), successors.getFirst().getSourceStockId());
        assertNotEquals(samePosition.getId(), successors.getFirst().getSourceStockId());
        equalQuantity("3", successors.getFirst().getTargetQuantity());
        var originalReservation = reservations.findByTaskId(task.getId()).orElseThrow();
        equalQuantity("2", originalReservation.getConsumedQuantity()); equalQuantity("3", originalReservation.getReleasedQuantity());
        assertEquals("ENCERRADA", originalReservation.getStatus());
        assertNotNull(stocks.findById(source.getId()).orElseThrow().getBlockStatus());
        equalQuantity("3", stocks.findById(source.getId()).orElseThrow().getQuantity());
        equalQuantity("22", physical());
        assertEquals(1, divergences.count()); assertEquals(1, ledger.count());
        String divergenceId = response.path("divergence").path("id").asText();
        getJson("/api/divergences/" + divergenceId, operatorToken).andExpect(status().isOk()).andExpect(jsonPath("$.data.type").value("FALTA"));
        getJson("/api/divergences?status=ABERTA", operatorToken).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(get("/api/search/divergences").header("Authorization", operatorToken).param("q", "falta física")
                .param("warehouseId", warehouse.getId()).param("taskId", task.getId()).param("stockId", source.getId()).param("type", "FALTA"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].id").value(divergenceId));
    }

    @Test
    void shouldKeepAnUnfulfilledOrderWhenNoAlternateStockExists() throws Exception {
        var source = stock(product, sourcePosition, "5", null, "2026-01-01T00:00:00Z");
        String order = order("5");
        var task = onlyTask(wave(List.of(order))); start(task);
        var request = pickingBody(task, "0", "UNUSED-LPN"); request.put("reason", "Endereço sem quantidade física");
        postJson("/api/picking-tasks/" + task.getId() + "/divergences", request, operatorToken)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.unallocatedQuantity").value(5))
                .andExpect(jsonPath("$.data.replacementTasks.length()").value(0));
        assertNotEquals("ATENDIDO", orders.findById(order).orElseThrow().getStatus());
        equalQuantity("5", stocks.findById(source.getId()).orElseThrow().getQuantity());
        assertTrue(lpns.findByTrackingCode("UNUSED-LPN").isEmpty());
        assertEquals(0, ledger.count());
    }

    @Test
    void shouldCancelOnlyTheSelectedOrdersPendingTasksAndReleaseWaveReservations() throws Exception {
        stock(product, sourcePosition, "20", null, "2026-01-01T00:00:00Z");
        String first = order("5"), second = order("5");
        String wave = wave(List.of(first, second));
        var firstTask = tasks.findByOrderId(first).getFirst();
        var secondTask = tasks.findByOrderId(second).getFirst();
        postJson("/api/orders/" + first + "/cancel", cancelBody(), managerToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELADO"));
        assertEquals("CANCELADA", tasks.findById(firstTask.getId()).orElseThrow().getStatus());
        assertEquals("PENDENTE", tasks.findById(secondTask.getId()).orElseThrow().getStatus());
        var cancellation = cancelBody();
        postJson("/api/waves/" + wave + "/cancel", cancellation, managerToken).andExpect(status().isOk());
        postJson("/api/waves/" + wave + "/cancel", cancellation, managerToken).andExpect(status().isOk());
        assertEquals("CANCELADA", tasks.findById(secondTask.getId()).orElseThrow().getStatus());
        assertEquals("PENDENTE", orders.findById(second).orElseThrow().getStatus());
        assertTrue(reservations.findAll().stream().allMatch(reservation -> reservation.remainingQuantity().signum() == 0));
        equalQuantity("20", physical()); assertEquals(0, ledger.count()); assertEquals(2, tasks.count());
        assertEquals(2, history.findByTaskIdOrderByRecordedAtAsc(firstTask.getId()).size());
    }

    @Test
    void shouldEnforcePermissionsScansQuantitiesAndRunningTaskCancellationRules() throws Exception {
        stock(product, sourcePosition, "10", null, "2026-01-01T00:00:00Z");
        String order = order("5"); var task = onlyTask(wave(List.of(order)));
        postJson("/api/orders", orderBody(product, "1"), operatorToken).andExpect(status().isForbidden());
        postJson("/api/picking-tasks/" + task.getId() + "/start", Map.of("idempotencyKey", key()), managerToken).andExpect(status().isForbidden());
        postJson("/api/tasks/" + task.getId() + "/authorize-excess", Map.of("newTargetQuantity", 6, "reason", "Excesso", "idempotencyKey", key()), operatorToken)
                .andExpect(status().isForbidden());
        start(task);
        var wrongCode = pickingBody(task, "5", "OUTPUT"); wrongCode.put("productCode", "WRONG-SKU");
        postJson("/api/picking-tasks/" + task.getId() + "/complete", wrongCode, operatorToken).andExpect(status().isUnprocessableContent());
        var missingQuantity = pickingBody(task, "5", "OUTPUT"); missingQuantity.remove("actualQuantity");
        postJson("/api/picking-tasks/" + task.getId() + "/complete", missingQuantity, operatorToken).andExpect(status().isBadRequest());
        postJson("/api/picking-tasks/" + task.getId() + "/complete", pickingBody(task, "6", "OUTPUT"), operatorToken).andExpect(status().isUnprocessableContent());
        postJson("/api/orders/" + order + "/cancel", cancelBody(), managerToken).andExpect(status().isUnprocessableContent());
        postJson("/api/waves/" + task.getWaveId() + "/cancel", cancelBody(), managerToken).andExpect(status().isUnprocessableContent());
        assertEquals("EM_EXECUCAO", tasks.findById(task.getId()).orElseThrow().getStatus());
        equalQuantity("10", physical()); assertEquals(0, ledger.count());
    }

    @Test
    void shouldAuditAnAuthorizedExcessAndExtendOnlyTheAvailableReservation() throws Exception {
        var source = stock(product, sourcePosition, "2", null, "2026-01-01T00:00:00Z");
        String order = order("5"); var task = onlyTask(wave(List.of(order))); start(task);
        // Controlled fixture: availability has increased after allocation, as after an independent accepted adjustment.
        source = stocks.findById(source.getId()).orElseThrow(); source.setQuantity(new BigDecimal("4")); stocks.saveAndFlush(source);
        postJson("/api/picking-tasks/" + task.getId() + "/complete", pickingBody(task, "4", "AUTHORIZED-OUT"), operatorToken)
                .andExpect(status().isUnprocessableContent());
        var authorization = Map.of("newTargetQuantity", 4, "reason", "Saldo adicional conferido pelo gestor", "idempotencyKey", key());
        postJson("/api/tasks/" + task.getId() + "/authorize-excess", authorization, managerToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetQuantity").value(4));
        postJson("/api/tasks/" + task.getId() + "/authorize-excess", authorization, managerToken).andExpect(status().isOk());
        var divergence = divergences.findByTaskId(task.getId()).getFirst();
        assertEquals(managerId, divergence.getAuthorizedById()); assertEquals("EXCESSO", divergence.getType());
        equalQuantity("2", divergence.getExpectedQuantity()); equalQuantity("4", divergence.getActualQuantity());
        postJson("/api/picking-tasks/" + task.getId() + "/complete", pickingBody(task, "4", "AUTHORIZED-OUT"), operatorToken).andExpect(status().isOk());
        equalQuantity("4", reservations.findByTaskId(task.getId()).orElseThrow().getConsumedQuantity());
        equalQuantity("4", physical()); equalQuantity("4", items.findByOrderId(order).getFirst().getPickedQuantity());
        assertNotEquals("ATENDIDO", orders.findById(order).orElseThrow().getStatus());
    }

    @Test
    void shouldRollBackPickingAndNewLpnWhenDestinationCapacityFails() throws Exception {
        stock(product, sourcePosition, "6", null, "2026-01-01T00:00:00Z");
        dock.setWeightCapacityKg(new BigDecimal("5")); positions.saveAndFlush(dock);
        var task = onlyTask(wave(List.of(order("6")))); start(task);
        postJson("/api/picking-tasks/" + task.getId() + "/complete", pickingBody(task, "6", "ROLLBACK-LPN"), operatorToken)
                .andExpect(status().isUnprocessableContent());
        assertTrue(lpns.findByTrackingCode("ROLLBACK-LPN").isEmpty());
        equalQuantity("6", physical()); assertEquals(0, ledger.count());
        assertEquals("EM_EXECUCAO", tasks.findById(task.getId()).orElseThrow().getStatus());
        assertEquals("ATIVA", reservations.findByTaskId(task.getId()).orElseThrow().getStatus());
        assertNull(tasks.findById(task.getId()).orElseThrow().getActualQuantity());
    }

    @Test
    void shouldRequireEqualityForEveryOrderItemAndAllowCancellationWithoutAStockDepletion() throws Exception {
        var secondProduct = product("SKU-SECOND");
        stock(product, sourcePosition, "2", null, "2026-01-01T00:00:00Z");
        stock(secondProduct, alternatePosition, "3", null, "2026-01-01T00:00:00Z");
        var body = orderBody(product, "2");
        body.put("items", List.of(Map.of("productId", product.getId(), "quantity", 2), Map.of("productId", secondProduct.getId(), "quantity", 3)));
        String order = created(postJson("/api/orders", body, managerToken)).path("id").asText();
        var generated = tasks.findByWaveId(wave(List.of(order)));
        for (int index = 0; index < generated.size(); index++) {
            var task = generated.get(index); start(task);
            String code = "PARTIAL-OUT-" + index;
            postJson("/api/picking-tasks/" + task.getId() + "/complete", pickingBody(task, task.getTargetQuantity().toPlainString(), code), operatorToken).andExpect(status().isOk());
            var lpn = lpns.findByTrackingCode(code).orElseThrow();
            String shipment = prepare(order, lpn);
            if (index == 0) {
                postJson("/api/shipments/" + shipment + "/cancel", cancelBody(), managerToken).andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.status").value("CANCELADA"));
                equalQuantity("5", physical());
                shipment = prepare(order, lpn);
            }
            confirm(shipment, lpn);
            postJson("/api/shipments/" + shipment + "/dispatch", dispatchBody(), operatorToken).andExpect(status().isOk());
            assertEquals(index == 0 ? "PARCIAL" : "ATENDIDO", orders.findById(order).orElseThrow().getStatus());
        }
        equalQuantity("0", physical());
        assertTrue(items.findByOrderId(order).stream().allMatch(item -> item.getFulfilledQuantity().compareTo(item.getRequestedQuantity()) == 0));
    }

    @Test
    void shouldSerializeDifferentActorsCompetingForTheSamePhysicalStock() throws Exception {
        var source = stock(product, sourcePosition, "8", null, "2026-01-01T00:00:00Z");
        String firstOrder = order("8"), secondOrder = order("8");
        String anotherManager = "Bearer " + tokens.generateTokens(actor("second.manager@example.com", "GESTOR").getUlid()).token();
        var statuses = concurrentWaves(waveBody(List.of(firstOrder)), managerToken,
                waveBody(List.of(secondOrder)), anotherManager);
        assertOneWinner(statuses);
        var allocated = reservations.findByStockIdAndStatus(source.getId(), "ATIVA").stream()
                .map(reservation -> reservation.remainingQuantity()).reduce(BigDecimal.ZERO, BigDecimal::add);
        equalQuantity("8", allocated);
        assertTrue(allocated.compareTo(stocks.findById(source.getId()).orElseThrow().getQuantity()) <= 0);
        assertEquals(1, waves.count());
        for (String orderId : List.of(firstOrder, secondOrder)) {
            var item = items.findByOrderId(orderId).getFirst();
            var demandAllocated = reservations.findByOrderItemIdAndStatus(item.getId(), "ATIVA").stream()
                    .map(reservation -> reservation.remainingQuantity()).reduce(BigDecimal.ZERO, BigDecimal::add);
            assertTrue(demandAllocated.compareTo(item.getRequestedQuantity()) <= 0);
        }
    }

    @Test
    void shouldSerializeDifferentActorsCompetingForTheSameOrderItemDemand() throws Exception {
        stock(product, sourcePosition, "20", null, "2026-01-01T00:00:00Z");
        String order = order("8");
        String anotherManager = "Bearer " + tokens.generateTokens(actor("second.manager@example.com", "GESTOR").getUlid()).token();
        var statuses = concurrentWaves(waveBody(List.of(order)), managerToken,
                waveBody(List.of(order)), anotherManager);
        assertOneWinner(statuses);
        var item = items.findByOrderId(order).getFirst();
        var allocated = reservations.findByOrderItemIdAndStatus(item.getId(), "ATIVA").stream()
                .map(reservation -> reservation.remainingQuantity()).reduce(BigDecimal.ZERO, BigDecimal::add);
        equalQuantity("8", allocated);
        assertTrue(allocated.add(item.getPickedQuantity()).add(item.getFulfilledQuantity()).compareTo(item.getRequestedQuantity()) <= 0);
        equalQuantity("20", physical());
        assertEquals(1, waves.count()); assertEquals(1, tasks.findByOrderId(order).size());
    }

    private List<Integer> concurrentWaves(Map<String, Object> firstBody, String firstToken,
                                           Map<String, Object> secondBody, String secondToken) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> {
                ready.countDown(); assertTrue(start.await(5, SECONDS), "The concurrent wave start was not released");
                return postJson("/api/waves", firstBody, firstToken).andReturn().getResponse().getStatus();
            });
            var second = executor.submit(() -> {
                ready.countDown(); assertTrue(start.await(5, SECONDS), "The concurrent wave start was not released");
                return postJson("/api/waves", secondBody, secondToken).andReturn().getResponse().getStatus();
            });
            assertTrue(ready.await(5, SECONDS), "Both wave workers must be ready");
            start.countDown();
            return List.of(first.get(20, SECONDS), second.get(20, SECONDS));
        } finally {
            start.countDown(); executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, SECONDS), "Wave workers did not terminate");
        }
    }

    private void assertOneWinner(List<Integer> statuses) {
        assertEquals(1, statuses.stream().filter(status -> status == 201).count(), () -> "Expected one created wave: " + statuses);
        assertTrue(statuses.stream().allMatch(status -> status == 201 || status == 422 || status == 409),
                () -> "Contention must produce creation or an expected business conflict, never a server error: " + statuses);
    }

    private User actor(String email, String profile) {
        var user = new User(profile, email, passwords.encode("outbound-password-123"));
        user.setProfile(profiles.findById(profile).orElseThrow()); user.setStatus(UserAccountStatus.ACTIVE);
        user.setEmailVerifiedAt(Instant.now()); return users.saveAndFlush(user);
    }

    private String searchableOrder(String documentNumber) {
        var order = new WarehouseOrder(); order.setWarehouseId(warehouse.getId()); order.setPartnerId(client.getId());
        order.setDocumentNumber(documentNumber); order.setType("SAIDA"); order.setStatus("PENDENTE"); order.setCreatedById(managerId);
        order = orders.saveAndFlush(order);
        var item = new OrderItem(); item.setOrderId(order.getId()); item.setProductId(product.getId()); item.setRequestedQuantity(BigDecimal.ONE);
        items.saveAndFlush(item);
        return order.getId();
    }

    private Position position(String code, String purpose) {
        var position = new Position(); position.setZoneId(zone.getId()); position.setCode(code); position.setPurpose(purpose);
        return positions.saveAndFlush(position);
    }

    private Product product(String sku) {
        var product = new Product(); product.setCategoryId(category.getId()); product.setSku(sku); product.setName(sku);
        product.setLotControlled(false); product.setExpiryControlled(false); product.setBaseUnit("UN");
        product.setUnitWeightKg(BigDecimal.ONE); product.setUnitVolumeM3(new BigDecimal("0.01"));
        return products.saveAndFlush(product);
    }

    private Stock stock(Product product, Position position, String quantity, String expiry, String entry) {
        var lpn = new Lpn(); lpn.setTrackingCode("INPUT-" + key()); lpn.setPositionId(position.getId()); lpn.setStatus("ARMAZENADO"); lpn = lpns.saveAndFlush(lpn);
        var stock = new Stock(); stock.setProductId(product.getId()); stock.setPositionId(position.getId()); stock.setLpnId(lpn.getId());
        stock.setQuantity(new BigDecimal(quantity)); stock.setExpiryDate(expiry == null ? null : LocalDate.parse(expiry)); stock.setEntryAt(Instant.parse(entry));
        return stocks.saveAndFlush(stock);
    }

    private Map<String, Object> orderBody(Product product, String quantity) {
        return new LinkedHashMap<>(Map.of("warehouseId", warehouse.getId(), "partnerId", client.getId(),
                "documentNumber", "SALE-" + key(), "items", List.of(Map.of("productId", product.getId(), "quantity", new BigDecimal(quantity)))));
    }

    private String order(String quantity) throws Exception { return created(postJson("/api/orders", orderBody(product, quantity), managerToken)).path("id").asText(); }

    private Map<String, Object> waveBody(List<String> orderIds) {
        return Map.of("warehouseId", warehouse.getId(), "code", "WAVE-" + key(), "orderIds", orderIds, "priority", 1, "idempotencyKey", key());
    }

    private String wave(List<String> orderIds) throws Exception { return created(postJson("/api/waves", waveBody(orderIds), managerToken)).path("id").asText(); }

    private WarehouseTask onlyTask(String waveId) { var found = tasks.findByWaveId(waveId); assertEquals(1, found.size()); return found.getFirst(); }

    private void start(WarehouseTask task) throws Exception {
        postJson("/api/picking-tasks/" + task.getId() + "/start", Map.of("idempotencyKey", key()), operatorToken).andExpect(status().isOk());
        getJson("/api/picking-tasks/" + task.getId(), operatorToken).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("EM_EXECUCAO"));
        getJson("/api/picking-tasks?orderId=" + task.getOrderId(), operatorToken).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").isNumber());
    }

    private Map<String, Object> pickingBody(WarehouseTask task, String quantity, String destinationCode) {
        var source = stocks.findById(task.getSourceStockId()).orElseThrow();
        var sourceLpn = lpns.findById(source.getLpnId()).orElseThrow();
        var position = positions.findById(source.getPositionId()).orElseThrow();
        var product = products.findById(source.getProductId()).orElseThrow();
        return new LinkedHashMap<>(Map.of("actualQuantity", new BigDecimal(quantity), "sourceLpnCode", sourceLpn.getTrackingCode(),
                "sourcePositionCode", position.getCode(), "productCode", product.getSku(), "destinationLpnCode", destinationCode,
                "destinationPositionId", dock.getId(), "destinationPositionCode", dock.getCode(), "idempotencyKey", key()));
    }

    private String prepare(String orderId, Lpn lpn) throws Exception {
        return created(postJson("/api/shipments", Map.of("orderId", orderId, "dockPositionId", dock.getId(), "lpnIds", List.of(lpn.getId()), "idempotencyKey", key()), operatorToken)).path("id").asText();
    }

    private void confirm(String shipment, Lpn lpn) throws Exception {
        postJson("/api/shipments/" + shipment + "/confirm", Map.of("lpnCode", lpn.getTrackingCode(), "dockPositionCode", dock.getCode(), "idempotencyKey", key()), operatorToken)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CONFERIDA"));
    }

    private Map<String, Object> dispatchBody() { return Map.of("dockPositionCode", dock.getCode(), "idempotencyKey", key()); }
    private Map<String, Object> cancelBody() { return Map.of("reason", "Cancelamento solicitado pelo gestor", "idempotencyKey", key()); }
    private String key() { return UUID.randomUUID().toString(); }
    private BigDecimal physical() { return stocks.findAll().stream().map(Stock::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add); }
    private void equalQuantity(String expected, BigDecimal actual) { assertNotNull(actual); assertEquals(0, new BigDecimal(expected).compareTo(actual)); }
    private ResultActions getJson(String path, String token) throws Exception { return mvc.perform(get(path).header("Authorization", token)); }
    private ResultActions postJson(String path, Object body, String token) throws Exception { return mvc.perform(post(path).header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body))); }
    private ResultActions putJson(String path, Object body, String token) throws Exception { return mvc.perform(put(path).header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body))); }
    private JsonNode data(ResultActions response) throws Exception { return mapper.readTree(response.andReturn().getResponse().getContentAsString()).path("data"); }
    private JsonNode created(ResultActions response) throws Exception { return data(response.andExpect(status().isCreated()).andExpect(jsonPath("$.data.id").isString())); }
}
