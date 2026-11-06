package dev.lourencogabriel.wmssaas.stock.service;

import dev.lourencogabriel.wmssaas.audit.model.StockTransaction;
import dev.lourencogabriel.wmssaas.audit.repository.StockTransactionRepository;
import dev.lourencogabriel.wmssaas.product.model.Product;
import dev.lourencogabriel.wmssaas.product.repository.ProductRepository;
import dev.lourencogabriel.wmssaas.stock.model.Lpn;
import dev.lourencogabriel.wmssaas.stock.model.Stock;
import dev.lourencogabriel.wmssaas.stock.model.StockBlock;
import dev.lourencogabriel.wmssaas.stock.model.StockReservation;
import dev.lourencogabriel.wmssaas.stock.repository.LpnRepository;
import dev.lourencogabriel.wmssaas.stock.repository.StockBlockRepository;
import dev.lourencogabriel.wmssaas.stock.repository.StockRepository;
import dev.lourencogabriel.wmssaas.stock.repository.StockReservationRepository;
import dev.lourencogabriel.wmssaas.task.model.TaskHistory;
import dev.lourencogabriel.wmssaas.task.model.WarehouseTask;
import dev.lourencogabriel.wmssaas.task.repository.TaskHistoryRepository;
import dev.lourencogabriel.wmssaas.warehouse.model.Position;
import dev.lourencogabriel.wmssaas.warehouse.model.Warehouse;
import dev.lourencogabriel.wmssaas.warehouse.model.Zone;
import dev.lourencogabriel.wmssaas.warehouse.repository.PositionRepository;
import dev.lourencogabriel.wmssaas.warehouse.repository.WarehouseRepository;
import dev.lourencogabriel.wmssaas.warehouse.repository.ZoneRepository;

import dev.lourencogabriel.wmssaas.web.exception.BusinessRuleException;
import dev.lourencogabriel.wmssaas.web.exception.ResourceNotFoundException;
import dev.lourencogabriel.wmssaas.inventory.repository.InventoryRepository;
import dev.lourencogabriel.wmssaas.order.repository.WarehouseOrderRepository;
import dev.lourencogabriel.wmssaas.order.repository.OrderItemRepository;
import dev.lourencogabriel.wmssaas.outbound.repository.ShipmentItemRepository;

import lombok.RequiredArgsConstructor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/** Shared transactional kernel. Callers acquire actor -> warehouse -> positions -> balances. */
@Service
@RequiredArgsConstructor
@Transactional(isolation = Isolation.READ_COMMITTED)
public class StockMutationService {
    private final EntityManager entityManager;
    private final WarehouseRepository warehouses;
    private final ZoneRepository zones;
    private final PositionRepository positions;
    private final ProductRepository products;
    private final LpnRepository lpns;
    private final StockRepository stocks;
    private final StockReservationRepository reservations;
    private final StockBlockRepository blocks;
    private final StockTransactionRepository transactions;
    private final TaskHistoryRepository histories;
    private final InventoryRepository inventories;
    private final WarehouseOrderRepository orders;
    private final OrderItemRepository orderItems;
    private final ShipmentItemRepository shipmentItems;

    public Warehouse lockWarehouse(String id) {
        Warehouse warehouse = warehouses.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Warehouse", id));
        entityManager.refresh(warehouse, LockModeType.PESSIMISTIC_WRITE);
        if (!Boolean.TRUE.equals(warehouse.getActive())) throw new BusinessRuleException("Warehouse is inactive");
        return warehouse;
    }
    public String warehouseForPosition(String id) {
        Position p = position(id);
        return zones.findById(p.getZoneId()).orElseThrow(() -> new ResourceNotFoundException("Zone", p.getZoneId())).getWarehouseId();
    }
    public Position position(String id) {
        return positions.findById(id).orElseThrow(() -> new ResourceNotFoundException("Position", id));
    }
    public Product lockProduct(String id) {
        Product product = products.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Product", id));
        entityManager.flush(); entityManager.refresh(product, LockModeType.PESSIMISTIC_WRITE); return product;
    }
    public Product product(String id) {
        return products.findById(id).orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }
    public Stock lockStock(String id) {
        Stock stock = stocks.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Stock", id));
        entityManager.flush(); entityManager.refresh(stock, LockModeType.PESSIMISTIC_WRITE); return stock;
    }
    public List<Stock> lockStocks(Collection<String> ids) {
        return ids.stream().filter(Objects::nonNull).distinct().sorted().map(this::lockStock).toList();
    }
    public List<Position> lockPositions(Collection<String> ids) {
        return ids.stream().filter(Objects::nonNull).distinct().sorted().map(id -> {
            Position position = positions.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Position", id));
            entityManager.flush();
            entityManager.refresh(position, LockModeType.PESSIMISTIC_WRITE);
            return position;
        }).toList();
    }
    public BigDecimal remaining(StockReservation reservation) {
        return reservation.getReservedQuantity().subtract(reservation.getConsumedQuantity()).subtract(reservation.getReleasedQuantity());
    }
    public BigDecimal reserved(Stock stock) {
        // A large wave may already have thousands of reservations for this layer.
        // Aggregate in SQL rather than loading that growing history for every item.
        return reservations.sumActiveRemainingByStockId(stock.getId());
    }
    public boolean isBlocked(Stock stock) {
        if (stock.getBlockStatus() != null && !stock.getBlockStatus().isBlank()) return true;
        String warehouseId = warehouseForPosition(stock.getPositionId());
        return applicableBlocks(stock, warehouseId).size() > 0 || isPositionUnavailable(position(stock.getPositionId()));
    }
    public BigDecimal available(Stock stock) {
        Position position = position(stock.getPositionId());
        if (!"ARMAZENAGEM".equals(position.getPurpose()) || isBlocked(stock) || !Boolean.TRUE.equals(product(stock.getProductId()).getActive())) return BigDecimal.ZERO;
        if (stock.getLpnId() == null || lpns.findById(stock.getLpnId()).map(l -> l.getOutboundOrderId() != null
                || "EXPEDIDO".equals(l.getStatus()) || !Objects.equals(l.getPositionId(), stock.getPositionId())).orElse(true)) return BigDecimal.ZERO;
        return stock.getQuantity().subtract(reserved(stock)).max(BigDecimal.ZERO);
    }
    public void ensureMovable(Stock stock) {
        if (isBlocked(stock)) throw new BusinessRuleException("Stock or position is blocked");
        if (stock.getQuantity().signum() <= 0) throw new BusinessRuleException("There is no physical stock to move");
        if (stock.getLpnId() == null || lpns.findById(stock.getLpnId()).map(l -> "EXPEDIDO".equals(l.getStatus())
                || !Objects.equals(stock.getPositionId(), l.getPositionId())).orElse(true)) {
            throw new BusinessRuleException("Stock requires an active LPN in the same position");
        }
    }
    public void ensureDestination(Position position) {
        if (isPositionUnavailable(position) || !blocks.findByPositionIdAndActive(position.getId(), true).isEmpty()) {
            throw new BusinessRuleException("Destination position is unavailable or blocked");
        }
        Zone zone = zones.findById(position.getZoneId()).orElseThrow(() -> new ResourceNotFoundException("Zone", position.getZoneId()));
        if (!Boolean.TRUE.equals(zone.getActive())) throw new BusinessRuleException("Destination zone is inactive");
    }
    private boolean isPositionUnavailable(Position p) {
        return !Boolean.TRUE.equals(p.getActive()) || p.getStatus() == null || p.getStatus().startsWith("BLOQUE") || p.getStatus().startsWith("INATIV")
                || inventories.existsByPositionIdAndStatusIn(p.getId(), List.of("PENDENTE", "EM_CONTAGEM"));
    }
    public void ensureNoActiveInventory(String positionId) {
        if (inventories.existsByPositionIdAndStatusIn(positionId,List.of("PENDENTE","EM_CONTAGEM"))) {
            throw new BusinessRuleException("Finish or cancel the active inventory before moving or compensating this position");
        }
    }
    public void requireCapacity(Position position, BigDecimal addedWeight, BigDecimal addedVolume) {
        BigDecimal weight = BigDecimal.ZERO, volume = BigDecimal.ZERO;
        for (Stock stock : stocks.findByPositionId(position.getId())) {
            Product product = product(stock.getProductId());
            weight = weight.add(stock.getQuantity().multiply(product.getUnitWeightKg()));
            volume = volume.add(stock.getQuantity().multiply(product.getUnitVolumeM3()));
        }
        if ((position.getWeightCapacityKg() != null && weight.add(addedWeight).compareTo(position.getWeightCapacityKg()) > 0)
                || (position.getVolumeCapacityM3() != null && volume.add(addedVolume).compareTo(position.getVolumeCapacityM3()) > 0)) {
            throw new BusinessRuleException("Position weight or volume capacity would be exceeded");
        }
    }
    public void validateTrace(Product product, String lot, LocalDate expiryDate) {
        if (Boolean.TRUE.equals(product.getLotControlled()) && (lot == null || lot.isBlank())) {
            throw new BusinessRuleException("The product requires a lot");
        }
        if (Boolean.TRUE.equals(product.getExpiryControlled()) && expiryDate == null) {
            throw new BusinessRuleException("The product requires an expiry date");
        }
    }
    public static void positive(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0 || quantity.scale() > 6 || quantity.precision() - quantity.scale() > 12) {
            throw new BusinessRuleException("A positive quantity with at most six decimal places is required");
        }
    }
    public static void requireCode(String scanned, String expected, String label) {
        if (scanned == null || expected == null || !scanned.trim().equals(expected)) {
            throw new BusinessRuleException("The scanned " + label + " does not match the operation");
        }
    }
    public Lpn getOrCreateLpn(String code, Position position) {
        if (code == null || code.isBlank() || code.length() > 100) throw new BusinessRuleException("A valid LPN code is required");
        var existing = lpns.findByTrackingCode(code.trim());
        if (existing.isPresent()) {
            Lpn lpn = lpns.findByIdForUpdate(existing.get().getId()).orElseThrow();
            if (!Objects.equals(position.getId(), lpn.getPositionId()) || "EXPEDIDO".equals(lpn.getStatus())) {
                throw new BusinessRuleException("LPN belongs to another position or has already been dispatched");
            }
            return lpn;
        }
        Lpn lpn = new Lpn(); lpn.setTrackingCode(code.trim()); lpn.setPositionId(position.getId());
        lpn.setStatus("ATIVO"); lpn.setCreatedAt(Instant.now()); lpn.setUpdatedAt(Instant.now());
        return lpns.saveAndFlush(lpn);
    }
    public Stock receive(Product product, Lpn lpn, Position dock, BigDecimal quantity, String lot, LocalDate expiryDate,
                         String confirmationId, String userId, String operationId) {
        positive(quantity); validateTrace(product, lot, expiryDate); ensureDestination(dock);
        if (lpn.getOutboundOrderId() != null) throw new BusinessRuleException("An outbound LPN cannot receive inbound goods");
        requireCapacity(dock, quantity.multiply(product.getUnitWeightKg()), quantity.multiply(product.getUnitVolumeM3()));
        Stock stock = new Stock(); stock.setProductId(product.getId()); stock.setLpnId(lpn.getId());
        stock.setPositionId(dock.getId()); stock.setQuantity(quantity); stock.setLot(lot); stock.setExpiryDate(expiryDate);
        stock.setEntryAt(Instant.now()); stock.setOriginConfirmationId(confirmationId); stock.setUpdatedAt(Instant.now());
        stocks.saveAndFlush(stock); refreshBlockStatus(stock);
        append(stock, "ENTRADA", quantity, BigDecimal.ZERO, quantity, null, dock.getId(), userId, operationId, null, null, null, null, null, null, null);
        return stock;
    }
    /** Keeps each receipt layer separate; picking only relocates stock, without warehouse-level depletion. */
    public Stock moveStock(Stock source, Lpn destinationLpn, Position destination, BigDecimal quantity,
                           String userId, String operationId, String taskId, String movementType) {
        positive(quantity); ensureMovable(source); ensureDestination(destination);
        if (!warehouseForPosition(source.getPositionId()).equals(warehouseForPosition(destination.getId()))) {
            throw new BusinessRuleException("Internal transfers must remain in the same warehouse");
        }
        if (Objects.equals(source.getLpnId(), destinationLpn.getId())) {
            throw new BusinessRuleException("Moving the same LPN requires the complete-LPN operation");
        }
        if (!Objects.equals(destinationLpn.getPositionId(), destination.getId()) || "EXPEDIDO".equals(destinationLpn.getStatus())) {
            throw new BusinessRuleException("Destination LPN does not belong to the destination position");
        }
        BigDecimal otherReserved = reservations.findByStockIdAndStatus(source.getId(), "ATIVA").stream()
                .filter(r -> !Objects.equals(taskId, r.getTaskId())).map(this::remaining).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (quantity.compareTo(source.getQuantity().subtract(otherReserved)) > 0) throw new BusinessRuleException("Stock is insufficient or reserved by another task");
        Product product = product(source.getProductId());
        if (!source.getPositionId().equals(destination.getId())) {
            requireCapacity(destination, quantity.multiply(product.getUnitWeightKg()), quantity.multiply(product.getUnitVolumeM3()));
        }
        BigDecimal before = source.getQuantity(); source.setQuantity(before.subtract(quantity)); stocks.save(source);
        Stock target = new Stock(); target.setProductId(source.getProductId()); target.setLpnId(destinationLpn.getId());
        target.setPositionId(destination.getId()); target.setLot(source.getLot()); target.setExpiryDate(source.getExpiryDate());
        target.setEntryAt(source.getEntryAt()); target.setOriginConfirmationId(source.getOriginConfirmationId());
        target.setQuantity(quantity); target.setUpdatedAt(Instant.now()); stocks.saveAndFlush(target); refreshBlockStatus(target);
        append(source, movementType, quantity, before, source.getQuantity(), source.getPositionId(), destination.getId(), userId,
                operationId, taskId, target, BigDecimal.ZERO, quantity, null, null, null);
        return target;
    }
    /** RN004: one physical LPN has one position, including every positive balance linked to it. */
    public void moveWholeLpn(Lpn lpn, Position destination, String userId, String operationId, String taskId, String type) {
        ensureDestination(destination);
        if (Objects.equals(lpn.getPositionId(), destination.getId())) throw new BusinessRuleException("Origin and destination positions are equal");
        boolean returning = lpn.getOutboundOrderId() != null;
        if (returning) {
            ensureReturnableLpn(lpn);
            if (!"ARMAZENAGEM".equals(destination.getPurpose())) throw new BusinessRuleException("Cancelled outbound goods must return to storage");
        }
        String sourcePosition = lpn.getPositionId();
        if (sourcePosition == null || !warehouseForPosition(sourcePosition).equals(warehouseForPosition(destination.getId()))) {
            throw new BusinessRuleException("Internal transfers must remain in the same warehouse");
        }
        List<Stock> balances = stocks.findByLpnIdForUpdate(lpn.getId());
        if (balances.stream().noneMatch(s -> s.getQuantity().signum() > 0)) throw new BusinessRuleException("LPN has no physical stock");
        BigDecimal weight = BigDecimal.ZERO, volume = BigDecimal.ZERO;
        for (Stock stock : balances) {
            if (!Objects.equals(sourcePosition, stock.getPositionId()) && stock.getQuantity().signum() > 0) throw new BusinessRuleException("LPN has inconsistent positions");
            if (stock.getQuantity().signum() > 0) {
                ensureMovable(stock);
                if (reserved(stock).signum() > 0) throw new BusinessRuleException("A reserved LPN cannot be transferred");
                Product product = product(stock.getProductId());
                weight = weight.add(stock.getQuantity().multiply(product.getUnitWeightKg()));
                volume = volume.add(stock.getQuantity().multiply(product.getUnitVolumeM3()));
            }
        }
        requireCapacity(destination, weight, volume);
        if (returning) {
            for (var item : orderItems.findByOrderId(lpn.getOutboundOrderId())) {
                BigDecimal quantity = balances.stream().filter(s -> item.getProductId().equals(s.getProductId()))
                        .map(Stock::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
                if (quantity.compareTo(item.getPickedQuantity()) > 0) throw new BusinessRuleException("Return exceeds the prepared order quantity");
                item.setPickedQuantity(item.getPickedQuantity().subtract(quantity));
                orderItems.save(item);
            }
        }
        for (Stock stock : balances) {
            String beforePosition = stock.getPositionId(); stock.setPositionId(destination.getId()); stocks.save(stock); refreshBlockStatus(stock);
            if (stock.getQuantity().signum() > 0) append(stock, returning ? "RETORNO" : type, stock.getQuantity(), stock.getQuantity(), stock.getQuantity(), beforePosition,
                    destination.getId(), userId, operationId, taskId, null, null, null, null, null, null);
        }
        lpn.setPositionId(destination.getId()); lpn.setUpdatedAt(Instant.now());
        if (returning) { lpn.setOutboundOrderId(null); lpn.setStatus("ATIVO"); }
        lpns.save(lpn);
    }
    public void ensureReturnableLpn(Lpn lpn) {
        var order = orders.findById(lpn.getOutboundOrderId()).orElseThrow(() -> new BusinessRuleException("Outbound order is unavailable"));
        if (!"CANCELADO".equals(order.getStatus()) || "EXPEDIDO".equals(lpn.getStatus())) {
            throw new BusinessRuleException("Cancel the outbound order before returning prepared goods");
        }
        if (shipmentItems.findByLpnId(lpn.getId()).stream().anyMatch(i -> !"CANCELADO".equals(i.getStatus()))) {
            throw new BusinessRuleException("Cancel active shipments before returning prepared goods");
        }
    }
    public void dispatchBalance(Stock stock, BigDecimal quantity, String userId, String operationId, String taskId, String shipmentItemId) {
        positive(quantity); ensureMovable(stock);
        if (!"EXPEDICAO".equals(position(stock.getPositionId()).getPurpose())) throw new BusinessRuleException("Shipment stock must be on the dispatch dock");
        if (quantity.compareTo(stock.getQuantity().subtract(reserved(stock))) > 0) throw new BusinessRuleException("Dispatch quantity is unavailable");
        BigDecimal before = stock.getQuantity(); stock.setQuantity(before.subtract(quantity)); stocks.save(stock);
        append(stock, "SAIDA", quantity.negate(), before, stock.getQuantity(), stock.getPositionId(), null, userId, operationId,
                taskId, null, null, null, null, shipmentItemId, null);
    }
    public StockTransaction adjustStock(Stock stock, BigDecimal delta, String userId, String operationId, String inventoryItemId,
                                       String compensatedId, String reason) {
        if (delta == null || delta.signum() == 0) throw new BusinessRuleException("An adjustment must change stock");
        if (reason == null || reason.isBlank()) throw new BusinessRuleException("An adjustment reason is required");
        positive(delta.abs()); Product product = product(stock.getProductId());
        validateTrace(product, stock.getLot(), stock.getExpiryDate());
        BigDecimal before = stock.getQuantity(), after = before.add(delta);
        if (after.signum() < 0 || after.compareTo(reserved(stock)) < 0) throw new BusinessRuleException("Adjustment would consume reserved or unavailable stock");
        if (delta.signum() > 0) requireCapacity(position(stock.getPositionId()), delta.multiply(product.getUnitWeightKg()), delta.multiply(product.getUnitVolumeM3()));
        stock.setQuantity(after); stocks.saveAndFlush(stock);
        return append(stock, compensatedId == null ? "AJUSTE" : "COMPENSACAO", delta, before, after, stock.getPositionId(), stock.getPositionId(),
                userId, operationId, null, null, null, null, inventoryItemId, null, compensatedId, reason);
    }
    public StockTransaction append(Stock stock, String type, BigDecimal quantity, BigDecimal previous, BigDecimal resulting,
                                   String originPosition, String destinationPosition, String userId, String operationId, String taskId,
                                   Stock destinationStock, BigDecimal destinationPrevious, BigDecimal destinationResulting,
                                   String inventoryItemId, String shipmentItemId, String compensatedId) {
        return append(stock,type,quantity,previous,resulting,originPosition,destinationPosition,userId,operationId,taskId,
                destinationStock,destinationPrevious,destinationResulting,inventoryItemId,shipmentItemId,compensatedId,null);
    }
    private StockTransaction append(Stock stock, String type, BigDecimal quantity, BigDecimal previous, BigDecimal resulting,
                                    String originPosition, String destinationPosition, String userId, String operationId, String taskId,
                                    Stock destinationStock, BigDecimal destinationPrevious, BigDecimal destinationResulting,
                                    String inventoryItemId, String shipmentItemId, String compensatedId, String reason) {
        StockTransaction entry = new StockTransaction(); entry.setProductId(stock.getProductId()); entry.setUserId(userId);
        entry.setTaskId(taskId); entry.setLpnId(stock.getLpnId()); entry.setMovementType(type); entry.setQuantity(quantity);
        entry.setPreviousPositionId(originPosition); entry.setNewPositionId(destinationPosition); entry.setPreviousBalance(previous);
        entry.setResultingBalance(resulting); entry.setOccurredAt(Instant.now()); entry.setOperationId(operationId);
        entry.setSequence(transactions.findByOperationIdOrderBySequenceAsc(operationId).size() + 1); entry.setStockId(stock.getId());
        entry.setRecordedLot(stock.getLot()); entry.setRecordedExpiryDate(stock.getExpiryDate()); entry.setRecordedEntryAt(stock.getEntryAt());
        if (destinationStock != null) { entry.setDestinationStockId(destinationStock.getId()); entry.setDestinationLpnId(destinationStock.getLpnId()); }
        entry.setDestinationPreviousBalance(destinationPrevious); entry.setDestinationResultingBalance(destinationResulting);
        entry.setCompensatedTransactionId(compensatedId); entry.setInventoryItemId(inventoryItemId); entry.setShipmentItemId(shipmentItemId);
        entry.setReason(reason); return transactions.save(entry);
    }
    public void transition(WarehouseTask task, String status, String userId, String operationId, String reason) {
        String before = task.getStatus(); boolean valid = switch (before) {
            case "PENDENTE" -> Set.of("EM_EXECUCAO", "CANCELADA").contains(status);
            case "EM_EXECUCAO" -> Set.of("CONCLUIDA", "DIVERGENTE").contains(status);
            default -> false;
        };
        if (!valid) throw new BusinessRuleException("Invalid task transition: " + before + " -> " + status);
        if ("EM_EXECUCAO".equals(status)) { task.setStartedAt(Instant.now()); task.setAssignedUserId(userId); }
        if (Set.of("CONCLUIDA", "DIVERGENTE").contains(status)) task.setCompletedAt(Instant.now());
        if ("CANCELADA".equals(status)) { task.setCancelledAt(Instant.now()); task.setCancelledById(userId); task.setCancellationReason(reason); }
        task.setStatus(status); history(task, before, userId, operationId, reason);
    }
    public void history(WarehouseTask task, String before, String userId, String operationId, String reason) {
        TaskHistory history = new TaskHistory(); history.setTaskId(task.getId()); history.setPreviousStatus(before); history.setNewStatus(task.getStatus());
        history.setUserId(userId); history.setOperationId(operationId); history.setReason(reason); history.setRecordedAt(Instant.now()); histories.save(history);
    }
    public StockBlock blockStock(String stockId, String reason, String userId, String operationId) {
        Stock stock = lockStock(stockId); String warehouse = warehouseForPosition(stock.getPositionId());
        StockBlock block = createBlock("ESTOQUE", warehouse, stockId, null, null, null, reason, userId);
        refreshBlockStatus(stock); return block;
    }
    public StockBlock createBlock(String type, String warehouseId, String stockId, String productId, String lot,
                                  String positionId, String reason, String userId) {
        if (reason == null || reason.isBlank() || reason.length() > 255) throw new BusinessRuleException("A block reason is required");
        StockBlock block = new StockBlock(); block.setWarehouseId(warehouseId); block.setType(type); block.setStockId(stockId);
        block.setProductId(productId); block.setLot(lot); block.setPositionId(positionId); block.setReason(reason);
        block.setActive(true); block.setCreatedById(userId); block.setCreatedAt(Instant.now()); return blocks.saveAndFlush(block);
    }
    public void refreshBlockStatus(Stock stock) {
        List<StockBlock> applicable = applicableBlocks(stock, warehouseForPosition(stock.getPositionId()));
        stock.setBlockStatus(applicable.isEmpty() ? null : "BLOQUEADO"); stocks.save(stock);
    }
    private List<StockBlock> applicableBlocks(Stock stock, String warehouseId) {
        return blocks.findByWarehouseIdAndActive(warehouseId, true).stream().filter(b ->
                ("ESTOQUE".equals(b.getType()) && Objects.equals(stock.getId(), b.getStockId()))
                || ("POSICAO".equals(b.getType()) && Objects.equals(stock.getPositionId(), b.getPositionId()))
                || ("LOTE".equals(b.getType()) && Objects.equals(stock.getProductId(), b.getProductId()) && Objects.equals(stock.getLot(), b.getLot()))).toList();
    }
}
