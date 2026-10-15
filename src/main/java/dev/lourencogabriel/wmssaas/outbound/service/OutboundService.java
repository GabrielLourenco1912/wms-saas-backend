package dev.lourencogabriel.wmssaas.outbound.service;

import dev.lourencogabriel.wmssaas.shared.api.PageResponse;
import dev.lourencogabriel.wmssaas.web.exception.BusinessRuleException;
import dev.lourencogabriel.wmssaas.web.exception.ResourceNotFoundException;
import dev.lourencogabriel.wmssaas.order.model.OrderItem;
import dev.lourencogabriel.wmssaas.order.model.WarehouseOrder;
import dev.lourencogabriel.wmssaas.outbound.model.Wave;
import dev.lourencogabriel.wmssaas.outbound.model.WaveOrder;
import dev.lourencogabriel.wmssaas.stock.model.StockReservation;
import dev.lourencogabriel.wmssaas.task.model.OperationalDivergence;
import dev.lourencogabriel.wmssaas.task.model.TaskHistory;
import dev.lourencogabriel.wmssaas.task.model.WarehouseTask;
import dev.lourencogabriel.wmssaas.order.repository.OrderItemRepository;
import dev.lourencogabriel.wmssaas.order.repository.WarehouseOrderRepository;
import dev.lourencogabriel.wmssaas.outbound.repository.ShipmentItemRepository;
import dev.lourencogabriel.wmssaas.outbound.repository.ShipmentRepository;
import dev.lourencogabriel.wmssaas.outbound.repository.WaveOrderRepository;
import dev.lourencogabriel.wmssaas.outbound.repository.WaveRepository;
import dev.lourencogabriel.wmssaas.partner.repository.PartnerRepository;
import dev.lourencogabriel.wmssaas.product.repository.ProductRepository;
import dev.lourencogabriel.wmssaas.stock.repository.StockRepository;
import dev.lourencogabriel.wmssaas.stock.repository.StockReservationRepository;
import dev.lourencogabriel.wmssaas.task.repository.OperationalDivergenceRepository;
import dev.lourencogabriel.wmssaas.task.repository.TaskHistoryRepository;
import dev.lourencogabriel.wmssaas.task.repository.WarehouseTaskRepository;
import dev.lourencogabriel.wmssaas.warehouse.repository.PositionRepository;
import dev.lourencogabriel.wmssaas.operation.service.OperationService;
import dev.lourencogabriel.wmssaas.stock.service.StockMutationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static dev.lourencogabriel.wmssaas.outbound.dto.OutboundDtos.*;

/** Orders, allocation and cancellation share the warehouse transaction boundary. */
@Service
@RequiredArgsConstructor
@Transactional(isolation = Isolation.READ_COMMITTED)
public class OutboundService {
    private final WarehouseOrderRepository orders;
    private final OrderItemRepository items;
    private final WaveRepository waves;
    private final WaveOrderRepository waveOrders;
    private final WarehouseTaskRepository tasks;
    private final StockReservationRepository reservations;
    private final ProductRepository products;
    private final PartnerRepository partners;
    private final PositionRepository positions;
    private final StockRepository stocks;
    private final ShipmentRepository shipments;
    private final ShipmentItemRepository shipmentItems;
    private final OperationalDivergenceRepository divergences;
    private final TaskHistoryRepository histories;
    private final StockMutationService stockMutation;
    private final OperationService operations;
    private final OutboundMapper mapper;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    @PreAuthorize("@wmsAuthorization.can(authentication, 'ORDER_VIEW')")
    public PageResponse<OrderResponse> orders(Pageable pageable, String warehouseId, String status) {
        return PageResponse.from(orders.findAll((root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(root.get("type"), "SAIDA"));
            if (warehouseId != null) predicates.add(cb.equal(root.get("warehouseId"), warehouseId));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        }, page(pageable)).map(mapper::order));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@wmsAuthorization.can(authentication, 'ORDER_VIEW')")
    public OrderResponse order(String id) { return mapper.order(requireOutboundOrder(id)); }

    @PreAuthorize("@wmsAuthorization.can(authentication, 'ORDER_CREATE')")
    public OrderResponse createOrder(OrderRequest request, String userId) {
        stockMutation.lockWarehouse(request.warehouseId());
        validateOrderRequest(request);
        var order = new WarehouseOrder();
        order.setWarehouseId(request.warehouseId());
        order.setPartnerId(request.partnerId());
        order.setDocumentNumber(request.documentNumber());
        order.setExpectedAt(request.expectedAt());
        order.setType("SAIDA");
        order.setStatus("PENDENTE");
        order.setCreatedById(userId);
        orders.saveAndFlush(order);
        createLines(order, request.items());
        return mapper.order(order);
    }

    @PreAuthorize("@wmsAuthorization.can(authentication, 'ORDER_EDIT')")
    public OrderResponse updateOrder(String id, OrderRequest request) {
        var order = lockOrder(id);
        if (!order.getWarehouseId().equals(request.warehouseId()))
            throw rule("Não é permitido mudar o armazém de um pedido existente");
        if (!"PENDENTE".equals(order.getStatus()) || !tasks.findByOrderId(id).isEmpty())
            throw rule("O pedido só pode ser editado antes de gerar tarefas");
        validateOrderRequest(request);
        order.setPartnerId(request.partnerId());
        order.setDocumentNumber(request.documentNumber());
        order.setExpectedAt(request.expectedAt());
        items.deleteAll(items.findByOrderId(id));
        items.flush();
        createLines(order, request.items());
        return mapper.order(orders.saveAndFlush(order));
    }

    private void validateOrderRequest(OrderRequest request) {
        var partner = partners.findById(request.partnerId())
                .orElseThrow(() -> new ResourceNotFoundException("Parceiro", request.partnerId()));
        if (!Boolean.TRUE.equals(partner.getActive())) throw rule("Parceiro inativo");
        if (!Set.of("CLIENTE", "AMBOS").contains(partner.getType())) throw rule("O parceiro do pedido de saída deve ser cliente");
        if (request.items() == null || request.items().isEmpty()) throw rule("Informe os itens do pedido");
        for (var item : request.items()) {
            positive(item.quantity(), "Quantidade solicitada");
            var product = products.findById(item.productId())
                    .orElseThrow(() -> new ResourceNotFoundException("Produto", item.productId()));
            if (!Boolean.TRUE.equals(product.getActive())) throw rule("Produto inativo: " + product.getSku());
        }
    }

    private void createLines(WarehouseOrder order, List<OrderLine> lines) {
        // Repeated SKU lines are consolidated to preserve an unambiguous physical shipment line.
        var quantities = new LinkedHashMap<String, BigDecimal>();
        lines.forEach(line -> quantities.merge(line.productId(), line.quantity(), BigDecimal::add));
        quantities.forEach((productId, quantity) -> {
            positive(quantity, "Quantidade solicitada");
            var item = new OrderItem();
            item.setOrderId(order.getId());
            item.setProductId(productId);
            item.setRequestedQuantity(quantity);
            item.setPickedQuantity(BigDecimal.ZERO);
            item.setFulfilledQuantity(BigDecimal.ZERO);
            items.save(item);
        });
        items.flush();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@wmsAuthorization.can(authentication, 'WAVE_VIEW')")
    public PageResponse<WaveResponse> waves(Pageable pageable, String warehouseId, String status) {
        return PageResponse.from(waves.findAll((root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            if (warehouseId != null) predicates.add(cb.equal(root.get("warehouseId"), warehouseId));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        }, page(pageable)).map(mapper::wave));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@wmsAuthorization.can(authentication, 'WAVE_VIEW')")
    public WaveResponse wave(String id) { return mapper.wave(requireWave(id)); }

    @PreAuthorize("@wmsAuthorization.can(authentication, 'WAVE_CREATE')")
    public WaveResponse createWave(WaveRequest request, String userId) {
        return operations.execute(request.idempotencyKey(), request, userId, "GERAR_ONDA", WaveResponse.class, operationId -> {
            stockMutation.lockWarehouse(request.warehouseId());
            if (waves.findByCode(request.code().strip()).isPresent()) throw rule("Código de onda já utilizado");
            if (request.orderIds() == null || request.orderIds().isEmpty()) throw rule("Selecione pedidos de saída");
            var orderIds = request.orderIds().stream().distinct().sorted().toList();
            var selected = orderIds.stream().map(id -> orders.lockById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Pedido", id))).toList();
            for (var order : selected) {
                if (!"SAIDA".equals(order.getType()) || !request.warehouseId().equals(order.getWarehouseId()))
                    throw rule("Todos os pedidos devem ser de saída e pertencer ao armazém da onda");
                requireOpen(order);
            }
            var wave = new Wave();
            wave.setWarehouseId(request.warehouseId());
            wave.setCode(request.code().strip());
            wave.setStatus("PENDENTE");
            wave.setCreatedById(userId);
            waves.saveAndFlush(wave);
            int count = 0;
            for (var order : selected) {
                var relation = new WaveOrder();
                relation.setWaveId(wave.getId());
                relation.setOrderId(order.getId());
                waveOrders.save(relation);
                for (var item : items.findByOrderId(order.getId()).stream().sorted(Comparator.comparing(OrderItem::getId)).toList()) {
                    var locked = items.lockById(item.getId()).orElseThrow();
                    var residual = demand(locked);
                    if (residual.signum() > 0) {
                        count += allocate(order, locked, wave.getId(), residual, null, null,
                                request.priority() == null ? 0 : request.priority(), userId, operationId).size();
                    }
                }
            }
            if (count == 0) throw rule("Não há demanda sem reserva com saldo disponível para esta onda");
            tasks.flush();
            return mapper.wave(wave);
        });
    }

    /** Caller already holds the warehouse lock; candidates are locked in FEFO/FIFO order. */
    List<WarehouseTask> allocate(WarehouseOrder order, OrderItem item, String waveId,
                                 BigDecimal requested, String excludedPositionId, String originTaskId,
                                 int priority, String userId, String operationId) {
        var remaining = requested.min(demand(item)).max(BigDecimal.ZERO);
        var result = new ArrayList<WarehouseTask>();
        if (!products.findById(item.getProductId()).map(product -> Boolean.TRUE.equals(product.getActive())).orElse(false))
            throw rule("Produto inativo não pode gerar nova tarefa de picking");
        for (var candidate : stocks.findAllocationCandidates(order.getWarehouseId(), item.getProductId())) {
            if (remaining.signum() == 0) break;
            if (candidate.getLpnId() == null) continue;
            var position = positions.findById(candidate.getPositionId()).orElseThrow();
            if (!"ARMAZENAGEM".equals(position.getPurpose())
                    || !Boolean.TRUE.equals(position.getActive())
                    || Objects.equals(excludedPositionId, position.getId())) continue;
            var stock = stockMutation.lockStock(candidate.getId());
            var available = stockMutation.available(stock);
            if (available.signum() <= 0) continue;
            var quantity = remaining.min(available);
            var task = new WarehouseTask();
            task.setType("PICKING");
            task.setStatus("PENDENTE");
            task.setPriority(priority);
            task.setWaveId(waveId);
            task.setOrderId(order.getId());
            task.setOrderItemId(item.getId());
            task.setProductId(item.getProductId());
            task.setSourceStockId(stock.getId());
            task.setOriginPositionId(stock.getPositionId());
            task.setLpnId(stock.getLpnId());
            task.setOriginTaskId(originTaskId);
            task.setTargetQuantity(quantity);
            tasks.saveAndFlush(task);
            var reservation = new StockReservation();
            reservation.setStockId(stock.getId());
            reservation.setTaskId(task.getId());
            reservation.setOrderItemId(item.getId());
            reservation.setReservedQuantity(quantity);
            reservation.setConsumedQuantity(BigDecimal.ZERO);
            reservation.setReleasedQuantity(BigDecimal.ZERO);
            reservation.setStatus("ATIVA");
            reservations.saveAndFlush(reservation);
            history(task, null, "PENDENTE", userId, operationId,
                    originTaskId == null ? "Alocação FEFO/FIFO" : "Realocação após falta física");
            result.add(task);
            remaining = remaining.subtract(quantity);
        }
        return result;
    }

    BigDecimal demand(OrderItem item) {
        var reserved = reservations.findByOrderItemIdAndStatus(item.getId(), "ATIVA").stream()
                .map(StockReservation::remainingQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        return item.getRequestedQuantity().subtract(item.getFulfilledQuantity())
                .subtract(item.getPickedQuantity()).subtract(reserved).max(BigDecimal.ZERO);
    }

    @PreAuthorize("@wmsAuthorization.can(authentication, 'ORDER_CANCEL')")
    public OrderResponse cancelOrder(String id, CancelRequest request, String userId) {
        return operations.execute(request.idempotencyKey(), List.of(id, request), userId,
                "CANCELAR_PEDIDO", OrderResponse.class, operationId -> {
            var order = lockOrder(id);
            requireOpen(order);
            var related = tasks.findByOrderId(id);
            rejectRunning(related);
            related.stream().filter(task -> "PENDENTE".equals(task.getStatus()))
                    .forEach(task -> cancelPendingTask(task, request.reason(), userId, operationId));
            // Prepared merchandise remains physically present and can be returned by an audited transfer.
            for (var shipment : shipments.findByOrderId(id)) {
                if (!"DESPACHADA".equals(shipment.getStatus()) && !"CANCELADA".equals(shipment.getStatus())) {
                    shipment.setStatus("CANCELADA");
                    shipments.save(shipment);
                    shipmentItems.findByShipmentId(shipment.getId()).forEach(line -> {
                        line.setStatus("CANCELADO");
                        shipmentItems.save(line);
                    });
                }
            }
            order.setStatus("CANCELADO");
            order.setCancelledAt(Instant.now());
            order.setCancelledById(userId);
            order.setCancellationReason(requiredReason(request.reason()));
            orders.saveAndFlush(order);
            related.stream().map(WarehouseTask::getWaveId).filter(Objects::nonNull).distinct().forEach(this::refreshWave);
            return mapper.order(order);
        });
    }

    @PreAuthorize("@wmsAuthorization.can(authentication, 'WAVE_CANCEL')")
    public WaveResponse cancelWave(String id, CancelRequest request, String userId) {
        return operations.execute(request.idempotencyKey(), List.of(id, request), userId,
                "CANCELAR_ONDA", WaveResponse.class, operationId -> {
            var wave = requireWave(id);
            stockMutation.lockWarehouse(wave.getWarehouseId());
            entityManager.refresh(wave, LockModeType.PESSIMISTIC_WRITE);
            if (Set.of("CANCELADA", "CONCLUIDA").contains(wave.getStatus())) throw rule("Onda já encerrada");
            var related = tasks.findByWaveId(id);
            rejectRunning(related);
            related.stream().filter(task -> "PENDENTE".equals(task.getStatus()))
                    .forEach(task -> cancelPendingTask(task, request.reason(), userId, operationId));
            wave.setStatus("CANCELADA");
            wave.setCancelledAt(Instant.now());
            wave.setCancelledById(userId);
            wave.setCancellationReason(requiredReason(request.reason()));
            return mapper.wave(waves.saveAndFlush(wave));
        });
    }

    void cancelPendingTask(WarehouseTask task, String reason, String userId, String operationId) {
        if (!"PENDENTE".equals(task.getStatus())) throw rule("Só é permitido cancelar tarefas pendentes");
        reservations.findByTaskId(task.getId()).ifPresent(reservation -> closeReservation(reservation, BigDecimal.ZERO, reason));
        task.setStatus("CANCELADA");
        task.setCancelledAt(Instant.now());
        task.setCancelledById(userId);
        task.setCancellationReason(requiredReason(reason));
        tasks.save(task);
        history(task, "PENDENTE", "CANCELADA", userId, operationId, reason);
    }

    private void rejectRunning(List<WarehouseTask> related) {
        if (related.stream().anyMatch(task -> "EM_EXECUCAO".equals(task.getStatus())))
            throw rule("Conclua ou trate as tarefas em execução antes de cancelar");
    }

    @PreAuthorize("@wmsAuthorization.can(authentication, 'TASK_QUANTITY_OVERRIDE')")
    public TaskResponse authorizeExcess(String taskId, ExcessRequest request, String userId) {
        return operations.execute(request.idempotencyKey(), List.of(taskId, request), userId,
                "AUTORIZAR_EXCESSO", TaskResponse.class, operationId -> {
            var task = lockPickingTask(taskId);
            if (!Set.of("PENDENTE", "EM_EXECUCAO").contains(task.getStatus())) throw rule("Tarefa já encerrada");
            positive(request.newTargetQuantity(), "Novo limite autorizado");
            if (request.newTargetQuantity().compareTo(task.getTargetQuantity()) <= 0)
                throw rule("O novo limite deve ser maior que o alvo atual");
            var reservation = activeReservation(task);
            var item = items.lockById(task.getOrderItemId()).orElseThrow();
            var extra = request.newTargetQuantity().subtract(task.getTargetQuantity());
            if (extra.compareTo(demand(item)) > 0) throw rule("Excesso ultrapassa a demanda ainda não alocada do pedido");
            var stock = stockMutation.lockStock(task.getSourceStockId());
            if (extra.compareTo(stockMutation.available(stock)) > 0) throw rule("Saldo livre insuficiente para autorizar excesso");
            var divergence = new OperationalDivergence();
            divergence.setTaskId(task.getId());
            divergence.setStockId(stock.getId());
            divergence.setPositionId(stock.getPositionId());
            divergence.setUserId(userId);
            divergence.setExpectedQuantity(task.getTargetQuantity());
            divergence.setActualQuantity(request.newTargetQuantity());
            divergence.setMissingQuantity(BigDecimal.ZERO);
            divergence.setType("EXCESSO");
            divergence.setReason(requiredReason(request.reason()));
            divergence.setStatus("TRATADA");
            divergence.setAuthorizedById(userId);
            divergence.setAuthorizedAt(Instant.now());
            divergence.setResolvedAt(Instant.now());
            divergences.save(divergence);
            reservation.setReservedQuantity(reservation.getReservedQuantity().add(extra));
            reservations.save(reservation);
            task.setTargetQuantity(request.newTargetQuantity());
            tasks.saveAndFlush(task);
            history(task, task.getStatus(), task.getStatus(), userId, operationId, "Excesso autorizado: " + request.reason());
            return mapper.task(task);
        });
    }

    WarehouseOrder lockOrder(String id) {
        var order = requireOutboundOrder(id);
        stockMutation.lockWarehouse(order.getWarehouseId());
        // A row loaded to discover its warehouse must be refreshed after waiting for the lock.
        entityManager.refresh(order, LockModeType.PESSIMISTIC_WRITE);
        return order;
    }

    WarehouseTask lockPickingTask(String id) {
        var task = tasks.findById(id).orElseThrow(() -> new ResourceNotFoundException("Tarefa", id));
        if (!"PICKING".equals(task.getType())) throw rule("Tarefa não é de picking");
        var order = lockOrder(task.getOrderId());
        requireOpen(order);
        entityManager.refresh(task, LockModeType.PESSIMISTIC_WRITE);
        return task;
    }

    WarehouseOrder requireOutboundOrder(String id) {
        var order = orders.findById(id).orElseThrow(() -> new ResourceNotFoundException("Pedido", id));
        if (!"SAIDA".equals(order.getType())) throw rule("O documento não é um pedido de saída");
        return order;
    }

    Wave requireWave(String id) {
        return waves.findById(id).orElseThrow(() -> new ResourceNotFoundException("Onda", id));
    }

    void requireOpen(WarehouseOrder order) {
        if (Set.of("CANCELADO", "ATENDIDO").contains(order.getStatus())) throw rule("Pedido já encerrado");
    }

    StockReservation activeReservation(WarehouseTask task) {
        var reservation = reservations.findByTaskId(task.getId()).orElseThrow(() -> rule("Tarefa sem reserva"));
        if (!"ATIVA".equals(reservation.getStatus())) throw rule("Reserva da tarefa já encerrada");
        return reservation;
    }

    void closeReservation(StockReservation reservation, BigDecimal consumed, String reason) {
        if (consumed.signum() < 0 || consumed.compareTo(reservation.remainingQuantity()) > 0)
            throw rule("Consumo excede a reserva ativa");
        var remaining = reservation.remainingQuantity();
        reservation.setConsumedQuantity(reservation.getConsumedQuantity().add(consumed));
        reservation.setReleasedQuantity(reservation.getReleasedQuantity().add(remaining.subtract(consumed)));
        reservation.setStatus("ENCERRADA");
        reservation.setClosedAt(Instant.now());
        reservation.setClosureReason(reason);
        reservations.saveAndFlush(reservation);
    }

    void history(WarehouseTask task, String previous, String next, String userId, String operationId, String reason) {
        var entry = new TaskHistory();
        entry.setTaskId(task.getId());
        entry.setPreviousStatus(previous);
        entry.setNewStatus(next);
        entry.setUserId(userId);
        entry.setOperationId(operationId);
        entry.setReason(reason == null ? null : reason.substring(0, Math.min(255, reason.length())));
        entry.setRecordedAt(Instant.now());
        histories.save(entry);
    }

    void refreshWave(String waveId) {
        if (waveId == null) return;
        var wave = waves.findById(waveId).orElseThrow();
        if ("CANCELADA".equals(wave.getStatus())) return;
        var related = tasks.findByWaveId(waveId);
        boolean pending = related.stream().anyMatch(task -> Set.of("PENDENTE", "EM_EXECUCAO").contains(task.getStatus()));
        if (!related.isEmpty() && !pending) wave.setStatus("CONCLUIDA");
        else if (related.stream().anyMatch(task -> !"PENDENTE".equals(task.getStatus()))) wave.setStatus("EM_EXECUCAO");
        waves.save(wave);
    }

    void refreshOrder(WarehouseOrder order) {
        if ("CANCELADO".equals(order.getStatus())) return;
        var lines = items.findByOrderId(order.getId());
        boolean complete = !lines.isEmpty() && lines.stream()
                .allMatch(line -> line.getFulfilledQuantity().compareTo(line.getRequestedQuantity()) == 0);
        if (complete) order.setStatus("ATENDIDO");
        else if (lines.stream().anyMatch(line -> line.getFulfilledQuantity().signum() > 0)) order.setStatus("PARCIAL");
        else order.setStatus("EM_EXECUCAO");
        orders.save(order);
    }

    static BigDecimal positive(BigDecimal quantity, String label) {
        if (quantity == null || quantity.signum() <= 0 || quantity.scale() > 6 || quantity.precision() - quantity.scale() > 12)
            throw rule(label + " deve ser positiva com até 6 casas decimais e 12 inteiros");
        return quantity;
    }

    static String requiredReason(String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 255) throw rule("Informe um motivo de até 255 caracteres");
        return reason.strip();
    }

    static Pageable page(Pageable pageable) {
        return PageRequest.of(Math.max(0, pageable.getPageNumber()), Math.max(1, Math.min(200, pageable.getPageSize())), Sort.by("id"));
    }

    static BusinessRuleException rule(String message) { return new BusinessRuleException(message); }
}
