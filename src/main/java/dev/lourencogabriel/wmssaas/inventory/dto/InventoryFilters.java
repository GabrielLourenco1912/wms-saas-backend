package dev.lourencogabriel.wmssaas.inventory.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class InventoryFilters {
    @Size(max=26) private String warehouseId;
    @Size(max=26) private String positionId;
    @Size(max=20) private String status;
    @Size(max=255) private String responsibleUserId;
}
