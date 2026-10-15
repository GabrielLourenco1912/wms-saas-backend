package dev.lourencogabriel.wmssaas.stock.service;

import dev.lourencogabriel.wmssaas.audit.dto.TransactionDtos;
import dev.lourencogabriel.wmssaas.audit.model.StockTransaction;
import dev.lourencogabriel.wmssaas.audit.repository.StockTransactionRepository;
import dev.lourencogabriel.wmssaas.order.model.WarehouseOrder;
import dev.lourencogabriel.wmssaas.product.repository.ProductRepository;
import dev.lourencogabriel.wmssaas.product.model.Product;
import dev.lourencogabriel.wmssaas.shared.api.*;
import dev.lourencogabriel.wmssaas.stock.dto.*;
import dev.lourencogabriel.wmssaas.stock.model.*;
import dev.lourencogabriel.wmssaas.stock.repository.*;
import dev.lourencogabriel.wmssaas.task.dto.TaskDtos;
import dev.lourencogabriel.wmssaas.task.dto.TaskHistoryDtos;
import dev.lourencogabriel.wmssaas.task.model.*;
import dev.lourencogabriel.wmssaas.task.repository.*;
import dev.lourencogabriel.wmssaas.warehouse.model.Position;
import dev.lourencogabriel.wmssaas.warehouse.model.Zone;
import dev.lourencogabriel.wmssaas.warehouse.repository.PositionRepository;
import dev.lourencogabriel.wmssaas.web.exception.ResourceNotFoundException;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Arrays;

import static dev.lourencogabriel.wmssaas.shared.persistence.SearchSpecifications.*;

/** All collection predicates, counts and page limits run in the database; lookup mapping sees only its requested page. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly=true)
public class StockQueryService {
    private final StockRepository stocks;
    private final StockBlockRepository blocks;
    private final LpnRepository lpns;
    private final WarehouseTaskRepository tasks;
    private final TaskHistoryRepository histories;
    private final StockTransactionRepository transactions;
    private final ProductRepository products;
    private final PositionRepository positions;
    private final StockService stockService;

    @PreAuthorize("@wmsAuthorization.can(authentication, 'STOCK_VIEW')")
    public PageResponse<StockDtos.StockView> balances(PageQuery query,StockSearchFilters.Balances filters) {
        return PageResponse.from(stocks.findAll(balanceSpec(query,filters),query.toPageable()).map(stockService::view));
    }
    @PreAuthorize("@wmsAuthorization.can(authentication, 'STOCK_VIEW')")
    public PageResponse<LookupOption> balanceLookup(LookupQuery query,StockSearchFilters.Balances filters) {
        return PageResponse.from(stocks.findAll(balanceSpec(query,filters),query.toPageable()).map(stock -> {
            String sku=products.findById(stock.getProductId()).map(p -> p.getSku()).orElse(stock.getProductId());
            String lpn=stock.getLpnId()==null?"":lpns.findById(stock.getLpnId()).map(Lpn::getTrackingCode).orElse(stock.getLpnId());
            String position=positions.findById(stock.getPositionId()).map(Position::getCode).orElse(stock.getPositionId());
            return new LookupOption(stock.getId(),sku+" | "+lpn+" | "+position+(stock.getLot()==null?"":" | "+stock.getLot()));
        }));
    }
    private Specification<Stock> balanceSpec(PageQuery query,StockSearchFilters.Balances f) {
        return balanceText(query.getQ())
                .and(equal("productId",f.getProductId())).and(equal("positionId",f.getPositionId()))
                .and(equal("lpnId",f.getLpnId())).and(equal("lot",f.getLot())).and(equal("blockStatus",f.getBlockStatus()))
                .and(inWarehouse(f.getWarehouseId(),"positionId"));
    }
    private Specification<Stock> balanceText(String q) {
        if (q==null || q.isBlank()) return all();
        return StockQueryService.<Stock>match(q,"id","lot")
                .or(relatedText("productId",Product.class,q,"sku","name"))
                .or(relatedText("lpnId",Lpn.class,q,"trackingCode"))
                .or(relatedText("positionId",Position.class,q,"code"));
    }
    private static <T,R> Specification<T> relatedText(String foreignKey,Class<R> relatedClass,String text,String... fields) {
        return (root,query,builder) -> {
            Subquery<String> sub=query.subquery(String.class);Root<R> related=sub.from(relatedClass);
            sub.select(related.get("id")).where(dev.lourencogabriel.wmssaas.shared.persistence.SearchSpecifications.<R>text(text,fields).toPredicate(related,query,builder));
            return root.get(foreignKey).in(sub);
        };
    }
    @PreAuthorize("@wmsAuthorization.can(authentication, 'STOCK_VIEW')")
    public PageResponse<StockBlockDtos.Response> blocks(PageQuery query,StockSearchFilters.Blocks filters) {
        return PageResponse.from(blocks.findAll(blockSpec(query,filters),query.toPageable()).map(StockBlockDtos.Response::from));
    }
    @PreAuthorize("@wmsAuthorization.can(authentication, 'STOCK_VIEW')")
    public PageResponse<LookupOption> blockLookup(LookupQuery query,StockSearchFilters.Blocks filters) {
        return PageResponse.from(blocks.findAll(blockSpec(query,filters),query.toPageable())
                .map(b -> new LookupOption(b.getId(),b.getType()+" | "+b.getReason())));
    }
    private Specification<StockBlock> blockSpec(PageQuery query,StockSearchFilters.Blocks f) {
        return StockQueryService.<StockBlock>match(query.getQ(),"id","type","lot","reason")
                .and(equal("warehouseId",f.getWarehouseId())).and(equal("active",f.getActive())).and(equal("type",f.getType()))
                .and(equal("stockId",f.getStockId())).and(equal("productId",f.getProductId()))
                .and(equal("positionId",f.getPositionId())).and(equal("lot",f.getLot()));
    }
    @PreAuthorize("@wmsAuthorization.can(authentication, 'TASK_VIEW')")
    public PageResponse<TaskDtos.Response> tasks(PageQuery query,StockSearchFilters.Tasks filters) {
        return PageResponse.from(tasks.findAll(taskSpec(query,filters,null),query.toPageable()).map(TaskDtos.Response::from));
    }
    @PreAuthorize("@wmsAuthorization.can(authentication, 'TASK_VIEW')")
    public PageResponse<LookupOption> taskLookup(LookupQuery query,StockSearchFilters.Tasks filters) {
        return taskOptions(query,filters,null);
    }
    @PreAuthorize("@wmsAuthorization.can(authentication, 'TRANSFER_VIEW')")
    public PageResponse<TaskDtos.Response> transfers(PageQuery query,StockSearchFilters.Tasks filters) {
        return PageResponse.from(tasks.findAll(taskSpec(query,filters,"TRANSFERENCIA"),query.toPageable()).map(TaskDtos.Response::from));
    }
    @PreAuthorize("@wmsAuthorization.can(authentication, 'TRANSFER_VIEW')")
    public PageResponse<LookupOption> transferLookup(LookupQuery query,StockSearchFilters.Tasks filters) {
        return taskOptions(query,filters,"TRANSFERENCIA");
    }
    private PageResponse<LookupOption> taskOptions(PageQuery query,StockSearchFilters.Tasks filters,String forcedType) {
        return PageResponse.from(tasks.findAll(taskSpec(query,filters,forcedType),query.toPageable())
                .map(task -> new LookupOption(task.getId(),task.getType()+" | "+task.getStatus()+" | "+task.getId())));
    }
    private Specification<WarehouseTask> taskSpec(PageQuery query,StockSearchFilters.Tasks f,String forcedType) {
        return StockQueryService.<WarehouseTask>match(query.getQ(),"id","type","status","cancellationReason")
                .and(equal("type",forcedType)).and(equal("type",f.getType())).and(equal("status",f.getStatus()))
                .and(equal("assignedUserId",f.getAssignedUserId())).and(equal("orderId",f.getOrderId()))
                .and(equal("productId",f.getProductId())).and(equal("lpnId",f.getLpnId())).and(equal("waveId",f.getWaveId()))
                .and(equal("originPositionId",f.getOriginPositionId())).and(equal("destinationPositionId",f.getDestinationPositionId()))
                .and(inWarehouse(f.getWarehouseId(),"originPositionId","destinationPositionId"));
    }
    @PreAuthorize("@wmsAuthorization.can(authentication, 'TASK_VIEW')")
    public PageResponse<TaskHistoryDtos.Response> history(String id,PageQuery query) {
        if (!tasks.existsById(id)) throw new ResourceNotFoundException("Task",id);
        Specification<TaskHistory> specification=StockQueryService.<TaskHistory>match(query.getQ(),"id","previousStatus","newStatus","reason")
                .and(equal("taskId",id));
        return PageResponse.from(histories.findAll(specification,query.toPageable()).map(TaskHistoryDtos.Response::from));
    }
    @PreAuthorize("@wmsAuthorization.can(authentication, 'AUDIT_VIEW')")
    public PageResponse<TransactionDtos.Response> ledger(PageQuery query,StockSearchFilters.Ledger filters) {
        return PageResponse.from(transactions.findAll(ledgerSpec(query,filters),query.toPageable()).map(TransactionDtos.Response::from));
    }
    @PreAuthorize("@wmsAuthorization.can(authentication, 'AUDIT_VIEW')")
    public PageResponse<LookupOption> ledgerLookup(LookupQuery query,StockSearchFilters.Ledger filters) {
        return PageResponse.from(transactions.findAll(ledgerSpec(query,filters),query.toPageable())
                .map(t -> new LookupOption(t.getId(),t.getMovementType()+" | "+t.getOccurredAt()+" | "+t.getId())));
    }
    private Specification<StockTransaction> ledgerSpec(PageQuery query,StockSearchFilters.Ledger f) {
        Specification<StockTransaction> specification=StockQueryService.<StockTransaction>match(query.getQ(),"id","recordedLot","reason")
                .and(equal("productId",f.getProductId())).and(equal("operationId",f.getOperationId()))
                .and(equal("movementType",f.getMovementType())).and(equal("userId",f.getUserId()))
                .and(equal("recordedLot",f.getLot()))
                .and(inWarehouse(f.getWarehouseId(),"previousPositionId","newPositionId"));
        if (f.getStockId()!=null && !f.getStockId().isBlank()) specification=specification.and((r,q,b) ->
                b.or(b.equal(r.get("stockId"),f.getStockId().trim()),b.equal(r.get("destinationStockId"),f.getStockId().trim())));
        if (f.getLpnId()!=null && !f.getLpnId().isBlank()) specification=specification.and((r,q,b) ->
                b.or(b.equal(r.get("lpnId"),f.getLpnId().trim()),b.equal(r.get("destinationLpnId"),f.getLpnId().trim())));
        if (f.getFrom()!=null) specification=specification.and((r,q,b) -> b.greaterThanOrEqualTo(r.get("occurredAt"),f.getFrom()));
        if (f.getTo()!=null) specification=specification.and((r,q,b) -> b.lessThanOrEqualTo(r.get("occurredAt"),f.getTo()));
        return specification;
    }
    @PreAuthorize("@wmsAuthorization.can(authentication, 'STOCK_VIEW')")
    public PageResponse<LpnResponse> lpns(PageQuery query,StockSearchFilters.Lpns filters) {
        return PageResponse.from(lpns.findAll(lpnSpec(query,filters),query.toPageable()).map(LpnResponse::from));
    }
    @PreAuthorize("@wmsAuthorization.can(authentication, 'STOCK_VIEW')")
    public LpnResponse lpn(String id) {
        return LpnResponse.from(lpns.findById(id).orElseThrow(() -> new ResourceNotFoundException("LPN",id)));
    }
    @PreAuthorize("@wmsAuthorization.can(authentication, 'STOCK_VIEW')")
    public PageResponse<LookupOption> lpnLookup(LookupQuery query,StockSearchFilters.Lpns filters) {
        return PageResponse.from(lpns.findAll(lpnSpec(query,filters),query.toPageable())
                .map(lpn -> new LookupOption(lpn.getId(),lpn.getTrackingCode()+" | "+lpn.getStatus())));
    }
    private Specification<Lpn> lpnSpec(PageQuery query,StockSearchFilters.Lpns f) {
        Specification<Lpn> specification=StockQueryService.<Lpn>match(query.getQ(),"id","trackingCode","status")
                .and(equal("positionId",f.getPositionId())).and(equal("outboundOrderId",f.getOutboundOrderId())).and(equal("status",f.getStatus()));
        if (f.getWarehouseId()!=null && !f.getWarehouseId().isBlank()) {
            Specification<Lpn> withPosition=inWarehouse(f.getWarehouseId(),"positionId");
            Specification<Lpn> shippedOrder=(r,q,b) -> {
                var sub=q.subquery(String.class);var order=sub.from(WarehouseOrder.class);
                sub.select(order.get("id")).where(b.equal(order.get("warehouseId"),f.getWarehouseId().trim()));
                return r.get("outboundOrderId").in(sub);
            };
            specification=specification.and(withPosition.or(shippedOrder));
        }
        return specification;
    }
    private static <T> Specification<T> match(String q,String...fields) { return text(q,fields); }
    private static <T> Specification<T> inWarehouse(String warehouseId,String... positionFields) {
        if (warehouseId==null || warehouseId.isBlank()) return all();
        return (root,query,builder) -> {
            Subquery<String> sub=query.subquery(String.class);Root<Position> position=sub.from(Position.class);Root<Zone> zone=sub.from(Zone.class);
            sub.select(position.get("id")).where(builder.equal(position.get("zoneId"),zone.get("id")),builder.equal(zone.get("warehouseId"),warehouseId.trim()));
            return builder.or(Arrays.stream(positionFields).map(field -> root.get(field).in(sub)).toArray(Predicate[]::new));
        };
    }
}
