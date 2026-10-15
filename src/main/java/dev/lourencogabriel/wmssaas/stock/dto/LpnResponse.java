package dev.lourencogabriel.wmssaas.stock.dto;

import dev.lourencogabriel.wmssaas.stock.model.Lpn;
import java.time.Instant;

public record LpnResponse(String id, String trackingCode, String positionId, String status,
                           String outboundOrderId, Instant createdAt, Instant updatedAt) {
    public static LpnResponse from(Lpn value) {
        return new LpnResponse(value.getId(),value.getTrackingCode(),value.getPositionId(),value.getStatus(),
                value.getOutboundOrderId(),value.getCreatedAt(),value.getUpdatedAt());
    }
}
