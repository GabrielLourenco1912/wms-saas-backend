package dev.lourencogabriel.wmssaas.outbound.service;

import dev.lourencogabriel.wmssaas.order.model.OrderItem;
import dev.lourencogabriel.wmssaas.order.model.WarehouseOrder;
import dev.lourencogabriel.wmssaas.outbound.dto.OutboundFilters;
import dev.lourencogabriel.wmssaas.outbound.model.Shipment;
import dev.lourencogabriel.wmssaas.outbound.model.Wave;
import dev.lourencogabriel.wmssaas.outbound.model.WaveOrder;
import dev.lourencogabriel.wmssaas.task.model.OperationalDivergence;
import dev.lourencogabriel.wmssaas.task.model.WarehouseTask;
import dev.lourencogabriel.wmssaas.warehouse.model.Position;
import dev.lourencogabriel.wmssaas.warehouse.model.Zone;
import org.springframework.data.jpa.domain.Specification;
import static dev.lourencogabriel.wmssaas.shared.persistence.SearchSpecifications.*;

final class OutboundSearchSpecifications {
    private OutboundSearchSpecifications() {}

    static Specification<WarehouseOrder> orders(String q, OutboundFilters.Orders filters) {
        return dev.lourencogabriel.wmssaas.shared.persistence.SearchSpecifications.<WarehouseOrder>equal("type", "SAIDA")
                .and(text(q, "id", "documentNumber"))
                .and(equal("warehouseId", filters.warehouseId())).and(equal("status", filters.status()))
                .and(equal("partnerId", filters.partnerId())).and(equal("documentNumber", filters.documentNumber()))
                .and((root, query, cb) -> {
                    if (blank(filters.productId())) return cb.conjunction();
                    var ids = query.subquery(String.class);
                    var item = ids.from(OrderItem.class);
                    ids.select(item.get("orderId")).where(cb.equal(item.get("productId"), filters.productId().trim()));
                    return root.get("id").in(ids);
                });
    }

    static Specification<Wave> waves(String q, OutboundFilters.Waves filters) {
        return dev.lourencogabriel.wmssaas.shared.persistence.SearchSpecifications.<Wave>text(q, "id", "code")
                .and(equal("warehouseId", filters.warehouseId())).and(equal("status", filters.status())).and(equal("code", filters.code()))
                .and((root, query, cb) -> {
                    if (blank(filters.orderId())) return cb.conjunction();
                    var ids = query.subquery(String.class);
                    var relation = ids.from(WaveOrder.class);
                    ids.select(relation.get("waveId")).where(cb.equal(relation.get("orderId"), filters.orderId().trim()));
                    return root.get("id").in(ids);
                });
    }

    static Specification<WarehouseTask> picking(String q, OutboundFilters.PickingTasks filters) {
        return dev.lourencogabriel.wmssaas.shared.persistence.SearchSpecifications.<WarehouseTask>equal("type", "PICKING")
                .and(text(q, "id", "status", "type", "orderId", "lpnId"))
                .and(equal("status", filters.status())).and(equal("assignedUserId", filters.assignedUserId()))
                .and(equal("orderId", filters.orderId())).and(equal("productId", filters.productId())).and(equal("waveId", filters.waveId()))
                .and(warehousePosition("originPositionId", filters.warehouseId()));
    }

    static Specification<OperationalDivergence> divergences(String q, OutboundFilters.Divergences filters) {
        return dev.lourencogabriel.wmssaas.shared.persistence.SearchSpecifications.<OperationalDivergence>text(q, "id", "reason", "type", "status")
                .and(equal("status", filters.status())).and(equal("type", filters.type()))
                .and(equal("taskId", filters.taskId())).and(equal("stockId", filters.stockId()))
                .and(warehousePosition("positionId", filters.warehouseId()));
    }

    static Specification<Shipment> shipments(String q, OutboundFilters.Shipments filters) {
        return dev.lourencogabriel.wmssaas.shared.persistence.SearchSpecifications.<Shipment>text(q, "id", "status", "orderId")
                .and(equal("status", filters.status())).and(equal("orderId", filters.orderId()))
                .and(equal("dockPositionId", filters.dockPositionId()))
                .and(warehousePosition("dockPositionId", filters.warehouseId()));
    }

    private static <T> Specification<T> warehousePosition(String positionField, String warehouseId) {
        return (root, query, cb) -> {
            if (blank(warehouseId)) return cb.conjunction();
            var ids = query.subquery(String.class);
            var position = ids.from(Position.class);
            var zone = ids.from(Zone.class);
            ids.select(position.get("id")).where(cb.equal(position.get("zoneId"), zone.get("id")),
                    cb.equal(zone.get("warehouseId"), warehouseId.trim()));
            return root.get(positionField).in(ids);
        };
    }

    private static boolean blank(String text) { return text == null || text.isBlank(); }
}
