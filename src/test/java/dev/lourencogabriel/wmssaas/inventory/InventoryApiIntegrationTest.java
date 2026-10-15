package dev.lourencogabriel.wmssaas.inventory;

import dev.lourencogabriel.wmssaas.audit.repository.StockTransactionRepository;
import dev.lourencogabriel.wmssaas.auth.service.TokenService;
import dev.lourencogabriel.wmssaas.inventory.repository.InventoryRepository;
import dev.lourencogabriel.wmssaas.order.model.OrderItem;
import dev.lourencogabriel.wmssaas.order.model.WarehouseOrder;
import dev.lourencogabriel.wmssaas.order.repository.OrderItemRepository;
import dev.lourencogabriel.wmssaas.order.repository.WarehouseOrderRepository;
import dev.lourencogabriel.wmssaas.partner.model.Partner;
import dev.lourencogabriel.wmssaas.partner.repository.PartnerRepository;
import dev.lourencogabriel.wmssaas.product.model.*;
import dev.lourencogabriel.wmssaas.product.repository.*;
import dev.lourencogabriel.wmssaas.shared.model.UserAccountStatus;
import dev.lourencogabriel.wmssaas.stock.model.*;
import dev.lourencogabriel.wmssaas.stock.repository.*;
import dev.lourencogabriel.wmssaas.stock.service.StockMutationService;
import dev.lourencogabriel.wmssaas.task.model.WarehouseTask;
import dev.lourencogabriel.wmssaas.task.repository.WarehouseTaskRepository;
import dev.lourencogabriel.wmssaas.user.model.User;
import dev.lourencogabriel.wmssaas.user.repository.*;
import dev.lourencogabriel.wmssaas.warehouse.model.*;
import dev.lourencogabriel.wmssaas.warehouse.repository.*;
import dev.lourencogabriel.wmssaas.web.exception.BusinessRuleException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:inventory-api;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class InventoryApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired AccessProfileRepository profiles;
    @Autowired TokenService tokens;
    @Autowired WarehouseRepository warehouses;
    @Autowired ZoneRepository zones;
    @Autowired PositionRepository positions;
    @Autowired ProductRepository products;
    @Autowired CategoryRepository categories;
    @Autowired LpnRepository lpns;
    @Autowired StockRepository stocks;
    @Autowired WarehouseTaskRepository tasks;
    @Autowired StockTransactionRepository transactions;
    @Autowired InventoryRepository inventories;
    @Autowired StockMutationService mutation;
    @Autowired PartnerRepository partners;
    @Autowired WarehouseOrderRepository orders;
    @Autowired OrderItemRepository orderItems;
    @Autowired StockReservationRepository reservations;
    String operator;
    String manager;
    String otherOperator;
    String operatorId;
    Position position;
    Product product;
    Stock stock;
    Lpn lpn;

    @BeforeEach
    void prepare() {
        User op = user("operator@inventory.test", "OPERADOR"); operatorId = op.getUlid(); operator = bearer(op);
        manager = bearer(user("manager@inventory.test", "GESTOR")); otherOperator = bearer(user("other@inventory.test", "OPERADOR"));
        Warehouse warehouse = new Warehouse(); warehouse.setName("Principal"); warehouses.saveAndFlush(warehouse);
        Zone zone = new Zone(); zone.setWarehouseId(warehouse.getId()); zone.setName("A"); zones.saveAndFlush(zone);
        position = new Position(); position.setZoneId(zone.getId()); position.setCode("A-01");
        position.setWeightCapacityKg(new BigDecimal("100")); position.setVolumeCapacityM3(new BigDecimal("100")); positions.saveAndFlush(position);
        Category category = new Category(); category.setName("Geral"); categories.saveAndFlush(category);
        product = new Product(); product.setCategoryId(category.getId()); product.setSku("SKU-INV"); product.setName("Produto");
        product.setLotControlled(true); product.setExpiryControlled(true); product.setUnitWeightKg(BigDecimal.ONE);
        product.setUnitVolumeM3(BigDecimal.ONE); products.saveAndFlush(product);
        lpn = new Lpn(); lpn.setTrackingCode("LPN-INV"); lpn.setPositionId(position.getId()); lpn.setStatus("ATIVO"); lpns.saveAndFlush(lpn);
        stock = new Stock(); stock.setProductId(product.getId()); stock.setPositionId(position.getId()); stock.setLpnId(lpn.getId());
        stock.setQuantity(new BigDecimal("10")); stock.setLot("L-01"); stock.setExpiryDate(LocalDate.of(2027, 1, 1));
        stock.setEntryAt(Instant.parse("2026-01-01T12:00:00Z")); stocks.saveAndFlush(stock);
    }

    @ParameterizedTest
    @CsvSource({"12,2", "7,-3", "10,0"})
    void shouldApplyPositiveNegativeAndZeroDifferencesOnceAndReplayTheSameOperation(String counted, String difference) throws Exception {
        if ("-3".equals(difference)) { stock.setBlockStatus("QUALIDADE"); stocks.saveAndFlush(stock); }
        JsonNode opened = open("open"); String id = inventoryId(opened); String itemId = opened.path("data").path("items").get(0).path("id").asText();
        mvc.perform(get("/api/inventories").header("Authorization", operator)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
        send("/api/inventories/" + id + "/counts", countBody(itemId, counted), operator, "count").andExpect(status().isOk());
        Map<String, Object> adjustment = Map.of("scannedPositionCode", "A-01", "reason", "Contagem física");
        send("/api/inventories/" + id + "/adjust", adjustment, manager, "adjust").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inventory.status").value("CONCLUIDO"));
        send("/api/inventories/" + id + "/adjust", adjustment, manager, "adjust").andExpect(status().isOk());
        assertEquals(0, new BigDecimal(counted).compareTo(stocks.findById(stock.getId()).orElseThrow().getQuantity()));
        if ("-3".equals(difference)) assertEquals("QUALIDADE", stocks.findById(stock.getId()).orElseThrow().getBlockStatus());
        var entries = transactions.findByStockIdOrderByOccurredAtAsc(stock.getId());
        if ("0".equals(difference)) assertTrue(entries.isEmpty());
        else { assertEquals(1, entries.size()); assertEquals(0, new BigDecimal(difference).compareTo(entries.getFirst().getQuantity())); }
        send("/api/inventories/" + id + "/adjust", adjustment, manager, "second-adjust").andExpect(status().isUnprocessableContent());
    }

    @Test
    void shouldCreateATraceableReceiptLayerForNewlyFoundGoodsWithoutChangingOldHistory() throws Exception {
        JsonNode opened = open("open"); String id = inventoryId(opened); String itemId = opened.path("data").path("items").get(0).path("id").asText();
        Map<String, Object> found = new LinkedHashMap<>(); found.put("productId", product.getId()); found.put("lpnCode", "FOUND-LPN");
        found.put("scannedProductCode", product.getSku()); found.put("lot", "L-FOUND"); found.put("expiryDate", "2027-02-01"); found.put("countedQuantity", 3);
        Map<String, Object> body = Map.of("scannedPositionCode", "A-01", "items", List.of(existingLine(itemId, "10"), found));
        send("/api/inventories/" + id + "/counts", body, operator, "count-found").andExpect(status().isOk());
        send("/api/inventories/" + id + "/adjust", Map.of("scannedPositionCode", "A-01", "reason", "Mercadoria encontrada"), manager, "adjust-found")
                .andExpect(status().isOk());
        var foundLpn = lpns.findByTrackingCode("FOUND-LPN").orElseThrow(); Stock foundStock = stocks.findByLpnId(foundLpn.getId()).getFirst();
        assertEquals("L-FOUND", foundStock.getLot()); assertEquals(LocalDate.of(2027, 2, 1), foundStock.getExpiryDate());
        assertEquals(0, new BigDecimal("3").compareTo(foundStock.getQuantity()));
        assertEquals(1, transactions.findByStockIdOrderByOccurredAtAsc(foundStock.getId()).size());
        assertEquals(Instant.parse("2026-01-01T12:00:00Z"), stocks.findById(stock.getId()).orElseThrow().getEntryAt());
    }

    @Test
    void shouldFreezeStockUntilCancellationAndPreserveTheOriginalBalance() throws Exception {
        String id = inventoryId(open("open"));
        assertThrows(BusinessRuleException.class, () -> mutation.ensureMovable(stock));
        send("/api/inventories/" + id + "/cancel", Map.of("scannedPositionCode", "A-01"), otherOperator, "wrong-cancel")
                .andExpect(status().isForbidden());
        send("/api/inventories/" + id + "/cancel", Map.of("scannedPositionCode", "A-01"), manager, "manager-cancel")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.inventory.status").value("CANCELADO"));
        assertDoesNotThrow(() -> mutation.ensureMovable(stock));
        assertEquals(0, new BigDecimal("10").compareTo(stocks.findById(stock.getId()).orElseThrow().getQuantity()));
        assertTrue(transactions.findByStockIdOrderByOccurredAtAsc(stock.getId()).isEmpty());
    }

    @Test
    void shouldRejectWrongOperatorWrongCodesAndMissingTraceForFoundGoods() throws Exception {
        JsonNode opened = open("open"); String id = inventoryId(opened); String itemId = opened.path("data").path("items").get(0).path("id").asText();
        send("/api/inventories/" + id + "/counts", countBody(itemId, "10"), otherOperator, "wrong-user").andExpect(status().isForbidden());
        var wrongScan = new LinkedHashMap<>(existingLine(itemId, "10")); wrongScan.put("scannedProductCode", "WRONG");
        send("/api/inventories/" + id + "/counts", Map.of("scannedPositionCode", "A-01", "items", List.of(wrongScan)), operator, "wrong-sku")
                .andExpect(status().isUnprocessableContent());
        var missingTrace = Map.of("productId", product.getId(), "lpnCode", "FOUND-LPN", "scannedProductCode", product.getSku(), "countedQuantity", 2);
        send("/api/inventories/" + id + "/counts", Map.of("scannedPositionCode", "A-01", "items", List.of(missingTrace)), operator, "invalid-trace")
                .andExpect(status().isUnprocessableContent());
        send("/api/inventories/" + id + "/adjust", Map.of("scannedPositionCode", "A-01", "reason", "Não contado"), manager, "uncounted")
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void shouldRejectSnapshotCreationWhileTasksArePendingAndInactiveResponsibles() throws Exception {
        WarehouseTask task = new WarehouseTask(); task.setType("TRANSFERENCIA"); task.setStatus("PENDENTE");
        task.setOriginPositionId(position.getId()); task.setSourceStockId(stock.getId()); task.setTargetQuantity(BigDecimal.ONE); tasks.saveAndFlush(task);
        send("/api/inventories", createBody(), operator, "pending-task").andExpect(status().isUnprocessableContent());
        tasks.delete(task); tasks.flush();
        User inactive = user("inactive@inventory.test", "OPERADOR"); inactive.setStatus(UserAccountStatus.DISABLED); users.saveAndFlush(inactive);
        var body = new LinkedHashMap<>(createBody()); body.put("responsibleUserId", inactive.getUlid());
        send("/api/inventories", body, operator, "inactive-user").andExpect(status().isUnprocessableContent());
        assertFalse(inventories.existsByPositionIdAndStatusIn(position.getId(), List.of("PENDENTE", "EM_CONTAGEM")));
    }

    @Test
    void shouldRejectAnAdjustmentThatExceedsPhysicalCapacityAtomically() throws Exception {
        JsonNode opened = open("open"); String id = inventoryId(opened); String itemId = opened.path("data").path("items").get(0).path("id").asText();
        send("/api/inventories/" + id + "/counts", countBody(itemId, "101"), operator, "count").andExpect(status().isOk());
        send("/api/inventories/" + id + "/adjust", Map.of("scannedPositionCode", "A-01", "reason", "Excesso"), manager, "over-capacity")
                .andExpect(status().isUnprocessableContent());
        assertEquals(0, new BigDecimal("10").compareTo(stocks.findById(stock.getId()).orElseThrow().getQuantity()));
        assertTrue(transactions.findByStockIdOrderByOccurredAtAsc(stock.getId()).isEmpty());
        assertEquals("EM_CONTAGEM", inventories.findById(id).orElseThrow().getStatus());
    }

    @Test
    void shouldRejectActiveReservationsEvenWhenTheLinkedTaskIsNoLongerPending() throws Exception {
        Partner partner = new Partner(); partner.setLegalName("Cliente"); partner.setType("CLIENTE"); partners.saveAndFlush(partner);
        WarehouseOrder order = new WarehouseOrder(); order.setPartnerId(partner.getId());
        order.setWarehouseId(mutation.warehouseForPosition(position.getId())); order.setCreatedById(operatorId);
        order.setType("SAIDA"); order.setStatus("PENDENTE"); orders.saveAndFlush(order);
        OrderItem orderItem = new OrderItem(); orderItem.setOrderId(order.getId()); orderItem.setProductId(product.getId());
        orderItem.setRequestedQuantity(BigDecimal.ONE); orderItems.saveAndFlush(orderItem);
        WarehouseTask task = new WarehouseTask(); task.setType("PICKING"); task.setStatus("CONCLUIDA");
        task.setTargetQuantity(BigDecimal.ONE); task.setSourceStockId(stock.getId()); tasks.saveAndFlush(task);
        StockReservation reservation = new StockReservation(); reservation.setStockId(stock.getId()); reservation.setTaskId(task.getId());
        reservation.setOrderItemId(orderItem.getId()); reservation.setReservedQuantity(BigDecimal.ONE);
        reservation.setStatus("ATIVA"); reservations.saveAndFlush(reservation);
        send("/api/inventories", createBody(), operator, "active-reservation").andExpect(status().isUnprocessableContent());
        assertFalse(inventories.existsByPositionIdAndStatusIn(position.getId(), List.of("PENDENTE", "EM_CONTAGEM")));
        assertEquals(0, new BigDecimal("10").compareTo(stocks.findById(stock.getId()).orElseThrow().getQuantity()));
    }

    @Test
    void shouldOpenInventoryAfterDispatchWithoutTreatingZeroHistoricalStockAsPreparedGoods() throws Exception {
        Partner customer=new Partner();customer.setLegalName("Cliente expedido");customer.setType("CLIENTE");partners.saveAndFlush(customer);
        WarehouseOrder order=new WarehouseOrder();order.setPartnerId(customer.getId());
        order.setWarehouseId(mutation.warehouseForPosition(position.getId()));order.setCreatedById(operatorId);
        order.setType("SAIDA");order.setStatus("ATENDIDO");orders.saveAndFlush(order);
        OrderItem fulfilled=new OrderItem();fulfilled.setOrderId(order.getId());fulfilled.setProductId(product.getId());
        fulfilled.setRequestedQuantity(BigDecimal.TEN);fulfilled.setFulfilledQuantity(BigDecimal.TEN);orderItems.saveAndFlush(fulfilled);
        stock.setQuantity(BigDecimal.ZERO);stocks.saveAndFlush(stock);
        lpn.setOutboundOrderId(order.getId());lpn.setStatus("EXPEDIDO");lpn.setPositionId(null);lpns.saveAndFlush(lpn);
        position.setPurpose("EXPEDICAO");positions.saveAndFlush(position);

        JsonNode opened=open("inventory-after-dispatch");
        String inventoryId=inventoryId(opened);
        assertEquals("PENDENTE",opened.path("data").path("inventory").path("status").asText());
        assertEquals(0,opened.path("data").path("items").size());
        assertTrue(inventories.existsByPositionIdAndStatusIn(position.getId(),List.of("PENDENTE","EM_CONTAGEM")));
        mvc.perform(get("/api/inventories/{id}",inventoryId).header("Authorization",operator))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
        assertEquals(0,stocks.findById(stock.getId()).orElseThrow().getQuantity().signum());
        assertEquals("EXPEDIDO",lpns.findById(lpn.getId()).orElseThrow().getStatus());
        assertNull(lpns.findById(lpn.getId()).orElseThrow().getPositionId());
        assertTrue(transactions.findByStockIdOrderByOccurredAtAsc(stock.getId()).isEmpty());
        assertEquals(0,BigDecimal.TEN.compareTo(orderItems.findById(fulfilled.getId()).orElseThrow().getFulfilledQuantity()));
    }

    private JsonNode open(String key) throws Exception {
        var result = send("/api/inventories", createBody(), operator, key).andExpect(status().isCreated()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }
    private String inventoryId(JsonNode response) { return response.path("data").path("inventory").path("id").asText(); }
    private Map<String, Object> createBody() { return Map.of("positionId", position.getId(), "scannedPositionCode", "A-01"); }
    private Map<String, Object> countBody(String itemId, String quantity) { return Map.of("scannedPositionCode", "A-01", "items", List.of(existingLine(itemId, quantity))); }
    private Map<String, Object> existingLine(String itemId, String quantity) {
        return Map.of("inventoryItemId", itemId, "lpnCode", "LPN-INV", "scannedProductCode", "SKU-INV", "countedQuantity", new BigDecimal(quantity));
    }
    private ResultActions send(String path, Object body, String authorization, String key) throws Exception {
        return mvc.perform(post(path).header("Authorization", authorization).header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)));
    }
    private User user(String email, String profile) {
        User user = new User(profile, email, "unused-test-hash"); user.setProfile(profiles.findById(profile).orElseThrow());
        user.setStatus(UserAccountStatus.ACTIVE); user.setEmailVerifiedAt(Instant.now()); return users.saveAndFlush(user);
    }
    private String bearer(User user) { return "Bearer " + tokens.generateTokens(user.getUlid()).token(); }
}
