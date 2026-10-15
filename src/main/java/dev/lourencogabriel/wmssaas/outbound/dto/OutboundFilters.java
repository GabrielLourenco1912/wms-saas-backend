package dev.lourencogabriel.wmssaas.outbound.dto;

/** Optional exact-match filters, shared by collection, search and lookup routes. */
public final class OutboundFilters {
    private OutboundFilters() {}

    public record Orders(String warehouseId, String status, String partnerId, String documentNumber, String productId) {}
    public record Waves(String warehouseId, String status, String code, String orderId) {}
    public record PickingTasks(String warehouseId, String status, String assignedUserId, String orderId, String productId, String waveId) {}
    public record Divergences(String warehouseId, String status, String type, String taskId, String stockId) {}
    public record Shipments(String warehouseId, String status, String orderId, String dockPositionId) {}
}
