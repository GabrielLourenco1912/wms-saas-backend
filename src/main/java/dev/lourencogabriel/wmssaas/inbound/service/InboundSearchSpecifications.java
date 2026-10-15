package dev.lourencogabriel.wmssaas.inbound.service;

import dev.lourencogabriel.wmssaas.inbound.dto.InboundFilters;
import dev.lourencogabriel.wmssaas.audit.model.StockTransaction;
import dev.lourencogabriel.wmssaas.order.model.OrderItem;
import dev.lourencogabriel.wmssaas.order.model.WarehouseOrder;
import dev.lourencogabriel.wmssaas.stock.model.Stock;
import dev.lourencogabriel.wmssaas.task.model.WarehouseTask;
import dev.lourencogabriel.wmssaas.task.model.TaskHistory;
import dev.lourencogabriel.wmssaas.warehouse.model.Position;
import dev.lourencogabriel.wmssaas.warehouse.model.Zone;
import org.springframework.data.jpa.domain.Specification;
import static dev.lourencogabriel.wmssaas.shared.persistence.SearchSpecifications.*;

final class InboundSearchSpecifications {
    private InboundSearchSpecifications() {}

    static Specification<WarehouseOrder> receipts(String q, InboundFilters.Receipts filters) {
        return InboundSearchSpecifications.<WarehouseOrder>equalType("ENTRADA")
                .and(text(q, "id", "documentNumber"))
                .and(equal("warehouseId", filters.warehouseId()))
                .and(equal("status", filters.status()))
                .and(equal("partnerId", filters.partnerId()))
                .and(equal("documentNumber", filters.documentNumber()))
                .and((root, query, cb) -> {
                    if (blank(filters.productId())) return cb.conjunction();
                    var items = query.subquery(String.class);
                    var item = items.from(OrderItem.class);
                    items.select(item.get("orderId")).where(cb.equal(item.get("productId"), filters.productId().trim()));
                    return root.get("id").in(items);
                });
    }

    static Specification<WarehouseTask> putaway(String q, InboundFilters.PutawayTasks filters) {
        return InboundSearchSpecifications.<WarehouseTask>equalType("PUTAWAY")
                .and(text(q, "id", "status", "type", "orderId", "lpnId"))
                .and(equal("status", filters.status()))
                .and(equal("assignedUserId", filters.assignedUserId()))
                .and(equal("orderId", filters.orderId()))
                .and(equal("lpnId", filters.lpnId()))
                .and((root, query, cb) -> {
                    if (blank(filters.warehouseId())) return cb.conjunction();
                    var positions = query.subquery(String.class);
                    var position = positions.from(Position.class);
                    var zone = positions.from(Zone.class);
                    positions.select(position.get("id")).where(cb.equal(position.get("zoneId"), zone.get("id")),
                            cb.equal(zone.get("warehouseId"), filters.warehouseId().trim()));
                    return root.get("originPositionId").in(positions);
                })
                .and((root, query, cb) -> {
                    if (blank(filters.productId())) return cb.conjunction();
                    // Completed work is identified by its immutable movement events, not by later LPN contents.
                    var completedTasks = query.subquery(String.class);
                    var transaction = completedTasks.from(StockTransaction.class);
                    completedTasks.select(transaction.get("taskId")).where(cb.equal(transaction.get("productId"), filters.productId().trim()));
                    // Each accepted receipt emits both an ENTRADA entry and a putaway history event in the same operation.
                    // This preserves every original product of a mixed LPN even if the task is later cancelled.
                    var receiptTasks = query.subquery(String.class);
                    var receiptEvent = receiptTasks.from(TaskHistory.class);
                    var receiptEntry = receiptTasks.from(StockTransaction.class);
                    receiptTasks.select(receiptEvent.get("taskId")).where(
                            cb.equal(receiptEvent.get("operationId"),receiptEntry.get("operationId")),
                            cb.equal(receiptEntry.get("movementType"),"ENTRADA"),
                            cb.equal(receiptEntry.get("productId"),filters.productId().trim()));
                    // Pending mixed-SKU tasks follow the physical contents still awaiting putaway.
                    var lpns = query.subquery(String.class);
                    var stock = lpns.from(Stock.class);
                    lpns.select(stock.get("lpnId")).where(cb.equal(stock.get("productId"), filters.productId().trim()),
                            cb.greaterThan(stock.get("quantity"), java.math.BigDecimal.ZERO));
                    // Legacy single-product tasks can also retain an explicit product/source layer.
                    var sourceLayers = query.subquery(String.class);
                    var sourceStock = sourceLayers.from(Stock.class);
                    sourceLayers.select(sourceStock.get("id")).where(cb.equal(sourceStock.get("productId"), filters.productId().trim()));
                    return cb.or(
                            root.get("id").in(receiptTasks),
                            cb.and(root.get("status").in("CONCLUIDA","DIVERGENTE"), root.get("id").in(completedTasks)),
                            cb.and(root.get("status").in("PENDENTE","EM_EXECUCAO"),
                                    cb.or(cb.equal(root.get("productId"), filters.productId().trim()),root.get("lpnId").in(lpns))),
                            cb.and(cb.equal(root.get("status"),"CANCELADA"),
                                    cb.or(cb.equal(root.get("productId"),filters.productId().trim()),root.get("sourceStockId").in(sourceLayers))));
                });
    }

    private static <T> Specification<T> equalType(String type) { return equal("type", type); }
    private static boolean blank(String text) { return text == null || text.isBlank(); }
}
