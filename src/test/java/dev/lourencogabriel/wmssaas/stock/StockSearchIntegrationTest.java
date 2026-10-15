package dev.lourencogabriel.wmssaas.stock;

import dev.lourencogabriel.wmssaas.audit.model.StockTransaction;
import dev.lourencogabriel.wmssaas.auth.service.TokenService;
import dev.lourencogabriel.wmssaas.inventory.model.Inventory;
import dev.lourencogabriel.wmssaas.operation.model.WmsOperation;
import dev.lourencogabriel.wmssaas.order.model.WarehouseOrder;
import dev.lourencogabriel.wmssaas.partner.model.Partner;
import dev.lourencogabriel.wmssaas.product.model.Category;
import dev.lourencogabriel.wmssaas.product.model.Product;
import dev.lourencogabriel.wmssaas.shared.model.UserAccountStatus;
import dev.lourencogabriel.wmssaas.stock.model.Lpn;
import dev.lourencogabriel.wmssaas.stock.model.Stock;
import dev.lourencogabriel.wmssaas.task.model.TaskHistory;
import dev.lourencogabriel.wmssaas.task.model.WarehouseTask;
import dev.lourencogabriel.wmssaas.user.model.User;
import dev.lourencogabriel.wmssaas.user.repository.AccessProfileRepository;
import dev.lourencogabriel.wmssaas.user.repository.UserRepository;
import dev.lourencogabriel.wmssaas.warehouse.model.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:stock-search;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class StockSearchIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired UserRepository users;
    @Autowired AccessProfileRepository profiles;
    @Autowired TokenService tokens;
    User managerUser;
    User operatorUser;
    String manager;
    String operator;
    Area first;
    Area second;
    Product product;

    @BeforeEach
    void prepare() {
        managerUser = user("manager@stock-search.test", "GESTOR"); manager = bearer(managerUser);
        operatorUser = user("operator@stock-search.test", "OPERADOR"); operator = bearer(operatorUser);
        first = area("First", "FIRST-POS"); second = area("Second", "SECOND-POS");
        Category category = new Category(); category.setName("Search"); em.persist(category);
        product = new Product(); product.setCategoryId(category.getId()); product.setSku("SKU-SEARCH");
        product.setName("Search product"); em.persist(product); em.flush();
    }

    @Test
    void shouldCombineLiteralStockSearchAndAllScopeFiltersWithoutLeakingAnotherWarehouse() throws Exception {
        Lpn firstLpn = lpn(first.position(), "LPN-FIRST", "ATIVO");
        Stock expected = balance(first.position(), firstLpn, "LOT%_!"); expected.setBlockStatus("BLOQUEADO");
        balance(first.position(), firstLpn, "LOT-PLAIN"); balance(second.position(), lpn(second.position(), "LPN-SECOND", "ATIVO"), "LOT%_!"); em.flush();
        mvc.perform(get("/api/search/stock").header("Authorization", operator).param("q", "%_!")
                        .param("warehouseId", first.warehouse().getId()).param("positionId", first.position().getId())
                        .param("productId", product.getId()).param("lpnId", firstLpn.getId()).param("blockStatus", "BLOQUEADO"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(expected.getId()))
                .andExpect(jsonPath("$.data.content[0].availableQuantity").value(0));
        mvc.perform(get("/api/stock").header("Authorization", operator).param("warehouseId", second.warehouse().getId())
                        .param("positionId", first.position().getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(get("/api/search/stock").header("Authorization", operator).param("q", "sku-search")
                        .param("warehouseId", first.warehouse().getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2));
        mvc.perform(get("/api/lookups/stock").header("Authorization", operator).param("q", "%_!").param("warehouseId", first.warehouse().getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].label", containsString("SKU-SEARCH")))
                .andExpect(jsonPath("$.data.content[0].quantity").doesNotExist());
    }

    @Test
    void shouldPageTrackingCodeLookupsAndFindDispatchedLpnsThroughTheirOrderWarehouse() throws Exception {
        for (int i = 0; i < 7; i++) lpn(first.position(), "SCAN-" + i, "ATIVO");
        lpn(second.position(), "SCAN-OTHER", "ATIVO");
        Partner partner = new Partner(); partner.setLegalName("Customer"); partner.setType("CLIENTE"); em.persist(partner);
        WarehouseOrder order = new WarehouseOrder(); order.setWarehouseId(first.warehouse().getId()); order.setPartnerId(partner.getId());
        order.setCreatedById(managerUser.getUlid()); order.setType("SAIDA"); order.setStatus("ATENDIDO"); em.persist(order);
        Lpn dispatched = lpn(null, "SHIPPED-FIRST", "EXPEDIDO"); dispatched.setOutboundOrderId(order.getId()); em.flush();
        mvc.perform(get("/api/lookups/lpns").header("Authorization", operator).param("warehouseId", first.warehouse().getId()).param("q", "scan-"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.size").value(5))
                .andExpect(jsonPath("$.data.totalElements").value(7)).andExpect(jsonPath("$.data.content", hasSize(5)))
                .andExpect(jsonPath("$.data.content[0].label", containsString("SCAN-")))
                .andExpect(jsonPath("$.data.content[0].status").doesNotExist());
        mvc.perform(get("/api/lookups/lpns").header("Authorization", operator).param("warehouseId", first.warehouse().getId()).param("q", "scan-").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content", hasSize(2)));
        mvc.perform(get("/api/search/lpns").header("Authorization", operator).param("warehouseId", first.warehouse().getId()).param("status", "EXPEDIDO"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(dispatched.getId()));
        mvc.perform(get("/api/lpns/{id}", dispatched.getId()).header("Authorization", operator))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.trackingCode").value("SHIPPED-FIRST"));
    }

    @Test
    void shouldFilterAuditByDestinationStockWarehouseAndInclusiveDatesAndRequireAuditPermission() throws Exception {
        Stock source = balance(first.position(), lpn(first.position(), "AUDIT-SOURCE", "ATIVO"), "LOT%_!");
        Stock destination = balance(first.position(), lpn(first.position(), "AUDIT-DEST", "ATIVO"), "LOT%_!");
        Stock remote = balance(second.position(), lpn(second.position(), "AUDIT-REMOTE", "ATIVO"), "LOT%_!");
        Instant occurred = Instant.parse("2026-03-01T12:00:00Z");
        StockTransaction expected = transaction(source, destination, first.position(), occurred, "LOT%_!");
        transaction(remote, null, second.position(), occurred, "LOT%_!"); em.flush();
        mvc.perform(get("/api/search/stock-transactions").header("Authorization", manager).param("q", "%_!")
                        .param("stockId", destination.getId()).param("warehouseId", first.warehouse().getId())
                        .param("from", occurred.toString()).param("to", occurred.toString()).param("movementType", "TRANSFERENCIA"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(expected.getId()));
        mvc.perform(get("/api/lookups/stock-transactions").header("Authorization", manager).param("warehouseId", first.warehouse().getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(get("/api/search/stock-transactions").header("Authorization", operator)).andExpect(status().isForbidden());
        mvc.perform(get("/api/lookups/stock-transactions").header("Authorization", operator)).andExpect(status().isForbidden());
        mvc.perform(get("/api/stock-transactions").header("Authorization", manager).param("from", occurred.plusSeconds(1).toString()).param("to", occurred.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldCombineInventoryResponsibleStatusAndWarehouseForSearchAndLookups() throws Exception {
        User other = user("other@stock-search.test", "OPERADOR");
        Inventory expected = inventory(first, first.position(), operatorUser, "PENDENTE");
        Position extra = new Position(); extra.setZoneId(first.zone().getId()); extra.setCode("EXTRA-POS"); em.persist(extra);
        inventory(first, extra, other, "EM_CONTAGEM"); inventory(second, second.position(), operatorUser, "PENDENTE"); em.flush();
        mvc.perform(get("/api/search/inventories").header("Authorization", operator).param("q", "pendente")
                        .param("warehouseId", first.warehouse().getId()).param("responsibleUserId", operatorUser.getUlid()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(expected.getId()));
        mvc.perform(get("/api/lookups/inventories").header("Authorization", manager).param("warehouseId", first.warehouse().getId())
                        .param("positionId", first.position().getId()).param("responsibleUserId", operatorUser.getUlid()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(expected.getId()));
        mvc.perform(get("/api/inventories").header("Authorization", manager).param("warehouseId", second.warehouse().getId())
                        .param("responsibleUserId", other.getUlid()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void shouldPaginateTaskHistoryWithoutIncludingAnotherTasksEvents() throws Exception {
        WarehouseTask task = task(); WarehouseTask other = task();
        history(task, "PENDENTE", "EM_EXECUCAO", "Início");
        history(task, "EM_EXECUCAO", "CONCLUIDA", "Conferência final");
        history(other, "PENDENTE", "EM_EXECUCAO", "Outra tarefa"); em.flush();
        mvc.perform(get("/api/tasks/{id}/history", task.getId()).header("Authorization", operator))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(2));
        mvc.perform(get("/api/tasks/{id}/history", task.getId()).param("page", "1").param("size", "1")
                        .header("Authorization", operator))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].taskId").value(task.getId()))
                .andExpect(jsonPath("$.data.totalElements").value(2)).andExpect(jsonPath("$.data.last").value(true));
        mvc.perform(get("/api/tasks/{id}/history", task.getId()).param("q", "final").header("Authorization", operator))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].newStatus").value("CONCLUIDA"));
        mvc.perform(get("/api/tasks/{id}/history", "0".repeat(26)).header("Authorization", operator))
                .andExpect(status().isNotFound());
    }

    private WarehouseTask task() {
        WarehouseTask value = new WarehouseTask(); value.setType("TRANSFERENCIA"); value.setStatus("CONCLUIDA");
        value.setTargetQuantity(BigDecimal.ONE); em.persist(value); return value;
    }

    private void history(WarehouseTask task, String previous, String next, String reason) {
        WmsOperation operation = new WmsOperation(); operation.setIdempotencyKey("history-" + task.getId() + next);
        operation.setRequestHash("b".repeat(64)); operation.setType("SEARCH_FIXTURE"); operation.setStatus("CONCLUIDA");
        operation.setUserId(operatorUser.getUlid()); em.persist(operation);
        TaskHistory value = new TaskHistory(); value.setTaskId(task.getId()); value.setOperationId(operation.getId());
        value.setPreviousStatus(previous); value.setNewStatus(next); value.setUserId(operatorUser.getUlid());
        value.setReason(reason); value.setRecordedAt(Instant.now()); em.persist(value);
    }

    private Area area(String name, String code) {
        Warehouse warehouse = new Warehouse(); warehouse.setName(name); em.persist(warehouse);
        Zone zone = new Zone(); zone.setWarehouseId(warehouse.getId()); zone.setName("Zone " + name); em.persist(zone);
        Position position = new Position(); position.setZoneId(zone.getId()); position.setCode(code); em.persist(position);
        return new Area(warehouse, zone, position);
    }
    private Lpn lpn(Position position, String code, String status) {
        Lpn value = new Lpn(); value.setTrackingCode(code); value.setPositionId(position == null ? null : position.getId()); value.setStatus(status); em.persist(value); return value;
    }
    private Stock balance(Position position, Lpn lpn, String lot) {
        Stock value = new Stock(); value.setPositionId(position.getId()); value.setLpnId(lpn.getId()); value.setProductId(product.getId());
        value.setLot(lot); value.setQuantity(BigDecimal.TEN); value.setEntryAt(Instant.parse("2026-01-01T00:00:00Z")); em.persist(value); return value;
    }
    private StockTransaction transaction(Stock source, Stock destination, Position position, Instant occurred, String lot) {
        WmsOperation operation = new WmsOperation(); operation.setIdempotencyKey("query-" + source.getId()); operation.setRequestHash("a".repeat(64));
        operation.setType("SEARCH_FIXTURE"); operation.setStatus("CONCLUIDA"); operation.setUserId(managerUser.getUlid()); em.persist(operation);
        StockTransaction value = new StockTransaction(); value.setProductId(product.getId()); value.setStockId(source.getId());
        value.setLpnId(source.getLpnId()); value.setUserId(managerUser.getUlid()); value.setOperationId(operation.getId()); value.setSequence(1);
        value.setMovementType("TRANSFERENCIA"); value.setPreviousPositionId(position.getId()); value.setNewPositionId(position.getId());
        value.setPreviousBalance(BigDecimal.TEN); value.setResultingBalance(BigDecimal.TEN); value.setQuantity(BigDecimal.ONE);
        value.setOccurredAt(occurred); value.setRecordedEntryAt(source.getEntryAt()); value.setRecordedLot(lot);
        if (destination != null) { value.setDestinationStockId(destination.getId()); value.setDestinationLpnId(destination.getLpnId()); }
        em.persist(value); return value;
    }
    private Inventory inventory(Area area, Position position, User responsible, String status) {
        Inventory value = new Inventory(); value.setWarehouseId(area.warehouse().getId()); value.setPositionId(position.getId());
        value.setResponsibleUserId(responsible.getUlid()); value.setStatus(status); em.persist(value); return value;
    }
    private User user(String email, String profile) {
        User value = new User(profile, email, "unused"); value.setProfile(profiles.findById(profile).orElseThrow());
        value.setStatus(UserAccountStatus.ACTIVE); value.setEmailVerifiedAt(Instant.now()); return users.saveAndFlush(value);
    }
    private String bearer(User value) { return "Bearer " + tokens.generateTokens(value.getUlid()).token(); }
    private record Area(Warehouse warehouse, Zone zone, Position position) {}
}
