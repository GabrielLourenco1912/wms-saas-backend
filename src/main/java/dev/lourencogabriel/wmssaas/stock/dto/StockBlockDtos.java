package dev.lourencogabriel.wmssaas.stock.dto;

import dev.lourencogabriel.wmssaas.stock.model.StockBlock;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class StockBlockDtos {
    private StockBlockDtos() {}

    public record Response(String id,
            String warehouseId,
            String type,
            String stockId,
            String productId,
            String lot,
            String positionId,
            String reason,
            Boolean active,
            String createdById,
            Instant createdAt,
            String releasedById,
            Instant releasedAt) {
        public static Response from(StockBlock value) {
            return new Response(value.getId(),
                    value.getWarehouseId(),
                    value.getType(),
                    value.getStockId(),
                    value.getProductId(),
                    value.getLot(),
                    value.getPositionId(),
                    value.getReason(),
                    value.getActive(),
                    value.getCreatedById(),
                    value.getCreatedAt(),
                    value.getReleasedById(),
                    value.getReleasedAt());
        }
    }
}
