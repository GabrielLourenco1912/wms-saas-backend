package dev.lourencogabriel.wmssaas.outbound.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Operational quantities are always expressed in the product's base unit. */
public final class OutboundDtos {
    private OutboundDtos() {}

    public record OrderLine(@NotBlank @Size(max = 26) String productId,
                            @NotNull @DecimalMin(value = "0", inclusive = false)
                            @Digits(integer = 12, fraction = 6) BigDecimal quantity) {}

    public record OrderRequest(@NotBlank @Size(max = 26) String warehouseId,
                               @NotBlank @Size(max = 26) String partnerId,
                               @Size(max = 50) String documentNumber, Instant expectedAt,
                               @NotEmpty @Size(max = 500) List<@Valid OrderLine> items) {}

    public record OrderItemResponse(String id, String productId, String sku, String productName,
                                    BigDecimal requestedQuantity, BigDecimal pickedQuantity,
                                    BigDecimal fulfilledQuantity, BigDecimal reservedQuantity) {}

    public record OrderResponse(String id, String warehouseId, String partnerId,
                                String documentNumber, String type, String status,
                                Instant expectedAt, Instant createdAt, Instant cancelledAt,
                                String cancellationReason, List<OrderItemResponse> items) {}

    public record WaveRequest(@NotBlank @Size(max = 26) String warehouseId,
                              @NotBlank @Size(max = 50) String code,
                              @NotEmpty @Size(max = 200) List<@NotBlank @Size(max = 26) String> orderIds,
                              @Min(0) @Max(1000) Integer priority,
                              @NotBlank @Size(max = 64) String idempotencyKey) {}

    public record CancelRequest(@NotBlank @Size(max = 255) String reason,
                                @NotBlank @Size(max = 64) String idempotencyKey) {}

    public record StartRequest(@NotBlank @Size(max = 64) String idempotencyKey) {}

    public record PickingRequest(@NotNull @DecimalMin(value = "0", inclusive = false)
                                  @Digits(integer = 12, fraction = 6) BigDecimal actualQuantity,
                                  @NotBlank @Size(max = 100) String sourceLpnCode,
                                  @NotBlank @Size(max = 50) String sourcePositionCode,
                                  @NotBlank @Size(max = 100) String productCode,
                                  @NotBlank @Size(max = 100) String destinationLpnCode,
                                  @NotBlank @Size(max = 26) String destinationPositionId,
                                  @NotBlank @Size(max = 50) String destinationPositionCode,
                                  @NotBlank @Size(max = 64) String idempotencyKey) {}

    public record ShortageRequest(@NotNull @DecimalMin("0")
                                   @Digits(integer = 12, fraction = 6) BigDecimal actualQuantity,
                                   @NotBlank @Size(max = 100) String sourceLpnCode,
                                   @NotBlank @Size(max = 50) String sourcePositionCode,
                                   @NotBlank @Size(max = 100) String productCode,
                                   @Size(max = 100) String destinationLpnCode,
                                   @Size(max = 26) String destinationPositionId,
                                   @Size(max = 50) String destinationPositionCode,
                                   @NotBlank @Size(max = 255) String reason,
                                   @NotBlank @Size(max = 64) String idempotencyKey) {}

    public record ExcessRequest(@NotNull @DecimalMin(value = "0", inclusive = false)
                                @Digits(integer = 12, fraction = 6) BigDecimal newTargetQuantity,
                                @NotBlank @Size(max = 255) String reason,
                                @NotBlank @Size(max = 64) String idempotencyKey) {}

    public record TaskResponse(String id, String type, String status, Integer priority,
                               String waveId, String orderId, String orderItemId,
                               String productId, String productSku, String productName,
                               String sourceStockId, String originPositionId,
                               String originPositionCode, String sourceLpnId, String sourceLpnCode,
                               String destinationPositionId, String destinationLpnId,
                               String assignedUserId, BigDecimal targetQuantity,
                               BigDecimal actualQuantity, Instant createdAt,
                               Instant startedAt, Instant completedAt, String originTaskId) {}

    public record WaveResponse(String id, String warehouseId, String code, String status,
                               Instant createdAt, Instant cancelledAt,
                               List<String> orderIds, List<TaskResponse> tasks) {}

    public record DivergenceResponse(String id, String taskId, String stockId, String positionId,
                                     String type, BigDecimal expectedQuantity,
                                     BigDecimal actualQuantity, BigDecimal missingQuantity,
                                     String reason, String status, String authorizedById,
                                     Instant createdAt, List<String> replacementTaskIds) {}

    public record ShortageResponse(TaskResponse task, DivergenceResponse divergence,
                                   List<TaskResponse> replacementTasks,
                                   BigDecimal unallocatedQuantity) {}

    public record ShipmentRequest(@NotBlank @Size(max = 26) String orderId,
                                  @NotBlank @Size(max = 26) String dockPositionId,
                                  @NotEmpty @Size(max = 500) List<@NotBlank @Size(max = 26) String> lpnIds,
                                  @NotBlank @Size(max = 64) String idempotencyKey) {}

    public record ShipmentScanRequest(@NotBlank @Size(max = 100) String lpnCode,
                                       @NotBlank @Size(max = 50) String dockPositionCode,
                                       @NotBlank @Size(max = 64) String idempotencyKey) {}

    public record DispatchRequest(@NotBlank @Size(max = 50) String dockPositionCode,
                                  @NotBlank @Size(max = 64) String idempotencyKey) {}

    public record ShipmentItemResponse(String id, String orderItemId, String stockId,
                                       String lpnId, String lpnCode, String productId,
                                       String sku, BigDecimal quantity, String status,
                                       Instant confirmedAt) {}

    public record ShipmentResponse(String id, String orderId, String dockPositionId,
                                   String status, Instant createdAt, Instant dispatchedAt,
                                   List<ShipmentItemResponse> items) {}
}
