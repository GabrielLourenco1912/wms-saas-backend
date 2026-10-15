package dev.lourencogabriel.wmssaas.stock.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class StockDtos {
    private StockDtos() {}
    public record StockView(String id, String productId, String sku, String productName,
                            String lpnId, String lpnCode, String positionId, String positionCode,
                            String lot, LocalDate expiryDate, BigDecimal quantity,
                            BigDecimal reservedQuantity, BigDecimal availableQuantity,
                            String blockStatus, Instant entryAt) {}
    public record ReasonRequest(@NotBlank @Size(max=255) String reason) {}
    public record LotBlockRequest(@NotBlank String warehouseId, @NotBlank String productId,
                                  @NotBlank @Size(max=100) String lot,
                                  @NotBlank @Size(max=255) String reason) {}
    public record TransferRequest(String lpnId, String stockId, @NotBlank String destinationPositionId,
                                  String destinationLpnCode,
                                  @NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=12,fraction=6) BigDecimal quantity) {}
    public record ConfirmTransferRequest(@NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=12,fraction=6) BigDecimal actualQuantity,
                                         @NotBlank String scannedLpnCode, @NotBlank String scannedOriginCode,
                                         @NotBlank String scannedDestinationCode, String scannedProductCode) {}
    public record TaskQuantityRequest(@NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=12,fraction=6) BigDecimal quantity,
                                      @NotBlank @Size(max=255) String reason) {}
    public record CompensationRequest(@NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=12,fraction=6) BigDecimal quantity,
                                      @NotBlank @Size(max=255) String reason,
                                      @NotBlank String scannedLpnCode, @NotBlank String scannedPositionCode,
                                      @NotBlank String scannedProductCode) {}
    public record MutationResult(String id, String status, String stockId, String taskId) {}
}
