package dev.lourencogabriel.wmssaas.outbound.service;

import dev.lourencogabriel.wmssaas.shared.api.PageResponse;
import dev.lourencogabriel.wmssaas.web.exception.ResourceNotFoundException;
import dev.lourencogabriel.wmssaas.product.model.Product;
import dev.lourencogabriel.wmssaas.stock.model.Lpn;
import dev.lourencogabriel.wmssaas.task.model.OperationalDivergence;
import dev.lourencogabriel.wmssaas.task.model.WarehouseTask;
import dev.lourencogabriel.wmssaas.warehouse.model.Position;
import dev.lourencogabriel.wmssaas.order.repository.OrderItemRepository;
import dev.lourencogabriel.wmssaas.order.repository.WarehouseOrderRepository;
import dev.lourencogabriel.wmssaas.outbound.repository.ShipmentItemRepository;
import dev.lourencogabriel.wmssaas.outbound.repository.WaveRepository;
import dev.lourencogabriel.wmssaas.product.repository.ProductPackagingRepository;
import dev.lourencogabriel.wmssaas.product.repository.ProductRepository;
import dev.lourencogabriel.wmssaas.stock.repository.LpnRepository;
import dev.lourencogabriel.wmssaas.stock.repository.StockRepository;
import dev.lourencogabriel.wmssaas.task.repository.OperationalDivergenceRepository;
import dev.lourencogabriel.wmssaas.task.repository.WarehouseTaskRepository;
import dev.lourencogabriel.wmssaas.warehouse.repository.PositionRepository;
import dev.lourencogabriel.wmssaas.operation.service.OperationService;
import dev.lourencogabriel.wmssaas.stock.service.StockMutationService;
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

@Service
@RequiredArgsConstructor
@Transactional(isolation = Isolation.READ_COMMITTED)
public class PickingService {
    private final WarehouseTaskRepository tasks;
    private final WarehouseOrderRepository orders;
    private final OrderItemRepository items;
    private final PositionRepository positions;
    private final ProductRepository products;
    private final ProductPackagingRepository packages;
    private final LpnRepository lpns;
    private final StockRepository stocks;
    private final ShipmentItemRepository shipmentItems;
    private final OperationalDivergenceRepository divergences;
    private final WaveRepository waves;
    private final OutboundService outbound;
    private final OutboundMapper mapper;
    private final OperationService operations;
    private final StockMutationService stockMutation;

    @Transactional(readOnly = true)
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PICKING_VIEW')")
    public PageResponse<TaskResponse> tasks(Pageable pageable, String status, String assignedUserId, String orderId) {
        return PageResponse.from(tasks.findAll((root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(root.get("type"), "PICKING"));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (assignedUserId != null) predicates.add(cb.equal(root.get("assignedUserId"), assignedUserId));
            if (orderId != null) predicates.add(cb.equal(root.get("orderId"), orderId));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        }, page(pageable)).map(mapper::task));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PICKING_VIEW')")
    public TaskResponse task(String id) {
        var task = tasks.findById(id).orElseThrow(() -> new ResourceNotFoundException("Tarefa", id));
        if (!"PICKING".equals(task.getType())) throw rule("Tarefa não é de picking");
        return mapper.task(task);
    }

    @PreAuthorize("@wmsAuthorization.can(authentication, 'PICKING_EXECUTE')")
    public TaskResponse start(String id, StartRequest request, String userId) {
        return operations.execute(request.idempotencyKey(), List.of(id, request), userId,
                "INICIAR_PICKING", TaskResponse.class, operationId -> {
            var task = outbound.lockPickingTask(id);
            if (!"PENDENTE".equals(task.getStatus())) throw rule("Somente tarefas pendentes podem ser iniciadas");
            requireAssignee(task, userId);
            outbound.activeReservation(task);
            stockMutation.ensureMovable(stockMutation.lockStock(task.getSourceStockId()));
            task.setAssignedUserId(userId);
            task.setStatus("EM_EXECUCAO");
            task.setStartedAt(Instant.now());
            tasks.saveAndFlush(task);
            outbound.history(task, "PENDENTE", "EM_EXECUCAO", userId, operationId, null);
            if (task.getWaveId() != null) {
                var wave = waves.findById(task.getWaveId()).orElseThrow();
                if ("CANCELADA".equals(wave.getStatus())) throw rule("Onda cancelada");
                wave.setStatus("EM_EXECUCAO");
                waves.save(wave);
            }
            var order = orders.findById(task.getOrderId()).orElseThrow();
            outbound.refreshOrder(order);
            return mapper.task(task);
        });
    }

    @PreAuthorize("@wmsAuthorization.can(authentication, 'PICKING_EXECUTE')")
    public TaskResponse complete(String id, PickingRequest request, String userId) {
        return operations.execute(request.idempotencyKey(), List.of(id, request), userId,
                "CONCLUIR_PICKING", TaskResponse.class, operationId -> {
            var task = outbound.lockPickingTask(id);
            requireRunning(task, userId);
            positive(request.actualQuantity(), "Quantidade realizada");
            if (request.actualQuantity().compareTo(task.getTargetQuantity()) < 0)
                throw rule("Informe a falta física pelo fluxo de divergência");
            if (request.actualQuantity().compareTo(task.getTargetQuantity()) > 0)
                throw rule("Quantidade excede o alvo; solicite autorização do gestor antes de confirmar");
            execute(task, request.actualQuantity(), request.sourceLpnCode(), request.sourcePositionCode(),
                    request.productCode(), request.destinationLpnCode(), request.destinationPositionId(),
                    request.destinationPositionCode(), userId, operationId);
            task.setStatus("CONCLUIDA");
            task.setCompletedAt(Instant.now());
            tasks.saveAndFlush(task);
            outbound.history(task, "EM_EXECUCAO", "CONCLUIDA", userId, operationId, null);
            outbound.refreshWave(task.getWaveId());
            outbound.refreshOrder(orders.findById(task.getOrderId()).orElseThrow());
            return mapper.task(task);
        });
    }

    @PreAuthorize("@wmsAuthorization.can(authentication, 'PICKING_REPORT_DIVERGENCE')")
    public ShortageResponse shortage(String id, ShortageRequest request, String userId) {
        return operations.execute(request.idempotencyKey(), List.of(id, request), userId,
                "DIVERGENCIA_PICKING", ShortageResponse.class, operationId -> {
            var task = outbound.lockPickingTask(id);
            requireRunning(task, userId);
            var actual = request.actualQuantity();
            if (actual == null || actual.signum() < 0 || actual.scale() > 6 || actual.precision() - actual.scale() > 12)
                throw rule("Quantidade coletada deve ser informada e não negativa");
            if (actual.compareTo(task.getTargetQuantity()) >= 0) throw rule("O relato de falta deve indicar quantidade inferior ao alvo");
            var reason = requiredReason(request.reason());
            var missing = task.getTargetQuantity().subtract(actual);
            execute(task, actual, request.sourceLpnCode(), request.sourcePositionCode(), request.productCode(),
                    request.destinationLpnCode(), request.destinationPositionId(), request.destinationPositionCode(), userId, operationId);
            task.setStatus("DIVERGENTE");
            task.setCompletedAt(Instant.now());
            tasks.saveAndFlush(task);
            outbound.history(task, "EM_EXECUCAO", "DIVERGENTE", userId, operationId, reason);
            var divergence = new OperationalDivergence();
            divergence.setTaskId(task.getId());
            divergence.setStockId(task.getSourceStockId());
            divergence.setPositionId(task.getOriginPositionId());
            divergence.setUserId(userId);
            divergence.setExpectedQuantity(task.getTargetQuantity());
            divergence.setActualQuantity(actual);
            divergence.setMissingQuantity(missing);
            divergence.setType("FALTA");
            divergence.setReason(reason);
            divergence.setStatus("ABERTA");
            divergences.saveAndFlush(divergence);
            // A shortage does not invent a physical stock adjustment. The theoretical residual is blocked.
            stockMutation.blockStock(task.getSourceStockId(), reason, userId, operationId);
            var order = orders.findById(task.getOrderId()).orElseThrow();
            var item = items.lockById(task.getOrderItemId()).orElseThrow();
            var replacements = outbound.allocate(order, item, task.getWaveId(), missing,
                    task.getOriginPositionId(), task.getId(), task.getPriority() == null ? 0 : task.getPriority(), userId, operationId);
            var allocated = replacements.stream().map(WarehouseTask::getTargetQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
            outbound.refreshWave(task.getWaveId());
            outbound.refreshOrder(order);
            return new ShortageResponse(mapper.task(task), mapper.divergence(divergence),
                    replacements.stream().map(mapper::task).toList(), missing.subtract(allocated));
        });
    }

    private void execute(WarehouseTask task, BigDecimal actual, String sourceLpnCode,
                         String sourcePositionCode, String productCode, String destinationLpnCode,
                         String destinationPositionId, String destinationPositionCode,
                         String userId, String operationId) {
        var reservation = outbound.activeReservation(task);
        if (actual.compareTo(reservation.remainingQuantity()) > 0) throw rule("Quantidade excede a reserva autorizada");
        var positionIds = new ArrayList<String>();
        positionIds.add(task.getOriginPositionId());
        if (actual.signum() > 0) {
            if (destinationPositionId == null || destinationPositionId.isBlank()) throw rule("Informe a doca de expedição");
            positionIds.add(destinationPositionId);
        }
        stockMutation.lockPositions(positionIds);
        var source = stockMutation.lockStock(task.getSourceStockId());
        stockMutation.ensureMovable(source);
        if (!Objects.equals(source.getPositionId(), task.getOriginPositionId())
                || !Objects.equals(source.getLpnId(), task.getLpnId())) throw rule("O saldo de origem foi movimentado; revise a tarefa");
        var origin = positions.findById(source.getPositionId()).orElseThrow();
        var sourceLpn = lpns.findById(source.getLpnId()).orElseThrow();
        var product = products.findById(source.getProductId()).orElseThrow();
        StockMutationService.requireCode(sourceLpnCode, sourceLpn.getTrackingCode(), "LPN de origem");
        StockMutationService.requireCode(sourcePositionCode, origin.getCode(), "posição de origem");
        requireProductCode(product, productCode);
        var item = items.lockById(task.getOrderItemId()).orElseThrow();
        if (actual.add(item.getPickedQuantity()).add(item.getFulfilledQuantity()).compareTo(item.getRequestedQuantity()) > 0)
            throw rule("Separação ultrapassa a quantidade solicitada do item");
        if (actual.signum() > 0) {
            var destination = positions.findById(destinationPositionId)
                    .orElseThrow(() -> new ResourceNotFoundException("Posição", destinationPositionId));
            if (!"EXPEDICAO".equals(destination.getPurpose())) throw rule("Destino do picking deve ser uma doca de expedição");
            StockMutationService.requireCode(destinationPositionCode, destination.getCode(), "doca de expedição");
            var order = orders.findById(task.getOrderId()).orElseThrow();
            if (!order.getWarehouseId().equals(stockMutation.warehouseForPosition(destination.getId())))
                throw rule("A doca deve pertencer ao armazém do pedido");
            var destinationLpn = outboundLpn(destinationLpnCode, destination, order.getId());
            stockMutation.moveStock(source, destinationLpn, destination, actual, userId, operationId, task.getId(), "PICKING");
            task.setDestinationPositionId(destination.getId());
            task.setDestinationLpnId(destinationLpn.getId());
            destinationLpn.setStatus("SEPARADO");
            lpns.save(destinationLpn);
            item.setPickedQuantity(item.getPickedQuantity().add(actual));
            items.saveAndFlush(item);
        }
        outbound.closeReservation(reservation, actual, actual.compareTo(task.getTargetQuantity()) < 0 ? "Falta física" : "Picking concluído");
        task.setActualQuantity(actual);
    }

    private Lpn outboundLpn(String code, Position destination, String orderId) {
        var lpn = stockMutation.getOrCreateLpn(code, destination);
        if (lpn.getOutboundOrderId() == null) {
            if (stocks.findByLpnId(lpn.getId()).stream().anyMatch(stock -> stock.getQuantity().signum() > 0))
                throw rule("LPN de destino já contém mercadorias fora deste pedido");
            lpn.setOutboundOrderId(orderId);
        } else if (!orderId.equals(lpn.getOutboundOrderId())) throw rule("LPN de destino pertence a outro pedido");
        if (shipmentItems.findByLpnId(lpn.getId()).stream()
                .anyMatch(item -> Set.of("PENDENTE", "CONFERIDO").contains(item.getStatus())))
            throw rule("O LPN já compõe uma carga; utilize outro LPN para continuar a separação");
        return lpns.saveAndFlush(lpn);
    }

    private void requireProductCode(Product product, String code) {
        if (code != null && product.getSku().equals(code.strip())) return;
        if (code != null && packages.findByBarcode(code.strip())
                .filter(packaging -> Boolean.TRUE.equals(packaging.getActive()) && product.getId().equals(packaging.getProductId())).isPresent()) return;
        throw rule("O código lido não identifica o produto desta tarefa");
    }

    private void requireRunning(WarehouseTask task, String userId) {
        if (!"EM_EXECUCAO".equals(task.getStatus())) throw rule("A tarefa deve estar em execução");
        requireAssignee(task, userId);
    }

    private void requireAssignee(WarehouseTask task, String userId) {
        if (task.getAssignedUserId() != null && !task.getAssignedUserId().equals(userId))
            throw rule("Tarefa atribuída a outro operador");
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PICKING_VIEW')")
    public PageResponse<DivergenceResponse> divergences(Pageable pageable, String status) {
        return PageResponse.from((status == null ? divergences.findAll(page(pageable)) : divergences.findByStatus(status, page(pageable))).map(mapper::divergence));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PICKING_VIEW')")
    public DivergenceResponse divergence(String id) {
        return mapper.divergence(divergences.findById(id).orElseThrow(() -> new ResourceNotFoundException("Divergência", id)));
    }

    @PreAuthorize("@wmsAuthorization.can(authentication, 'TASK_QUANTITY_OVERRIDE')")
    public TaskResponse authorizeExcess(String id, ExcessRequest request, String userId) {
        return outbound.authorizeExcess(id, request, userId);
    }
}
