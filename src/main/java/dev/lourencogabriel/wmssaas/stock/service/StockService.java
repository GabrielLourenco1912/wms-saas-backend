package dev.lourencogabriel.wmssaas.stock.service;

import dev.lourencogabriel.wmssaas.audit.model.StockTransaction;
import dev.lourencogabriel.wmssaas.audit.repository.StockTransactionRepository;
import dev.lourencogabriel.wmssaas.operation.service.OperationService;
import dev.lourencogabriel.wmssaas.product.model.Product;
import dev.lourencogabriel.wmssaas.stock.model.Lpn;
import dev.lourencogabriel.wmssaas.stock.model.Stock;
import dev.lourencogabriel.wmssaas.stock.model.StockBlock;
import dev.lourencogabriel.wmssaas.stock.repository.LpnRepository;
import dev.lourencogabriel.wmssaas.stock.repository.StockBlockRepository;
import dev.lourencogabriel.wmssaas.stock.repository.StockRepository;
import dev.lourencogabriel.wmssaas.stock.repository.StockReservationRepository;
import dev.lourencogabriel.wmssaas.task.model.TaskHistory;
import dev.lourencogabriel.wmssaas.task.model.WarehouseTask;
import dev.lourencogabriel.wmssaas.task.repository.TaskHistoryRepository;
import dev.lourencogabriel.wmssaas.task.repository.WarehouseTaskRepository;
import dev.lourencogabriel.wmssaas.warehouse.model.Position;
import dev.lourencogabriel.wmssaas.warehouse.repository.PositionRepository;

import dev.lourencogabriel.wmssaas.shared.api.PageResponse;
import dev.lourencogabriel.wmssaas.web.exception.BusinessRuleException;
import dev.lourencogabriel.wmssaas.web.exception.ResourceNotFoundException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static dev.lourencogabriel.wmssaas.operation.service.OperationService.currentActorId;
import static dev.lourencogabriel.wmssaas.stock.dto.StockDtos.*;

@Service
@RequiredArgsConstructor
@Transactional(isolation = Isolation.READ_COMMITTED)
public class StockService {
    private final StockRepository stocks;
    private final PositionRepository positions;
    private final LpnRepository lpns;
    private final StockBlockRepository blocks;
    private final StockReservationRepository reservations;
    private final StockTransactionRepository transactions;
    private final WarehouseTaskRepository tasks;
    private final TaskHistoryRepository histories;
    private final StockMutationService mutation;
    private final OperationService operations;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    @PreAuthorize("@wmsAuthorization.can(authentication, 'STOCK_VIEW')")
    public PageResponse<StockView> findStock(String productId, String positionId, String lpnId, String warehouseId, Pageable pageable) {
        Specification<Stock> spec = (root, query, cb) -> cb.conjunction();
        if (productId != null) spec = spec.and((r,q,b) -> b.equal(r.get("productId"), productId));
        if (positionId != null) spec = spec.and((r,q,b) -> b.equal(r.get("positionId"), positionId));
        if (lpnId != null) spec = spec.and((r,q,b) -> b.equal(r.get("lpnId"), lpnId));
        if (warehouseId != null) {
            List<String> ids = positions.findByWarehouseId(warehouseId).stream().map(Position::getId).toList();
            spec = spec.and((r,q,b) -> r.get("positionId").in(ids));
        }
        return PageResponse.from(stocks.findAll(spec, pageable).map(this::view));
    }
    @Transactional(readOnly = true)
    @PreAuthorize("@wmsAuthorization.can(authentication, 'STOCK_VIEW')")
    public StockView findStock(String id) { return view(stock(id)); }
    @Transactional(readOnly = true)
    @PreAuthorize("@wmsAuthorization.can(authentication, 'STOCK_VIEW')")
    public PageResponse<StockBlock> blocks(String warehouseId, Boolean active, Pageable pageable) {
        Specification<StockBlock> spec = (r, q, b) -> b.conjunction();
        if (warehouseId != null) spec = spec.and((r, q, b) -> b.equal(r.get("warehouseId"), warehouseId));
        if (active != null) spec = spec.and((r, q, b) -> b.equal(r.get("active"), active));
        return PageResponse.from(blocks.findAll(spec, pageable));
    }
    private Stock stock(String id) { return stocks.findById(id).orElseThrow(() -> new ResourceNotFoundException("Stock", id)); }
    public StockView view(Stock stock) {
        Product product = mutation.product(stock.getProductId());
        return new StockView(stock.getId(), product.getId(), product.getSku(), product.getName(), stock.getLpnId(),
                stock.getLpnId() == null ? null : lpns.findById(stock.getLpnId()).map(Lpn::getTrackingCode).orElse(null),
                stock.getPositionId(), mutation.position(stock.getPositionId()).getCode(), stock.getLot(), stock.getExpiryDate(), stock.getQuantity(),
                mutation.reserved(stock), mutation.available(stock), mutation.isBlocked(stock) ? "BLOQUEADO" : null, stock.getEntryAt());
    }
    @Transactional(readOnly = true)
    @PreAuthorize("@wmsAuthorization.can(authentication, 'AUDIT_VIEW')")
    public PageResponse<StockTransaction> audit(String stockId, String productId, String operationId, Pageable pageable) {
        Specification<StockTransaction> spec = (r,q,b) -> b.conjunction();
        if (stockId != null) spec = spec.and((r,q,b) -> b.or(b.equal(r.get("stockId"),stockId),b.equal(r.get("destinationStockId"),stockId)));
        if (productId != null) spec = spec.and((r,q,b) -> b.equal(r.get("productId"),productId));
        if (operationId != null) spec = spec.and((r,q,b) -> b.equal(r.get("operationId"),operationId));
        return PageResponse.from(transactions.findAll(spec,pageable));
    }
    @Transactional(readOnly = true)
    @PreAuthorize("@wmsAuthorization.can(authentication, 'AUDIT_VIEW')")
    public StockTransaction transaction(String id) {
        return transactions.findById(id).orElseThrow(() -> new ResourceNotFoundException("Stock transaction",id));
    }
    @PreAuthorize("@wmsAuthorization.can(authentication, 'STOCK_BLOCK')")
    public StockBlock block(String id, ReasonRequest request, String key) {
        String actor = currentActorId();
        return operations.execute(key,List.of(id,request),actor,"BLOQUEAR_ESTOQUE",StockBlock.class,op -> {
            mutation.lockWarehouse(mutation.warehouseForPosition(stock(id).getPositionId()));
            Stock stock = mutation.lockStock(id);
            StockBlock block = mutation.blockStock(id,request.reason(),actor,op);
            auditStatus(stock,"BLOQUEIO",actor,op); return block;
        });
    }
    @PreAuthorize("@wmsAuthorization.can(authentication, 'STOCK_UNBLOCK')")
    public StockView unblock(String id, ReasonRequest request, String key) {
        String actor = currentActorId();
        return operations.execute(key,List.of(id,request),actor,"LIBERAR_ESTOQUE",StockView.class,op -> {
            mutation.lockWarehouse(mutation.warehouseForPosition(stock(id).getPositionId()));
            Stock stock = mutation.lockStock(id);
            release(blocks.findByStockIdAndActive(id,true),actor);
            mutation.refreshBlockStatus(stock); auditStatus(stock,"DESBLOQUEIO",actor,op); return view(stock);
        });
    }
    @PreAuthorize("@wmsAuthorization.can(authentication, 'STOCK_BLOCK')")
    public StockBlock blockPosition(String id, ReasonRequest request, String key) {
        String actor = currentActorId();
        return operations.execute(key,List.of(id,request),actor,"BLOQUEAR_POSICAO",StockBlock.class,op -> {
            mutation.lockWarehouse(mutation.warehouseForPosition(id)); mutation.lockPositions(List.of(id));
            StockBlock block = mutation.createBlock("POSICAO",mutation.warehouseForPosition(id),null,null,null,id,request.reason(),actor);
            for (Stock stock : mutation.lockStocks(stocks.findByPositionId(id).stream().map(Stock::getId).toList())) {
                mutation.refreshBlockStatus(stock); auditStatus(stock,"BLOQUEIO",actor,op);
            }
            return block;
        });
    }
    @PreAuthorize("@wmsAuthorization.can(authentication, 'STOCK_UNBLOCK')")
    public MutationResult unblockPosition(String id, ReasonRequest request, String key) {
        String actor = currentActorId();
        return operations.execute(key,List.of(id,request),actor,"LIBERAR_POSICAO",MutationResult.class,op -> {
            mutation.lockWarehouse(mutation.warehouseForPosition(id)); mutation.lockPositions(List.of(id));
            // Inventory positions remain frozen until adjustment/cancellation closes the inventory.
            List<StockBlock> applicable = blocks.findByPositionIdAndActive(id,true);
            if (applicable.stream().anyMatch(b -> b.getReason().startsWith("INVENTARIO:"))) throw new BusinessRuleException("Finish or cancel the active inventory before releasing this position");
            release(applicable,actor);
            for (Stock stock : stocks.findByPositionId(id)) { mutation.refreshBlockStatus(stock); auditStatus(stock,"DESBLOQUEIO",actor,op); }
            return new MutationResult(id,"LIBERADA",null,null);
        });
    }
    @PreAuthorize("@wmsAuthorization.can(authentication, 'STOCK_BLOCK')")
    public StockBlock blockLot(LotBlockRequest request, String key) {
        String actor = currentActorId();
        return operations.execute(key,request,actor,"BLOQUEAR_LOTE",StockBlock.class,op -> {
            mutation.lockWarehouse(request.warehouseId()); mutation.product(request.productId());
            StockBlock block = mutation.createBlock("LOTE",request.warehouseId(),null,request.productId(),request.lot(),null,request.reason(),actor);
            for (Stock stock : stocks.findByProductIdAndLot(request.productId(),request.lot())) {
                if (request.warehouseId().equals(mutation.warehouseForPosition(stock.getPositionId()))) { mutation.refreshBlockStatus(stock); auditStatus(stock,"BLOQUEIO",actor,op); }
            }
            return block;
        });
    }
    @PreAuthorize("@wmsAuthorization.can(authentication, 'STOCK_UNBLOCK')")
    public MutationResult releaseBlock(String id, ReasonRequest request, String key) {
        String actor = currentActorId();
        return operations.execute(key,List.of(id,request),actor,"LIBERAR_BLOQUEIO",MutationResult.class,op -> {
            StockBlock initial = blocks.findById(id).orElseThrow(() -> new ResourceNotFoundException("Block",id));
            mutation.lockWarehouse(initial.getWarehouseId());
            StockBlock block = blocks.findByIdForUpdate(id).orElseThrow();
            if (block.getReason().startsWith("INVENTARIO:")) throw new BusinessRuleException("Finish or cancel the active inventory first");
            release(List.of(block),actor);
            for (Stock stock : stocks.findByPositionIdIn(positions.findByWarehouseId(block.getWarehouseId()).stream().map(Position::getId).toList())) {
                mutation.refreshBlockStatus(stock);
            }
            return new MutationResult(id,"LIBERADO",null,null);
        });
    }
    private void release(List<StockBlock> list, String actor) {
        list.forEach(block -> { block.setActive(false); block.setReleasedById(actor); block.setReleasedAt(Instant.now()); blocks.save(block); });
    }
    private void auditStatus(Stock stock, String type, String actor, String op) {
        mutation.append(stock,type,BigDecimal.ZERO,stock.getQuantity(),stock.getQuantity(),stock.getPositionId(),stock.getPositionId(),actor,op,null,null,null,null,null,null,null);
    }
    @Transactional(readOnly = true)
    @PreAuthorize("@wmsAuthorization.can(authentication, 'TRANSFER_VIEW')")
    public PageResponse<WarehouseTask> transfers(Pageable pageable) { return PageResponse.from(tasks.findByType("TRANSFERENCIA",pageable)); }
    @Transactional(readOnly = true)
    @PreAuthorize("@wmsAuthorization.can(authentication, 'TRANSFER_VIEW')")
    public WarehouseTask transfer(String id) { return task(id,"TRANSFERENCIA"); }
    @PreAuthorize("@wmsAuthorization.can(authentication, 'TRANSFER_EXECUTE')")
    public WarehouseTask createTransfer(TransferRequest request, String key) {
        String actor = currentActorId();
        return operations.execute(key,request,actor,"CRIAR_TRANSFERENCIA",WarehouseTask.class,op -> {
            if ((request.lpnId() == null) == (request.stockId() == null)) throw new BusinessRuleException("Specify exactly one LPN or stock balance");
            Lpn lpn; Stock stock = null;
            if (request.lpnId() != null) lpn = lpns.findById(request.lpnId()).orElseThrow(() -> new ResourceNotFoundException("LPN",request.lpnId()));
            else {
                stock = stock(request.stockId());
                if (stock.getLpnId() == null) throw new BusinessRuleException("Stock requires an LPN");
                lpn = lpns.findById(stock.getLpnId()).orElseThrow(() -> new BusinessRuleException("Stock requires an LPN"));
            }
            mutation.lockWarehouse(mutation.warehouseForPosition(lpn.getPositionId()));
            mutation.lockPositions(List.of(lpn.getPositionId(),request.destinationPositionId()));
            entityManager.refresh(lpn,LockModeType.PESSIMISTIC_WRITE);
            Position destination = mutation.position(request.destinationPositionId()); mutation.ensureDestination(destination);
            if (!mutation.warehouseForPosition(lpn.getPositionId()).equals(mutation.warehouseForPosition(destination.getId()))) throw new BusinessRuleException("Cross-warehouse transfer is not allowed");
            if (lpn.getOutboundOrderId() != null) {
                if (stock != null) throw new BusinessRuleException("Prepared goods must return as a complete LPN");
                mutation.ensureReturnableLpn(lpn);
            }
            BigDecimal quantity;
            Lpn destinationLpn = null;
            if (stock != null) {
                stock = mutation.lockStock(stock.getId()); mutation.ensureMovable(stock); quantity = request.quantity(); StockMutationService.positive(quantity);
                if (quantity.compareTo(stock.getQuantity().subtract(mutation.reserved(stock))) > 0) throw new BusinessRuleException("Quantity is unavailable");
                destinationLpn = mutation.getOrCreateLpn(request.destinationLpnCode(),destination);
                if (destinationLpn.getOutboundOrderId() != null) throw new BusinessRuleException("Outbound LPN requires the order flow");
                if (lpn.getId().equals(destinationLpn.getId())) throw new BusinessRuleException("A split transfer requires a different LPN");
            } else {
                List<Stock> balances = stocks.findByLpnId(lpn.getId());
                quantity = balances.stream().map(Stock::getQuantity).reduce(BigDecimal.ZERO,BigDecimal::add);
                if (request.quantity().compareTo(quantity) != 0) throw new BusinessRuleException("Whole-LPN quantity must match every linked balance");
                StockMutationService.positive(quantity);
                for (Stock balance : balances) if (balance.getQuantity().signum() > 0) {
                    mutation.ensureMovable(balance); if (mutation.reserved(balance).signum() > 0) throw new BusinessRuleException("LPN has reserved stock");
                }
            }
            WarehouseTask task = new WarehouseTask(); task.setType("TRANSFERENCIA"); task.setStatus("PENDENTE"); task.setPriority(1);
            task.setLpnId(lpn.getId()); task.setSourceStockId(stock == null ? null : stock.getId()); task.setProductId(stock == null ? null : stock.getProductId());
            task.setOriginPositionId(lpn.getPositionId()); task.setDestinationPositionId(destination.getId()); task.setDestinationLpnId(destinationLpn == null ? null : destinationLpn.getId());
            task.setTargetQuantity(quantity); task.setCreatedAt(Instant.now()); tasks.saveAndFlush(task); mutation.history(task,null,actor,op,"Internal transfer created"); return task;
        });
    }
    @PreAuthorize("@wmsAuthorization.can(authentication, 'TRANSFER_EXECUTE')")
    public WarehouseTask confirmTransfer(String id, ConfirmTransferRequest request, String key) {
        String actor = currentActorId();
        return operations.execute(key,List.of(id,request),actor,"EXECUTAR_TRANSFERENCIA",WarehouseTask.class,op -> {
            WarehouseTask task = task(id,"TRANSFERENCIA"); mutation.lockWarehouse(mutation.warehouseForPosition(task.getOriginPositionId()));
            entityManager.refresh(task,LockModeType.PESSIMISTIC_WRITE);
            if (!"PENDENTE".equals(task.getStatus())) throw new BusinessRuleException("Transfer is not pending");
            mutation.lockPositions(List.of(task.getOriginPositionId(),task.getDestinationPositionId()));
            Lpn source = lpns.findByIdForUpdate(task.getLpnId()).orElseThrow();
            if (!Objects.equals(source.getPositionId(),task.getOriginPositionId())) throw new BusinessRuleException("LPN moved since the transfer was planned; create a new transfer");
            StockMutationService.requireCode(request.scannedLpnCode(),source.getTrackingCode(),"LPN");
            StockMutationService.requireCode(request.scannedOriginCode(),mutation.position(task.getOriginPositionId()).getCode(),"origin position");
            StockMutationService.requireCode(request.scannedDestinationCode(),mutation.position(task.getDestinationPositionId()).getCode(),"destination position");
            StockMutationService.positive(request.actualQuantity());
            if (request.actualQuantity().compareTo(task.getTargetQuantity()) > 0) throw new BusinessRuleException("Quantity exceeds the planned transfer");
            mutation.transition(task,"EM_EXECUCAO",actor,op,null);
            if (task.getSourceStockId() == null) {
                BigDecimal current = stocks.findByLpnId(source.getId()).stream().map(Stock::getQuantity).reduce(BigDecimal.ZERO,BigDecimal::add);
                if (request.actualQuantity().compareTo(current) != 0 || current.compareTo(task.getTargetQuantity()) != 0) throw new BusinessRuleException("A whole-LPN transfer must move all its contents");
                mutation.moveWholeLpn(source,mutation.position(task.getDestinationPositionId()),actor,op,id,"TRANSFERENCIA");
            } else {
                Stock balance = mutation.lockStock(task.getSourceStockId());
                if (!Objects.equals(balance.getPositionId(),task.getOriginPositionId()) || !Objects.equals(balance.getLpnId(),source.getId())) throw new BusinessRuleException("Stock has changed since transfer creation");
                StockMutationService.requireCode(request.scannedProductCode(),mutation.product(balance.getProductId()).getSku(),"SKU");
                Lpn target = lpns.findByIdForUpdate(task.getDestinationLpnId()).orElseThrow();
                mutation.moveStock(balance,target,mutation.position(task.getDestinationPositionId()),request.actualQuantity(),actor,op,id,"TRANSFERENCIA");
            }
            task.setActualQuantity(request.actualQuantity()); mutation.transition(task,"CONCLUIDA",actor,op,null); return tasks.save(task);
        });
    }
    @Transactional(readOnly = true)
    @PreAuthorize("@wmsAuthorization.can(authentication, 'TASK_VIEW')")
    public PageResponse<WarehouseTask> tasks(String type, String status, String assignedUserId, Pageable pageable) {
        Specification<WarehouseTask> spec = (r,q,b) -> b.conjunction();
        if (type != null) spec = spec.and((r,q,b) -> b.equal(r.get("type"),type));
        if (status != null) spec = spec.and((r,q,b) -> b.equal(r.get("status"),status));
        if (assignedUserId != null) spec = spec.and((r,q,b) -> b.equal(r.get("assignedUserId"),assignedUserId));
        return PageResponse.from(tasks.findAll(spec,pageable));
    }
    public record TaskDetails(WarehouseTask task, List<TaskHistory> history) {}
    @Transactional(readOnly = true)
    @PreAuthorize("@wmsAuthorization.can(authentication, 'TASK_VIEW')")
    public TaskDetails taskDetails(String id) { return new TaskDetails(task(id,null),histories.findByTaskIdOrderByRecordedAtAsc(id)); }
    private WarehouseTask task(String id, String type) {
        return tasks.findById(id).filter(t -> type == null || type.equals(t.getType())).orElseThrow(() -> new ResourceNotFoundException("Task",id));
    }
    @PreAuthorize("@wmsAuthorization.can(authentication, 'TASK_CANCEL')")
    public WarehouseTask cancelTask(String id, ReasonRequest request, String key) {
        String actor = currentActorId();
        return operations.execute(key,List.of(id,request),actor,"CANCELAR_TAREFA",WarehouseTask.class,op -> {
            WarehouseTask task = task(id,null); mutation.lockWarehouse(mutation.warehouseForPosition(task.getOriginPositionId()));
            entityManager.refresh(task,LockModeType.PESSIMISTIC_WRITE);
            if (!"PENDENTE".equals(task.getStatus())) throw new BusinessRuleException("Only pending tasks can be cancelled");
            if (task.getSourceStockId() != null) mutation.lockStock(task.getSourceStockId());
            reservations.findByTaskId(id).ifPresent(r -> {
                r.setReleasedQuantity(r.getReleasedQuantity().add(mutation.remaining(r))); r.setStatus("ENCERRADA");
                r.setClosedAt(Instant.now()); r.setClosureReason(request.reason()); reservations.save(r);
            });
            mutation.transition(task,"CANCELADA",actor,op,request.reason()); return tasks.save(task);
        });
    }
    @PreAuthorize("@wmsAuthorization.can(authentication, 'INVENTORY_ADJUST')")
    public StockTransaction compensate(String id, CompensationRequest request, String key) {
        String actor = currentActorId();
        return operations.execute(key,List.of(id,request),actor,"COMPENSAR_MOVIMENTO",StockTransaction.class,op -> {
            StockTransaction original = transactionWithoutAuthorization(id); Stock stock = stock(original.getStockId());
            if (stock.getLpnId() == null) throw new BusinessRuleException("Reconcile unlabelled stock through inventory before compensation");
            mutation.lockWarehouse(mutation.warehouseForPosition(stock.getPositionId())); mutation.lockProduct(stock.getProductId());
            entityManager.refresh(stock);
            mutation.ensureNoActiveInventory(stock.getPositionId());
            if (stock.getLpnId() != null && lpns.findById(stock.getLpnId()).map(l -> "EXPEDIDO".equals(l.getStatus())).orElse(false)) {
                throw new BusinessRuleException("Dispatched goods must return through a documented receipt");
            }
            mutation.lockPositions(List.of(stock.getPositionId())); stock = mutation.lockStock(stock.getId());
            if (!Set.of("ENTRADA","SAIDA","AJUSTE","COMPENSACAO").contains(original.getMovementType()) || original.getQuantity().signum() == 0) throw new BusinessRuleException("Relocations must be corrected by a traceable reverse transfer");
            StockMutationService.requireCode(request.scannedPositionCode(),mutation.position(stock.getPositionId()).getCode(),"position");
            StockMutationService.requireCode(request.scannedLpnCode(),lpns.findById(stock.getLpnId()).orElseThrow().getTrackingCode(),"LPN");
            StockMutationService.requireCode(request.scannedProductCode(),mutation.product(stock.getProductId()).getSku(),"SKU");
            BigDecimal compensated = transactions.findByCompensatedTransactionId(id).stream().map(t -> t.getQuantity().abs()).reduce(BigDecimal.ZERO,BigDecimal::add);
            StockMutationService.positive(request.quantity());
            if (compensated.add(request.quantity()).compareTo(original.getQuantity().abs()) > 0) throw new BusinessRuleException("Compensation exceeds the remaining original quantity");
            BigDecimal delta = original.getQuantity().signum() > 0 ? request.quantity().negate() : request.quantity();
            return mutation.adjustStock(stock,delta,actor,op,null,id,request.reason());
        });
    }
    private StockTransaction transactionWithoutAuthorization(String id) {
        return transactions.findById(id).orElseThrow(() -> new ResourceNotFoundException("Stock transaction",id));
    }
}
