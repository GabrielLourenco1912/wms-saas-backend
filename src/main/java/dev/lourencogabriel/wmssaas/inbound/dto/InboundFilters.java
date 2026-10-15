package dev.lourencogabriel.wmssaas.inbound.dto;

/** Optional exact-match filters, shared by collection, search and lookup routes. */
public final class InboundFilters {
    private InboundFilters() {}

    public record Receipts(String warehouseId, String status, String partnerId,
                           String documentNumber, String productId) {}

    public record PutawayTasks(String warehouseId, String status, String assignedUserId,
                              String orderId, String productId, String lpnId) {}
}
