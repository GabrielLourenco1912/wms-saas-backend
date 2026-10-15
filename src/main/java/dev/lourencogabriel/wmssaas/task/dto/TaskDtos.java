package dev.lourencogabriel.wmssaas.task.dto;

import dev.lourencogabriel.wmssaas.task.model.WarehouseTask;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class TaskDtos {
    private TaskDtos() {}

    public record Response(String id,
            String type,
            Integer priority,
            String waveId,
            String orderId,
            String assignedUserId,
            String originPositionId,
            String destinationPositionId,
            String lpnId,
            String productId,
            BigDecimal targetQuantity,
            BigDecimal actualQuantity,
            String status,
            String orderItemId,
            String sourceStockId,
            String destinationLpnId,
            String originTaskId,
            Instant createdAt,
            Instant startedAt,
            Instant completedAt,
            Instant cancelledAt,
            String cancelledById,
            String cancellationReason) {
        public static Response from(WarehouseTask value) {
            return new Response(value.getId(),
                    value.getType(),
                    value.getPriority(),
                    value.getWaveId(),
                    value.getOrderId(),
                    value.getAssignedUserId(),
                    value.getOriginPositionId(),
                    value.getDestinationPositionId(),
                    value.getLpnId(),
                    value.getProductId(),
                    value.getTargetQuantity(),
                    value.getActualQuantity(),
                    value.getStatus(),
                    value.getOrderItemId(),
                    value.getSourceStockId(),
                    value.getDestinationLpnId(),
                    value.getOriginTaskId(),
                    value.getCreatedAt(),
                    value.getStartedAt(),
                    value.getCompletedAt(),
                    value.getCancelledAt(),
                    value.getCancelledById(),
                    value.getCancellationReason());
        }
    }
}
