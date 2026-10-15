package dev.lourencogabriel.wmssaas.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class InventoryDtos {
    private InventoryDtos() {}

    public record CreateRequest(@NotBlank @Size(min = 26, max = 26) String positionId,
                                @Size(max = 255) String responsibleUserId,
                                @NotBlank @Size(max = 50) String scannedPositionCode) {}
    public record CountLine(@Size(min = 26, max = 26) String inventoryItemId,
                            @Size(min = 26, max = 26) String productId,
                            @NotBlank @Size(max = 100) String lpnCode,
                            @NotBlank @Size(max = 100) String scannedProductCode,
                            @Size(max = 100) String lot, LocalDate expiryDate,
                            @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 6) BigDecimal countedQuantity) {}
    public record CountsRequest(@NotBlank @Size(max = 50) String scannedPositionCode,
                                @NotEmpty @Size(max = 500) List<@NotNull @Valid CountLine> items) {}
    public record AdjustRequest(@NotBlank @Size(max = 50) String scannedPositionCode,
                                @NotBlank @Size(max = 255) String reason) {}
    public record CancelRequest(@NotBlank @Size(max = 50) String scannedPositionCode) {}
    public record Summary(String id, String warehouseId, String positionId, String responsibleUserId,
                          String status, Instant createdAt, Instant completedAt, Instant cancelledAt) {}
    public record ItemResponse(String id, String stockId, String productId, String sku, String lpnId,
                               String lpnCode, String lot, LocalDate expiryDate, BigDecimal theoreticalQuantity,
                               BigDecimal countedQuantity, String countedById, Instant countedAt,
                               Instant appliedAt, String applicationOperationId) {}
    public record Detail(Summary inventory, List<ItemResponse> items) {}
}
