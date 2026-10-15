package dev.lourencogabriel.wmssaas.inventory.service;

import dev.lourencogabriel.wmssaas.auth.authorization.WmsPermission;
import dev.lourencogabriel.wmssaas.inventory.dto.InventoryDtos.*;
import dev.lourencogabriel.wmssaas.inventory.model.*;
import dev.lourencogabriel.wmssaas.inventory.repository.*;
import dev.lourencogabriel.wmssaas.operation.service.OperationService;
import dev.lourencogabriel.wmssaas.product.model.Product;
import dev.lourencogabriel.wmssaas.shared.api.*;
import dev.lourencogabriel.wmssaas.inventory.dto.InventoryFilters;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import static dev.lourencogabriel.wmssaas.shared.persistence.SearchSpecifications.*;
import dev.lourencogabriel.wmssaas.stock.model.*;
import dev.lourencogabriel.wmssaas.stock.repository.*;
import dev.lourencogabriel.wmssaas.stock.service.StockMutationService;
import dev.lourencogabriel.wmssaas.task.repository.WarehouseTaskRepository;
import dev.lourencogabriel.wmssaas.user.model.User;
import dev.lourencogabriel.wmssaas.user.repository.UserRepository;
import dev.lourencogabriel.wmssaas.warehouse.model.Position;
import dev.lourencogabriel.wmssaas.warehouse.repository.ZoneRepository;
import dev.lourencogabriel.wmssaas.web.exception.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(isolation = Isolation.READ_COMMITTED)
public class InventoryService {
    private static final List<String> ACTIVE = List.of("PENDENTE", "EM_CONTAGEM");
    private static final List<String> ACTIVE_TASKS = List.of("PENDENTE", "EM_EXECUCAO");
    private final InventoryRepository inventories;
    private final InventoryItemRepository items;
    private final StockRepository stocks;
    private final LpnRepository lpns;
    private final WarehouseTaskRepository tasks;
    private final UserRepository users;
    private final ZoneRepository zones;
    private final StockMutationService mutation;
    private final OperationService operations;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public PageResponse<Summary> list(String warehouseId, String positionId, String status, Pageable pageable) {
        Pageable bounded = PageRequest.of(Math.max(0, pageable.getPageNumber()), Math.min(100, pageable.getPageSize()), Sort.by("id"));
        return PageResponse.from(inventories.findAll((r, q, b) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            if (warehouseId != null) predicates.add(b.equal(r.get("warehouseId"), warehouseId));
            if (positionId != null) predicates.add(b.equal(r.get("positionId"), positionId));
            if (status != null) predicates.add(b.equal(r.get("status"), status));
            return b.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        }, bounded).map(this::summary));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@wmsAuthorization.can(authentication, 'INVENTORY_VIEW')")
    public PageResponse<Summary> search(PageQuery query,InventoryFilters filters) {
        return PageResponse.from(inventories.findAll(searchSpec(query,filters),query.toPageable()).map(this::summary));
    }
    @Transactional(readOnly = true)
    @PreAuthorize("@wmsAuthorization.can(authentication, 'INVENTORY_VIEW')")
    public PageResponse<LookupOption> lookup(LookupQuery query,InventoryFilters filters) {
        return PageResponse.from(inventories.findAll(searchSpec(query,filters),query.toPageable())
                .map(inventory -> new LookupOption(inventory.getId(),mutation.position(inventory.getPositionId()).getCode()+" | "+inventory.getStatus()+" | "+inventory.getId())));
    }
    private Specification<Inventory> searchSpec(PageQuery query,InventoryFilters filters) {
        return dev.lourencogabriel.wmssaas.shared.persistence.SearchSpecifications.<Inventory>text(query.getQ(),"id","status","responsibleUserId")
                .and(equal("warehouseId",filters.getWarehouseId())).and(equal("positionId",filters.getPositionId()))
                .and(equal("status",filters.getStatus())).and(equal("responsibleUserId",filters.getResponsibleUserId()));
    }

    @Transactional(readOnly = true)
    public Detail detail(String id) { return response(require(id)); }

    public Detail create(CreateRequest request, String key) {
        String actor = OperationService.currentActorId();
        return operations.execute(key, request, actor, "INVENTARIO_ABRIR", Detail.class, operationId -> {
            String warehouseId = mutation.warehouseForPosition(request.positionId());
            mutation.lockWarehouse(warehouseId);
            Position position = mutation.lockPositions(List.of(request.positionId())).getFirst();
            if (!Boolean.TRUE.equals(position.getActive()) || position.getStatus().startsWith("INATIV")) fail("Position is inactive");
            if (!Boolean.TRUE.equals(zones.findById(position.getZoneId()).orElseThrow().getActive())) fail("Zone is inactive");
            StockMutationService.requireCode(request.scannedPositionCode(), position.getCode(), "position");
            if (inventories.existsByPositionIdAndStatusIn(position.getId(), ACTIVE)) fail("Position already has an active inventory");
            String responsible = request.responsibleUserId() == null || request.responsibleUserId().isBlank() ? actor : request.responsibleUserId();
            requireResponsible(responsible);
            ensureNoWork(position.getId());
            List<Stock> snapshot = mutation.lockStocks(stocks.findByPositionId(position.getId()).stream().map(Stock::getId).toList());
            for (Stock stock : snapshot) {
                if (stock.getQuantity().signum() <= 0) continue;
                if (mutation.reserved(stock).signum() > 0) fail("Cancel active reservations before opening inventory");
                if (tasks.findBySourceStockId(stock.getId()).stream().anyMatch(t -> ACTIVE_TASKS.contains(t.getStatus()))) fail("Cancel active tasks before inventory");
                if (stock.getLpnId() != null) {
                    Lpn lpn = lpns.findById(stock.getLpnId()).orElseThrow();
                    if (!position.getId().equals(lpn.getPositionId())) fail("Stock and LPN positions are inconsistent");
                    if (lpn.getOutboundOrderId() != null) fail("Return or dispatch prepared outbound goods before inventory");
                    if (tasks.findByLpnId(lpn.getId()).stream().anyMatch(t -> ACTIVE_TASKS.contains(t.getStatus()))) fail("Cancel active LPN tasks before inventory");
                }
            }
            Inventory inventory = new Inventory(); inventory.setWarehouseId(warehouseId); inventory.setPositionId(position.getId());
            inventory.setResponsibleUserId(responsible); inventory.setStatus("PENDENTE"); inventories.saveAndFlush(inventory);
            for (Stock stock : snapshot) {
                if (stock.getQuantity().signum() <= 0) continue;
                if (stock.getLpnId() == null) {
                    Lpn lpn = mutation.getOrCreateLpn("INV-" + stock.getId(), position);
                    stock.setLpnId(lpn.getId()); stocks.save(stock);
                }
                InventoryItem item = new InventoryItem(); item.setInventoryId(inventory.getId()); item.setStockId(stock.getId());
                item.setProductId(stock.getProductId()); item.setLpnId(stock.getLpnId()); item.setLot(stock.getLot());
                item.setExpiryDate(stock.getExpiryDate()); item.setTheoreticalQuantity(stock.getQuantity()); items.save(item);
            }
            items.flush();
            return response(inventory);
        });
    }

    public Detail count(String id, CountsRequest request, String key) {
        String actor = OperationService.currentActorId();
        return operations.execute(key, Map.of("inventoryId", id, "request", request), actor, "INVENTARIO_CONTAR", Detail.class, operationId -> {
            Inventory inventory = lock(id); requireOpen(inventory); requireOwner(inventory, actor, false); requireResponsible(actor);
            Position position = mutation.position(inventory.getPositionId());
            StockMutationService.requireCode(request.scannedPositionCode(), position.getCode(), "position");
            Set<String> counted = new HashSet<>();
            for (CountLine line : request.items()) {
                InventoryItem item;
                if (line.inventoryItemId() != null) {
                    if (!counted.add(line.inventoryItemId())) fail("An item occurs more than once in this count");
                    item = items.findByIdForUpdate(line.inventoryItemId()).orElseThrow(() -> new ResourceNotFoundException("InventoryItem", line.inventoryItemId()));
                    if (!inventory.getId().equals(item.getInventoryId())) fail("Item belongs to another inventory");
                    if (line.productId() != null && !line.productId().equals(item.getProductId())) fail("Product does not match inventory item");
                    if (line.lot() != null && !Objects.equals(normalize(line.lot()), item.getLot())) fail("Snapshot lot cannot be changed");
                    if (line.expiryDate() != null && !Objects.equals(line.expiryDate(), item.getExpiryDate())) fail("Snapshot expiry cannot be changed");
                    Lpn lpn = lpns.findById(item.getLpnId()).orElseThrow();
                    StockMutationService.requireCode(line.lpnCode(), lpn.getTrackingCode(), "LPN");
                } else {
                    if (line.productId() == null) fail("A newly found item requires productId");
                    Product product = mutation.product(line.productId());
                    if (!Boolean.TRUE.equals(product.getActive())) fail("Product is inactive");
                    mutation.validateTrace(product, normalize(line.lot()), line.expiryDate());
                    Lpn lpn = mutation.getOrCreateLpn(line.lpnCode(), position);
                    if (lpn.getOutboundOrderId() != null) fail("Outbound LPN cannot be counted as newly found goods");
                    if (items.findByInventoryId(id).stream().anyMatch(i -> i.getProductId().equals(product.getId())
                            && i.getLpnId().equals(lpn.getId()) && Objects.equals(i.getLot(), normalize(line.lot()))
                            && Objects.equals(i.getExpiryDate(), line.expiryDate()))) fail("Count the existing inventory item instead of duplicating it");
                    item = new InventoryItem(); item.setInventoryId(id); item.setProductId(product.getId()); item.setLpnId(lpn.getId());
                    item.setLot(normalize(line.lot())); item.setExpiryDate(line.expiryDate()); item.setTheoreticalQuantity(BigDecimal.ZERO);
                }
                Product product = mutation.product(item.getProductId());
                StockMutationService.requireCode(line.scannedProductCode(), product.getSku(), "SKU");
                mutation.validateTrace(product, item.getLot(), item.getExpiryDate());
                item.setCountedQuantity(line.countedQuantity()); item.setCountedById(actor); item.setCountedAt(Instant.now()); items.saveAndFlush(item);
            }
            inventory.setStatus("EM_CONTAGEM"); inventories.save(inventory);
            return response(inventory);
        });
    }

    public Detail adjust(String id, AdjustRequest request, String key) {
        String actor = OperationService.currentActorId();
        return operations.execute(key, Map.of("inventoryId", id, "request", request), actor, "INVENTARIO_AJUSTAR", Detail.class, operationId -> {
            Inventory inventory = lock(id); requireOpen(inventory);
            Position position = mutation.position(inventory.getPositionId());
            StockMutationService.requireCode(request.scannedPositionCode(), position.getCode(), "position");
            ensureNoWork(position.getId());
            List<InventoryItem> lines = items.findByInventoryId(id);
            if (lines.stream().anyMatch(i -> i.getCountedQuantity() == null || i.getAppliedAt() != null)) fail("Every inventory item must have an unapplied count");
            List<Stock> physical = mutation.lockStocks(stocks.findByPositionId(position.getId()).stream().map(Stock::getId).toList());
            Set<String> captured = new HashSet<>();
            for (InventoryItem item : lines) {
                if (item.getStockId() == null) continue;
                captured.add(item.getStockId());
                Stock stock = physical.stream().filter(s -> s.getId().equals(item.getStockId())).findFirst()
                        .orElseThrow(() -> new BusinessRuleException("Snapshot stock no longer belongs to this position"));
                if (stock.getQuantity().compareTo(item.getTheoreticalQuantity()) != 0 || !Objects.equals(stock.getProductId(), item.getProductId()) || !Objects.equals(stock.getLpnId(), item.getLpnId())
                        || !Objects.equals(stock.getLot(), item.getLot()) || !Objects.equals(stock.getExpiryDate(), item.getExpiryDate())) fail("Inventory snapshot changed; cancel and recount");
                if (mutation.reserved(stock).signum() > 0) fail("Inventory stock has active reservations");
            }
            if (physical.stream().anyMatch(s -> s.getQuantity().signum() > 0 && !captured.contains(s.getId()))) fail("Unexpected stock appeared after the snapshot");
            BigDecimal finalWeight = BigDecimal.ZERO, finalVolume = BigDecimal.ZERO;
            for (InventoryItem item : lines) {
                Product product = mutation.lockProduct(item.getProductId());
                mutation.validateTrace(product, item.getLot(), item.getExpiryDate());
                finalWeight = finalWeight.add(item.getCountedQuantity().multiply(product.getUnitWeightKg()));
                finalVolume = finalVolume.add(item.getCountedQuantity().multiply(product.getUnitVolumeM3()));
            }
            if (position.getWeightCapacityKg() != null && finalWeight.compareTo(position.getWeightCapacityKg()) > 0
                    || position.getVolumeCapacityM3() != null && finalVolume.compareTo(position.getVolumeCapacityM3()) > 0) fail("Inventory would exceed position capacity");
            lines.sort(Comparator.comparing(i -> i.getCountedQuantity().subtract(i.getTheoreticalQuantity())));
            for (InventoryItem item : lines) {
                BigDecimal delta = item.getCountedQuantity().subtract(item.getTheoreticalQuantity());
                if (delta.signum() != 0) {
                    Stock stock;
                    if (item.getStockId() == null) {
                        stock = new Stock(); stock.setProductId(item.getProductId()); stock.setLpnId(item.getLpnId()); stock.setPositionId(position.getId());
                        stock.setLot(item.getLot()); stock.setExpiryDate(item.getExpiryDate()); stock.setEntryAt(Instant.now()); stock.setQuantity(BigDecimal.ZERO);
                        stocks.saveAndFlush(stock); mutation.refreshBlockStatus(stock); item.setStockId(stock.getId()); items.saveAndFlush(item);
                    } else stock = stocks.findById(item.getStockId()).orElseThrow();
                    mutation.adjustStock(stock, delta, actor, operationId, item.getId(), null, request.reason().trim());
                }
                item.setAppliedAt(Instant.now()); item.setApplicationOperationId(operationId); items.save(item);
            }
            inventory.setStatus("CONCLUIDO"); inventory.setCompletedAt(Instant.now()); inventories.save(inventory);
            return response(inventory);
        });
    }

    public Detail cancel(String id, CancelRequest request, String key) {
        String actor = OperationService.currentActorId();
        return operations.execute(key, Map.of("inventoryId", id, "request", request), actor, "INVENTARIO_CANCELAR", Detail.class, operationId -> {
            Inventory inventory = lock(id); requireOpen(inventory); requireOwner(inventory, actor, true);
            StockMutationService.requireCode(request.scannedPositionCode(), mutation.position(inventory.getPositionId()).getCode(), "position");
            inventory.setStatus("CANCELADO"); inventory.setCancelledAt(Instant.now()); inventories.save(inventory); return response(inventory);
        });
    }

    private Inventory lock(String id) {
        Inventory existing = require(id);
        mutation.lockWarehouse(existing.getWarehouseId()); mutation.lockPositions(List.of(existing.getPositionId()));
        Inventory locked = inventories.findByIdForUpdate(id).orElseThrow();
        entityManager.refresh(locked, LockModeType.PESSIMISTIC_WRITE);
        return locked;
    }
    private Inventory require(String id) { return inventories.findById(id).orElseThrow(() -> new ResourceNotFoundException("Inventory", id)); }
    private void requireOpen(Inventory inventory) { if (!ACTIVE.contains(inventory.getStatus())) fail("Inventory is already closed"); }
    private User requireResponsible(String id) {
        User user = users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
        if (!user.hasActiveAccess() || !user.getProfile().getPermissions().contains(WmsPermission.INVENTORY_EXECUTE)) fail("Responsible user must be active and allowed to count inventory");
        return user;
    }
    private void requireOwner(Inventory inventory, String actor, boolean allowManager) {
        boolean manager = allowManager && users.findById(actor).orElseThrow().getProfile().getPermissions().contains(WmsPermission.INVENTORY_ADJUST);
        if (!actor.equals(inventory.getResponsibleUserId()) && !manager) throw new AccessDeniedException("Only the responsible operator can perform this inventory action");
    }
    private void ensureNoWork(String positionId) {
        if (tasks.existsByOriginPositionIdAndStatusIn(positionId, ACTIVE_TASKS)
                || tasks.existsByDestinationPositionIdAndStatusIn(positionId, ACTIVE_TASKS)) fail("Cancel pending or running tasks before inventory");
    }
    private Summary summary(Inventory i) { return new Summary(i.getId(), i.getWarehouseId(), i.getPositionId(), i.getResponsibleUserId(), i.getStatus(), i.getCreatedAt(), i.getCompletedAt(), i.getCancelledAt()); }
    private Detail response(Inventory inventory) {
        return new Detail(summary(inventory), items.findByInventoryId(inventory.getId()).stream().sorted(Comparator.comparing(InventoryItem::getId)).map(i -> {
            Product p = mutation.product(i.getProductId()); Lpn lpn = lpns.findById(i.getLpnId()).orElseThrow();
            return new ItemResponse(i.getId(), i.getStockId(), i.getProductId(), p.getSku(), i.getLpnId(), lpn.getTrackingCode(), i.getLot(), i.getExpiryDate(), i.getTheoreticalQuantity(), i.getCountedQuantity(), i.getCountedById(), i.getCountedAt(), i.getAppliedAt(), i.getApplicationOperationId());
        }).toList());
    }
    private static String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static void fail(String message) { throw new BusinessRuleException(message); }
}
