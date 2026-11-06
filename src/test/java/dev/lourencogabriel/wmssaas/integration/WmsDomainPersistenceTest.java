package dev.lourencogabriel.wmssaas.integration;

import dev.lourencogabriel.wmssaas.shared.model.UserAccountStatus;
import dev.lourencogabriel.wmssaas.user.model.User;
import dev.lourencogabriel.wmssaas.user.repository.AccessProfileRepository;
import dev.lourencogabriel.wmssaas.user.repository.UserRepository;
import dev.lourencogabriel.wmssaas.audit.model.StockTransaction;
import dev.lourencogabriel.wmssaas.operation.model.WmsOperation;
import dev.lourencogabriel.wmssaas.order.model.OrderItem;
import dev.lourencogabriel.wmssaas.order.model.WarehouseOrder;
import dev.lourencogabriel.wmssaas.partner.model.Partner;
import dev.lourencogabriel.wmssaas.product.model.Category;
import dev.lourencogabriel.wmssaas.product.model.Product;
import dev.lourencogabriel.wmssaas.stock.model.Lpn;
import dev.lourencogabriel.wmssaas.stock.model.Stock;
import dev.lourencogabriel.wmssaas.stock.model.StockReservation;
import dev.lourencogabriel.wmssaas.task.model.WarehouseTask;
import dev.lourencogabriel.wmssaas.warehouse.model.Position;
import dev.lourencogabriel.wmssaas.warehouse.model.Warehouse;
import dev.lourencogabriel.wmssaas.warehouse.model.Zone;
import dev.lourencogabriel.wmssaas.audit.repository.StockTransactionRepository;
import dev.lourencogabriel.wmssaas.operation.repository.WmsOperationRepository;
import dev.lourencogabriel.wmssaas.order.repository.OrderItemRepository;
import dev.lourencogabriel.wmssaas.order.repository.WarehouseOrderRepository;
import dev.lourencogabriel.wmssaas.partner.repository.PartnerRepository;
import dev.lourencogabriel.wmssaas.product.repository.CategoryRepository;
import dev.lourencogabriel.wmssaas.product.repository.ProductRepository;
import dev.lourencogabriel.wmssaas.stock.repository.LpnRepository;
import dev.lourencogabriel.wmssaas.stock.repository.StockRepository;
import dev.lourencogabriel.wmssaas.stock.repository.StockReservationRepository;
import dev.lourencogabriel.wmssaas.task.repository.WarehouseTaskRepository;
import dev.lourencogabriel.wmssaas.warehouse.repository.PositionRepository;
import dev.lourencogabriel.wmssaas.warehouse.repository.WarehouseRepository;
import dev.lourencogabriel.wmssaas.warehouse.repository.ZoneRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:wms-domain-persistence;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@ActiveProfiles("test")
@Transactional
class WmsDomainPersistenceTest {
    @Autowired EntityManager entityManager;
    @Autowired UserRepository users;
    @Autowired AccessProfileRepository profiles;
    @Autowired WarehouseRepository warehouses;
    @Autowired ZoneRepository zones;
    @Autowired PositionRepository positions;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired LpnRepository lpns;
    @Autowired StockRepository stocks;
    @Autowired WmsOperationRepository operations;
    @Autowired StockTransactionRepository transactions;
    @Autowired PartnerRepository partners;
    @Autowired WarehouseOrderRepository orders;
    @Autowired OrderItemRepository orderItems;
    @Autowired WarehouseTaskRepository tasks;
    @Autowired StockReservationRepository reservations;

    @Test
    void shouldKeepReceiptLayersSeparateAndSelectFefoThenFifoWithDeterministicTies() {
        Fixture fixture = fixture();
        Stock laterEntry = stock(fixture, "2026-02-02T12:00:00Z", "2026-12-01", "1.125001");
        Stock earlierEntry = stock(fixture, "2026-02-01T12:00:00Z", "2026-12-01", "2.000001");
        Stock nearestExpiry = stock(fixture, "2026-02-03T12:00:00Z", "2026-11-01", "3");
        Stock noExpiry = stock(fixture, "2026-01-01T12:00:00Z", null, "4");
        Stock blocked = stock(fixture, "2025-01-01T12:00:00Z", "2026-10-01", "5");
        blocked.setBlockStatus("QUALIDADE");
        stocks.saveAndFlush(blocked);
        entityManager.clear();

        List<Stock> candidates = stocks.findAllocationCandidates(fixture.warehouse().getId(), fixture.product().getId());
        assertEquals(List.of(nearestExpiry.getId(), earlierEntry.getId(), laterEntry.getId(), noExpiry.getId()),
                candidates.stream().map(Stock::getId).toList());
        assertEquals(0, new BigDecimal("1.125001").compareTo(stocks.findById(laterEntry.getId()).orElseThrow().getQuantity()));
        assertEquals(5, stocks.findByLpnId(fixture.lpn().getId()).size());
        assertNotNull(stocks.findByIdForUpdate(earlierEntry.getId()).orElseThrow().getVersion());
    }

    @Test
    void shouldKeepLedgerEntriesImmutableAndAppendCompensationsWithoutRewritingHistory() {
        Fixture fixture = fixture();
        Stock stock = stock(fixture, "2026-02-01T12:00:00Z", null, "5");
        WmsOperation operation = operation(fixture.user().getUlid(), "entry");
        StockTransaction original = transaction(fixture, stock, operation, 1, "ENTRADA", "5", "0", "5");
        transactions.save(original);
        entityManager.flush();
        String originalId = original.getId();
        original.setQuantity(new BigDecimal("99"));
        entityManager.flush();
        entityManager.clear();
        assertEquals(0, new BigDecimal("5").compareTo(transactions.findById(originalId).orElseThrow().getQuantity()));

        stock = stocks.findByIdForUpdate(stock.getId()).orElseThrow();
        stock.setQuantity(new BigDecimal("3"));
        WmsOperation adjustment = operation(fixture.user().getUlid(), "compensation");
        StockTransaction compensation = transaction(fixture, stock, adjustment, 1, "COMPENSACAO", "-2", "5", "3");
        compensation.setCompensatedTransactionId(originalId);
        compensation.setReason("Correção da quantidade conferida");
        transactions.save(compensation);
        entityManager.flush();
        entityManager.clear();

        assertEquals(2, transactions.findByStockIdOrderByOccurredAtAsc(stock.getId()).size());
        assertEquals(0, new BigDecimal("5").compareTo(transactions.findById(originalId).orElseThrow().getResultingBalance()));
        assertEquals(compensation.getId(), transactions.findByCompensatedTransactionId(originalId).getFirst().getId());
        assertEquals(0, new BigDecimal("3").compareTo(stocks.findById(stock.getId()).orElseThrow().getQuantity()));
    }

    @Test
    void shouldPersistAReservationAgainstPhysicalStockAndPreserveItsUnconsumedRemainder() {
        Fixture fixture = fixture();
        Stock stock = stock(fixture, "2026-02-01T12:00:00Z", null, "10");
        Partner partner = new Partner();
        partner.setLegalName("Cliente de teste"); partner.setType("CLIENTE");
        partners.saveAndFlush(partner);
        WarehouseOrder order = new WarehouseOrder();
        order.setPartnerId(partner.getId()); order.setWarehouseId(fixture.warehouse().getId());
        order.setCreatedById(fixture.user().getUlid()); order.setType("SAIDA"); order.setStatus("PENDENTE");
        orders.saveAndFlush(order);
        OrderItem item = new OrderItem(); item.setOrderId(order.getId()); item.setProductId(fixture.product().getId());
        item.setRequestedQuantity(new BigDecimal("6")); orderItems.saveAndFlush(item);
        WarehouseTask task = new WarehouseTask(); task.setType("PICKING"); task.setStatus("PENDENTE");
        task.setOrderId(order.getId()); task.setOrderItemId(item.getId()); task.setProductId(fixture.product().getId());
        task.setSourceStockId(stock.getId()); task.setTargetQuantity(new BigDecimal("6")); tasks.saveAndFlush(task);
        StockReservation reservation = new StockReservation(); reservation.setStockId(stock.getId());
        reservation.setTaskId(task.getId()); reservation.setOrderItemId(item.getId());
        reservation.setReservedQuantity(new BigDecimal("6")); reservation.setConsumedQuantity(new BigDecimal("2"));
        reservation.setReleasedQuantity(BigDecimal.ONE); reservation.setStatus("ATIVA"); reservations.saveAndFlush(reservation);
        entityManager.clear();

        StockReservation stored = reservations.findByTaskId(task.getId()).orElseThrow();
        assertEquals(stock.getId(), stored.getStockId());
        assertEquals(0, new BigDecimal("3").compareTo(stored.remainingQuantity()));
        assertEquals(1, reservations.findByStockIdAndStatus(stock.getId(), "ATIVA").size());
        assertEquals(0, new BigDecimal("3").compareTo(reservations.sumActiveRemainingByStockId(stock.getId())));
        stored.setStatus("ENCERRADA");
        reservations.saveAndFlush(stored);
        assertEquals(0, BigDecimal.ZERO.compareTo(reservations.sumActiveRemainingByStockId(stock.getId())));
        assertEquals(0, new BigDecimal("10").compareTo(stocks.findById(stock.getId()).orElseThrow().getQuantity()));
    }

    private Fixture fixture() {
        User user = new User("Operador", "persistence@example.com", "test-hash");
        user.setProfile(profiles.findById("OPERADOR").orElseThrow()); user.setStatus(UserAccountStatus.ACTIVE);
        user.setEmailVerifiedAt(Instant.now()); users.saveAndFlush(user);
        Warehouse warehouse = new Warehouse(); warehouse.setName("Principal"); warehouses.saveAndFlush(warehouse);
        Zone zone = new Zone(); zone.setWarehouseId(warehouse.getId()); zone.setName("A"); zones.saveAndFlush(zone);
        Position position = new Position(); position.setZoneId(zone.getId()); position.setCode("A1"); positions.saveAndFlush(position);
        Category category = new Category(); category.setName("Geral"); categories.saveAndFlush(category);
        Product product = new Product(); product.setCategoryId(category.getId()); product.setSku("SKU-1"); product.setName("Produto");
        products.saveAndFlush(product);
        Lpn lpn = new Lpn(); lpn.setTrackingCode("LPN-1"); lpn.setPositionId(position.getId()); lpn.setStatus("ATIVO");
        lpns.saveAndFlush(lpn);
        return new Fixture(user, warehouse, position, product, lpn);
    }

    private Stock stock(Fixture fixture, String entry, String expiry, String quantity) {
        Stock stock = new Stock(); stock.setProductId(fixture.product().getId()); stock.setLpnId(fixture.lpn().getId());
        stock.setPositionId(fixture.position().getId()); stock.setQuantity(new BigDecimal(quantity));
        stock.setEntryAt(Instant.parse(entry)); stock.setExpiryDate(expiry == null ? null : LocalDate.parse(expiry));
        return stocks.saveAndFlush(stock);
    }

    private WmsOperation operation(String userId, String key) {
        WmsOperation operation = new WmsOperation(); operation.setIdempotencyKey(key); operation.setUserId(userId);
        operation.setRequestHash("a".repeat(64)); operation.setType("TESTE"); operation.setStatus("CONCLUIDA");
        return operations.saveAndFlush(operation);
    }

    private StockTransaction transaction(Fixture fixture, Stock stock, WmsOperation operation, int sequence,
                                         String type, String quantity, String previous, String resulting) {
        StockTransaction entry = new StockTransaction(); entry.setProductId(fixture.product().getId());
        entry.setUserId(fixture.user().getUlid()); entry.setLpnId(stock.getLpnId()); entry.setStockId(stock.getId());
        entry.setMovementType(type); entry.setQuantity(new BigDecimal(quantity)); entry.setOperationId(operation.getId());
        entry.setSequence(sequence); entry.setPreviousBalance(new BigDecimal(previous)); entry.setResultingBalance(new BigDecimal(resulting));
        entry.setRecordedEntryAt(stock.getEntryAt()); entry.setOccurredAt(Instant.now());
        return entry;
    }

    private record Fixture(User user, Warehouse warehouse, Position position, Product product, Lpn lpn) {}
}
