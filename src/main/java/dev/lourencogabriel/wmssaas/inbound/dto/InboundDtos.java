package dev.lourencogabriel.wmssaas.inbound.dto;

import dev.lourencogabriel.wmssaas.inbound.model.ReceiptConfirmation;
import dev.lourencogabriel.wmssaas.order.model.OrderItem;
import dev.lourencogabriel.wmssaas.order.model.WarehouseOrder;
import dev.lourencogabriel.wmssaas.task.model.WarehouseTask;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class InboundDtos {
    private InboundDtos() {}
    public record ExpectedItem(@NotBlank String productId,
                               @NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=12,fraction=6) BigDecimal quantity) {}
    public record PreReceiptRequest(@NotBlank String warehouseId, @NotBlank String partnerId,
                                    @NotBlank @Size(max=50) String documentNumber, Instant expectedAt,
                                    @NotEmpty @Size(max=500) List<@Valid ExpectedItem> items) {}
    public record ImportRequest(@NotBlank String warehouseId, @NotBlank String partnerId,
                                @NotBlank @Size(max=1000000) String invoiceXml, Instant expectedAt) {}
    public record PreReceiptDetail(WarehouseOrder receipt, List<OrderItem> items, List<ReceiptConfirmation> confirmations) {}
    public record ConfirmReceiptRequest(@NotBlank String orderItemId, @NotBlank String packagingId,
                                        @NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=12,fraction=6) BigDecimal quantity,
                                        @Size(max=100) String lot, LocalDate expiryDate,
                                        @NotBlank @Size(max=100) String lpnCode, @NotBlank String positionId,
                                        @NotBlank String scannedProductCode, @NotBlank String scannedLpnCode,
                                        @NotBlank String scannedPositionCode) {}
    public record ReceiptResult(ReceiptConfirmation confirmation, String stockId, WarehouseTask putawayTask) {}
    public record CompletePutawayRequest(@NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=12,fraction=6) BigDecimal actualQuantity,
                                         @NotBlank String scannedLpnCode, @NotBlank String scannedDestinationCode) {}
}
