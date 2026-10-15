package dev.lourencogabriel.wmssaas.outbound.service;

import dev.lourencogabriel.wmssaas.order.model.WarehouseOrder;
import dev.lourencogabriel.wmssaas.outbound.model.Shipment;
import dev.lourencogabriel.wmssaas.outbound.model.Wave;
import dev.lourencogabriel.wmssaas.outbound.model.WaveOrder;
import dev.lourencogabriel.wmssaas.stock.model.StockReservation;
import dev.lourencogabriel.wmssaas.task.model.OperationalDivergence;
import dev.lourencogabriel.wmssaas.task.model.WarehouseTask;
import dev.lourencogabriel.wmssaas.order.repository.OrderItemRepository;
import dev.lourencogabriel.wmssaas.outbound.repository.ShipmentItemRepository;
import dev.lourencogabriel.wmssaas.outbound.repository.WaveOrderRepository;
import dev.lourencogabriel.wmssaas.product.repository.ProductRepository;
import dev.lourencogabriel.wmssaas.stock.repository.LpnRepository;
import dev.lourencogabriel.wmssaas.stock.repository.StockRepository;
import dev.lourencogabriel.wmssaas.stock.repository.StockReservationRepository;
import dev.lourencogabriel.wmssaas.task.repository.WarehouseTaskRepository;
import dev.lourencogabriel.wmssaas.warehouse.repository.PositionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.List;
import static dev.lourencogabriel.wmssaas.outbound.dto.OutboundDtos.*;

@Component
@RequiredArgsConstructor
public class OutboundMapper {
    private final ProductRepository products;
    private final OrderItemRepository orderItems;
    private final StockReservationRepository reservations;
    private final WarehouseTaskRepository tasks;
    private final WaveOrderRepository waveOrders;
    private final PositionRepository positions;
    private final LpnRepository lpns;
    private final StockRepository stocks;
    private final ShipmentItemRepository shipmentItems;

    public OrderResponse order(WarehouseOrder order) {
        var items = orderItems.findByOrderId(order.getId()).stream().map(item -> {
            var product = products.findById(item.getProductId()).orElseThrow();
            var reserved = reservations.findByOrderItemIdAndStatus(item.getId(), "ATIVA").stream()
                    .map(StockReservation::remainingQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
            return new OrderItemResponse(item.getId(), product.getId(), product.getSku(), product.getName(),
                    item.getRequestedQuantity(), item.getPickedQuantity(), item.getFulfilledQuantity(), reserved);
        }).toList();
        return new OrderResponse(order.getId(), order.getWarehouseId(), order.getPartnerId(),
                order.getDocumentNumber(), order.getType(), order.getStatus(), order.getExpectedAt(),
                order.getCreatedAt(), order.getCancelledAt(), order.getCancellationReason(), items);
    }

    public TaskResponse task(WarehouseTask task) {
        var product = task.getProductId() == null ? null : products.findById(task.getProductId()).orElse(null);
        var position = task.getOriginPositionId() == null ? null : positions.findById(task.getOriginPositionId()).orElse(null);
        var lpn = task.getLpnId() == null ? null : lpns.findById(task.getLpnId()).orElse(null);
        return new TaskResponse(task.getId(), task.getType(), task.getStatus(), task.getPriority(),
                task.getWaveId(), task.getOrderId(), task.getOrderItemId(), task.getProductId(),
                product == null ? null : product.getSku(), product == null ? null : product.getName(),
                task.getSourceStockId(), task.getOriginPositionId(), position == null ? null : position.getCode(),
                task.getLpnId(), lpn == null ? null : lpn.getTrackingCode(), task.getDestinationPositionId(),
                task.getDestinationLpnId(), task.getAssignedUserId(), task.getTargetQuantity(),
                task.getActualQuantity(), task.getCreatedAt(), task.getStartedAt(), task.getCompletedAt(),
                task.getOriginTaskId());
    }

    public WaveResponse wave(Wave wave) {
        return new WaveResponse(wave.getId(), wave.getWarehouseId(), wave.getCode(), wave.getStatus(),
                wave.getCreatedAt(), wave.getCancelledAt(),
                waveOrders.findByWaveId(wave.getId()).stream().map(WaveOrder::getOrderId).toList(),
                tasks.findByWaveId(wave.getId()).stream().map(this::task).toList());
    }

    public DivergenceResponse divergence(OperationalDivergence divergence) {
        return new DivergenceResponse(divergence.getId(), divergence.getTaskId(), divergence.getStockId(),
                divergence.getPositionId(), divergence.getType(), divergence.getExpectedQuantity(),
                divergence.getActualQuantity(), divergence.getMissingQuantity(), divergence.getReason(),
                divergence.getStatus(), divergence.getAuthorizedById(), divergence.getCreatedAt(),
                tasks.findByOriginTaskId(divergence.getTaskId()).stream().map(WarehouseTask::getId).toList());
    }

    public ShipmentResponse shipment(Shipment shipment) {
        List<ShipmentItemResponse> items = shipmentItems.findByShipmentId(shipment.getId()).stream().map(item -> {
            var lpn = lpns.findById(item.getLpnId()).orElseThrow();
            var stock = stocks.findById(item.getStockId()).orElseThrow();
            var product = products.findById(stock.getProductId()).orElseThrow();
            return new ShipmentItemResponse(item.getId(), item.getOrderItemId(), item.getStockId(),
                    item.getLpnId(), lpn.getTrackingCode(), product.getId(), product.getSku(),
                    item.getQuantity(), item.getStatus(), item.getConfirmedAt());
        }).toList();
        return new ShipmentResponse(shipment.getId(), shipment.getOrderId(), shipment.getDockPositionId(),
                shipment.getStatus(), shipment.getCreatedAt(), shipment.getDispatchedAt(), items);
    }
}
