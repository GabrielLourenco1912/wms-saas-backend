package dev.lourencogabriel.wmssaas.audit.dto;

import dev.lourencogabriel.wmssaas.audit.model.StockTransaction;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class TransactionDtos {
    private TransactionDtos() {}

    public record Response(String id,
            String productId,
            String userId,
            String taskId,
            String lpnId,
            String movementType,
            BigDecimal quantity,
            String previousPositionId,
            String newPositionId,
            BigDecimal resultingBalance,
            Instant occurredAt,
            String operationId,
            Integer sequence,
            String stockId,
            String destinationStockId,
            String destinationLpnId,
            String recordedLot,
            LocalDate recordedExpiryDate,
            Instant recordedEntryAt,
            BigDecimal previousBalance,
            BigDecimal destinationPreviousBalance,
            BigDecimal destinationResultingBalance,
            String compensatedTransactionId,
            String reason,
            String inventoryItemId,
            String shipmentItemId) {
        public static Response from(StockTransaction value) {
            return new Response(value.getId(),
                    value.getProductId(),
                    value.getUserId(),
                    value.getTaskId(),
                    value.getLpnId(),
                    value.getMovementType(),
                    value.getQuantity(),
                    value.getPreviousPositionId(),
                    value.getNewPositionId(),
                    value.getResultingBalance(),
                    value.getOccurredAt(),
                    value.getOperationId(),
                    value.getSequence(),
                    value.getStockId(),
                    value.getDestinationStockId(),
                    value.getDestinationLpnId(),
                    value.getRecordedLot(),
                    value.getRecordedExpiryDate(),
                    value.getRecordedEntryAt(),
                    value.getPreviousBalance(),
                    value.getDestinationPreviousBalance(),
                    value.getDestinationResultingBalance(),
                    value.getCompensatedTransactionId(),
                    value.getReason(),
                    value.getInventoryItemId(),
                    value.getShipmentItemId());
        }
    }
}
