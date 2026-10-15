package dev.lourencogabriel.wmssaas.inbound.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Input and output contracts deliberately contain no persistence entities. Quantities use product base units unless packaging is explicit. */
public final class InboundDtos {
    private InboundDtos() {}

    public record ExpectedItem(@NotBlank @Size(min=26,max=26) String productId,
                               @NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=12,fraction=6) BigDecimal quantity) {}
    public record PreReceiptRequest(@NotBlank @Size(min=26,max=26) String warehouseId,
                                    @NotBlank @Size(min=26,max=26) String partnerId,
                                    @NotBlank @Size(max=50) String documentNumber, Instant expectedAt,
                                    @NotEmpty @Size(max=500) List<@NotNull @Valid ExpectedItem> items) {}
    public record ImportRequest(@NotBlank @Size(min=26,max=26) String warehouseId,
                                @NotBlank @Size(min=26,max=26) String partnerId,
                                @NotBlank @Size(max=1000000) String invoiceXml, Instant expectedAt) {}
    public record PreReceiptResponse(String id, String warehouseId, String partnerId, String documentNumber,
                                     String type, Instant expectedAt, String status, Instant createdAt, Instant updatedAt,
                                     String createdById) {}
    public record ReceiptItemResponse(String id, String orderId, String productId, String sku, String productName,
                                      BigDecimal requestedQuantity, BigDecimal fulfilledQuantity, BigDecimal remainingQuantity) {}
    public record ConfirmationResponse(String id, String orderItemId, String packagingId, String lpnId, String lpnCode,
                                        String positionId, String positionCode, BigDecimal informedQuantity, BigDecimal baseQuantity,
                                        String lot, LocalDate expiryDate, String userId, String operationId, Instant confirmedAt) {}
    public record PreReceiptDetail(PreReceiptResponse receipt, List<ReceiptItemResponse> items,
                                    List<ConfirmationResponse> confirmations) {}
    public record ConfirmReceiptRequest(@NotBlank @Size(min=26,max=26) String orderItemId,
                                        @NotBlank @Size(min=26,max=26) String packagingId,
                                        @NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=12,fraction=6) BigDecimal quantity,
                                        @Size(max=100) String lot, LocalDate expiryDate,
                                        @NotBlank @Size(max=100) String lpnCode,
                                        @NotBlank @Size(min=26,max=26) String positionId,
                                        @NotBlank @Size(max=100) String scannedProductCode,
                                        @NotBlank @Size(max=100) String scannedLpnCode,
                                        @NotBlank @Size(max=50) String scannedPositionCode) {}
    public record PutawayContentResponse(String stockId, String productId, String sku, String productName,
                                          BigDecimal quantity, String lot, LocalDate expiryDate, Instant entryAt) {}
    public record PutawayTaskResponse(String id, String type, String status, Integer priority, String warehouseId,
                                       String orderId, String lpnId, String lpnCode, String originPositionId,
                                       String originPositionCode, String destinationPositionId, String destinationPositionCode,
                                       String productId, BigDecimal targetQuantity, BigDecimal actualQuantity,
                                       String assignedUserId, Instant createdAt, Instant startedAt, Instant completedAt,
                                       List<PutawayContentResponse> contents) {}
    public record ReceiptResult(ConfirmationResponse confirmation, String stockId, PutawayTaskResponse putawayTask) {}
    public record CompletePutawayRequest(@NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=12,fraction=6) BigDecimal actualQuantity,
                                         @NotBlank @Size(max=100) String scannedLpnCode,
                                         @NotBlank @Size(max=50) String scannedDestinationCode) {}
}
