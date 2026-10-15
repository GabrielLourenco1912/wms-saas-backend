package dev.lourencogabriel.wmssaas.outbound.service;

import dev.lourencogabriel.wmssaas.shared.api.PageResponse;
import dev.lourencogabriel.wmssaas.web.exception.ResourceNotFoundException;
import dev.lourencogabriel.wmssaas.order.model.OrderItem;
import dev.lourencogabriel.wmssaas.outbound.model.Shipment;
import dev.lourencogabriel.wmssaas.outbound.model.ShipmentItem;
import dev.lourencogabriel.wmssaas.stock.model.Lpn;
import dev.lourencogabriel.wmssaas.stock.model.Stock;
import dev.lourencogabriel.wmssaas.warehouse.model.Position;
import dev.lourencogabriel.wmssaas.order.repository.OrderItemRepository;
import dev.lourencogabriel.wmssaas.outbound.repository.ShipmentItemRepository;
import dev.lourencogabriel.wmssaas.outbound.repository.ShipmentRepository;
import dev.lourencogabriel.wmssaas.stock.repository.LpnRepository;
import dev.lourencogabriel.wmssaas.stock.repository.StockRepository;
import dev.lourencogabriel.wmssaas.warehouse.repository.PositionRepository;
import dev.lourencogabriel.wmssaas.operation.service.OperationService;
import dev.lourencogabriel.wmssaas.stock.service.StockMutationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static dev.lourencogabriel.wmssaas.outbound.dto.OutboundDtos.*;
import static dev.lourencogabriel.wmssaas.outbound.service.OutboundService.*;

/** Picking is internal movement; only the dispatch below depletes warehouse stock. */
@Service
@RequiredArgsConstructor
@Transactional(isolation = Isolation.READ_COMMITTED)
public class ShipmentService {
    private final ShipmentRepository shipments;
    private final ShipmentItemRepository shipmentItems;
    private final OrderItemRepository orderItems;
    private final LpnRepository lpns;
    private final StockRepository stocks;
    private final PositionRepository positions;
    private final OutboundService outbound;
    private final OutboundMapper mapper;
    private final StockMutationService stockMutation;
    private final OperationService operations;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    @PreAuthorize("@wmsAuthorization.can(authentication, 'SHIPMENT_VIEW')")
    public PageResponse<ShipmentResponse> shipments(Pageable pageable, String orderId, String status) {
        return PageResponse.from(shipments.findAll((root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            if (orderId != null) predicates.add(cb.equal(root.get("orderId"), orderId));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        }, page(pageable)).map(mapper::shipment));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@wmsAuthorization.can(authentication, 'SHIPMENT_VIEW')")
    public ShipmentResponse shipment(String id) { return mapper.shipment(requireShipment(id)); }

    @PreAuthorize("@wmsAuthorization.can(authentication, 'SHIPMENT_EXECUTE')")
    public ShipmentResponse prepare(ShipmentRequest request, String userId) {
        return operations.execute(request.idempotencyKey(), request, userId, "PREPARAR_EXPEDICAO", ShipmentResponse.class, operationId -> {
            var order = outbound.lockOrder(request.orderId());
            outbound.requireOpen(order);
            stockMutation.lockPositions(List.of(request.dockPositionId()));
            var dock = positions.findById(request.dockPositionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Doca", request.dockPositionId()));
            requireDock(dock, order.getWarehouseId());
            if (request.lpnIds() == null || request.lpnIds().isEmpty()) throw rule("Selecione os LPNs da carga");
            var selectedLpns = request.lpnIds().stream().distinct().sorted().map(id -> lpns.lockById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("LPN", id))).toList();
            var lines = orderItems.findByOrderId(order.getId());
            var byProduct = new HashMap<String, OrderItem>();
            lines.forEach(item -> byProduct.put(item.getProductId(), item));
            var totalByItem = new HashMap<String, BigDecimal>();
            var shipment = new Shipment();
            shipment.setOrderId(order.getId());
            shipment.setDockPositionId(dock.getId());
            shipment.setStatus("ABERTA");
            shipment.setCreatedById(userId);
            shipments.saveAndFlush(shipment);
            for (var lpn : selectedLpns) {
                if (!order.getId().equals(lpn.getOutboundOrderId()) || !dock.getId().equals(lpn.getPositionId())
                        || !"SEPARADO".equals(lpn.getStatus()))
                    throw rule("LPN deve estar separado para este pedido na doca selecionada");
                if (shipmentItems.findByLpnId(lpn.getId()).stream()
                        .anyMatch(item -> Set.of("PENDENTE", "CONFERIDO").contains(item.getStatus())))
                    throw rule("LPN já está vinculado a uma carga em aberto");
                var balances = stocks.findByLpnIdForUpdate(lpn.getId()).stream().filter(stock -> stock.getQuantity().signum() > 0).toList();
                if (balances.isEmpty()) throw rule("O LPN não contém saldo físico");
                for (var stock : balances) {
                    if (!dock.getId().equals(stock.getPositionId())) throw rule("Posição do saldo difere da doca do LPN");
                    stockMutation.ensureMovable(stock);
                    var orderItem = byProduct.get(stock.getProductId());
                    if (orderItem == null) throw rule("LPN contém produto que não pertence ao pedido");
                    var total = totalByItem.merge(orderItem.getId(), stock.getQuantity(), BigDecimal::add);
                    if (total.compareTo(orderItem.getPickedQuantity()) > 0) throw rule("Carga excede a quantidade separada do item");
                    var item = new ShipmentItem();
                    item.setShipmentId(shipment.getId());
                    item.setOrderItemId(orderItem.getId());
                    item.setStockId(stock.getId());
                    item.setLpnId(lpn.getId());
                    item.setQuantity(stock.getQuantity());
                    item.setStatus("PENDENTE");
                    shipmentItems.save(item);
                }
            }
            shipmentItems.flush();
            return mapper.shipment(shipment);
        });
    }

    @PreAuthorize("@wmsAuthorization.can(authentication, 'SHIPMENT_EXECUTE')")
    public ShipmentResponse confirm(String id, ShipmentScanRequest request, String userId) {
        return operations.execute(request.idempotencyKey(), List.of(id, request), userId,
                "CONFERIR_EXPEDICAO", ShipmentResponse.class, operationId -> {
            var shipment = lockShipment(id);
            requireActive(shipment);
            var dock = positions.findById(shipment.getDockPositionId()).orElseThrow();
            StockMutationService.requireCode(request.dockPositionCode(), dock.getCode(), "doca de expedição");
            var lpn = lpns.findByTrackingCode(request.lpnCode().strip()).orElseThrow(() -> rule("LPN não pertence a esta carga"));
            if (!shipment.getOrderId().equals(lpn.getOutboundOrderId()) || !dock.getId().equals(lpn.getPositionId()))
                throw rule("LPN não pertence ao pedido ou à doca desta carga");
            var all = shipmentItems.findByShipmentId(id);
            var selected = all.stream().filter(item -> item.getLpnId().equals(lpn.getId())).toList();
            if (selected.isEmpty()) throw rule("LPN não pertence a esta carga");
            if (selected.stream().anyMatch(item -> !"PENDENTE".equals(item.getStatus())))
                throw rule("LPN já conferido ou cancelado; reutilize a chave original em um reenvio");
            stockMutation.lockPositions(List.of(dock.getId()));
            for (var item : selected) {
                var stock = stockMutation.lockStock(item.getStockId());
                requireUnchanged(shipment, item, stock, lpn);
                stockMutation.ensureMovable(stock);
                item.setStatus("CONFERIDO");
                item.setConfirmedAt(Instant.now());
                item.setConfirmedById(userId);
                shipmentItems.save(item);
            }
            if (all.stream().allMatch(item -> "CONFERIDO".equals(item.getStatus()))) {
                shipment.setStatus("CONFERIDA");
                shipments.save(shipment);
            }
            shipmentItems.flush();
            return mapper.shipment(shipment);
        });
    }

    @PreAuthorize("@wmsAuthorization.can(authentication, 'SHIPMENT_EXECUTE')")
    public ShipmentResponse dispatch(String id, DispatchRequest request, String userId) {
        return operations.execute(request.idempotencyKey(), List.of(id, request), userId,
                "DESPACHAR_EXPEDICAO", ShipmentResponse.class, operationId -> {
            var shipment = lockShipment(id);
            if (!"CONFERIDA".equals(shipment.getStatus())) throw rule("A carga deve estar integralmente conferida e ainda não despachada");
            var order = outbound.requireOutboundOrder(shipment.getOrderId());
            var dock = positions.findById(shipment.getDockPositionId()).orElseThrow();
            stockMutation.ensureDestination(dock);
            StockMutationService.requireCode(request.dockPositionCode(), dock.getCode(), "doca de expedição");
            stockMutation.lockPositions(List.of(dock.getId()));
            var lines = shipmentItems.findByShipmentId(id).stream().sorted(Comparator.comparing(ShipmentItem::getId)).toList();
            if (lines.isEmpty() || lines.stream().anyMatch(item -> !"CONFERIDO".equals(item.getStatus())))
                throw rule("Todos os itens da carga precisam estar conferidos");
            for (var line : lines) {
                var lpn = lpns.lockById(line.getLpnId()).orElseThrow();
                var stock = stockMutation.lockStock(line.getStockId());
                requireUnchanged(shipment, line, stock, lpn);
                var item = orderItems.lockById(line.getOrderItemId()).orElseThrow();
                if (!order.getId().equals(item.getOrderId())) throw rule("Item não pertence ao pedido desta carga");
                var fulfilled = item.getFulfilledQuantity().add(line.getQuantity());
                if (item.getPickedQuantity().compareTo(line.getQuantity()) < 0 || fulfilled.compareTo(item.getRequestedQuantity()) > 0)
                    throw rule("Quantidade preparada ou solicitada inconsistente para despacho");
                stockMutation.dispatchBalance(stock, line.getQuantity(), userId, operationId, null, line.getId());
                item.setPickedQuantity(item.getPickedQuantity().subtract(line.getQuantity()));
                item.setFulfilledQuantity(fulfilled);
                orderItems.saveAndFlush(item);
                line.setStatus("EXPEDIDO");
                shipmentItems.save(line);
            }
            for (String lpnId : lines.stream().map(ShipmentItem::getLpnId).distinct().toList()) {
                var lpn = lpns.findById(lpnId).orElseThrow();
                if (stocks.findByLpnId(lpnId).stream().anyMatch(stock -> stock.getQuantity().signum() > 0))
                    throw rule("A carga não contempla todo o conteúdo do LPN");
                lpn.setStatus("EXPEDIDO");
                lpn.setPositionId(null);
                lpns.save(lpn);
            }
            shipment.setStatus("DESPACHADA");
            shipment.setDispatchedAt(Instant.now());
            shipment.setDispatchedById(userId);
            shipments.saveAndFlush(shipment);
            outbound.refreshOrder(order);
            return mapper.shipment(shipment);
        });
    }

    @PreAuthorize("@wmsAuthorization.can(authentication, 'ORDER_CANCEL')")
    public ShipmentResponse cancel(String id, CancelRequest request, String userId) {
        return operations.execute(request.idempotencyKey(), List.of(id, request), userId,
                "CANCELAR_EXPEDICAO", ShipmentResponse.class, operationId -> {
            var shipment = lockShipment(id);
            requireActive(shipment);
            requiredReason(request.reason());
            shipment.setStatus("CANCELADA");
            shipmentItems.findByShipmentId(id).forEach(item -> {
                item.setStatus("CANCELADO");
                shipmentItems.save(item);
            });
            shipments.saveAndFlush(shipment);
            return mapper.shipment(shipment);
        });
    }

    private Shipment lockShipment(String id) {
        var shipment = requireShipment(id);
        var order = outbound.lockOrder(shipment.getOrderId());
        outbound.requireOpen(order);
        entityManager.refresh(shipment, LockModeType.PESSIMISTIC_WRITE);
        return shipment;
    }

    private Shipment requireShipment(String id) {
        return shipments.findById(id).orElseThrow(() -> new ResourceNotFoundException("Expedição", id));
    }

    private void requireDock(Position dock, String warehouseId) {
        if (!"EXPEDICAO".equals(dock.getPurpose()) || !warehouseId.equals(stockMutation.warehouseForPosition(dock.getId())))
            throw rule("Selecione uma doca de expedição do armazém do pedido");
        stockMutation.ensureDestination(dock);
    }

    private void requireActive(Shipment shipment) {
        if (!Set.of("ABERTA", "CONFERIDA").contains(shipment.getStatus())) throw rule("Expedição já encerrada");
    }

    private void requireUnchanged(Shipment shipment, ShipmentItem item, Stock stock, Lpn lpn) {
        if (!Objects.equals(stock.getLpnId(), item.getLpnId())
                || !Objects.equals(stock.getPositionId(), shipment.getDockPositionId())
                || !Objects.equals(lpn.getPositionId(), shipment.getDockPositionId())
                || !Objects.equals(lpn.getOutboundOrderId(), shipment.getOrderId())
                || !"SEPARADO".equals(lpn.getStatus())
                || stock.getQuantity().compareTo(item.getQuantity()) != 0)
            throw rule("Conteúdo, vínculo ou posição do LPN mudou; cancele a carga e refaça a conferência");
    }
}
