package dev.lourencogabriel.wmssaas.warehouse.dto;

import dev.lourencogabriel.wmssaas.warehouse.model.Warehouse;
import dev.lourencogabriel.wmssaas.warehouse.model.Zone;
import dev.lourencogabriel.wmssaas.warehouse.model.Position;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;

public final class WarehouseDtos {
    private WarehouseDtos() {}

    public record WarehouseRequest(@NotBlank @Size(max=100) String name,
            @Pattern(regexp="[0-9]{14}") String taxId, @Size(max=255) String address,
            @NotNull Boolean active) {}

    public record ZoneRequest(@NotBlank @Size(min=26,max=26) String warehouseId,
            @NotBlank @Size(max=50) String name, @Size(max=50) String controlType,
            @NotNull Boolean active) {}

    public record PositionRequest(@NotBlank @Size(min=26,max=26) String zoneId,
            @NotBlank @Size(max=50) String code, @NotBlank @Size(max=50) String type,
            @DecimalMin(value="0",inclusive=false) @Digits(integer=12,fraction=6) BigDecimal weightCapacityKg,
            @DecimalMin(value="0",inclusive=false) @Digits(integer=12,fraction=6) BigDecimal volumeCapacityM3,
            @NotBlank @Pattern(regexp="RECEBIMENTO|ARMAZENAGEM|EXPEDICAO") String purpose,
            @NotNull Boolean active) {}

    public record WarehouseResponse(String id, String name, String taxId, String address, Instant createdAt, Boolean active, Instant updatedAt) {
        public static WarehouseResponse from(Warehouse e) { return new WarehouseResponse(e.getId(), e.getName(), e.getTaxId(), e.getAddress(), e.getCreatedAt(), e.getActive(), e.getUpdatedAt()); }
    }

    public record ZoneResponse(String id, String warehouseId, String name, String controlType, Boolean active) {
        public static ZoneResponse from(Zone e) { return new ZoneResponse(e.getId(), e.getWarehouseId(), e.getName(), e.getControlType(), e.getActive()); }
    }

    public record PositionResponse(String id, String zoneId, String code, String type, BigDecimal weightCapacityKg, BigDecimal volumeCapacityM3, String status, Boolean active, String purpose) {
        public static PositionResponse from(Position e) { return new PositionResponse(e.getId(), e.getZoneId(), e.getCode(), e.getType(), e.getWeightCapacityKg(), e.getVolumeCapacityM3(), e.getStatus(), e.getActive(), e.getPurpose()); }
    }
}
