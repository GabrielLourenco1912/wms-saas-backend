package dev.lourencogabriel.wmssaas.stock.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.Instant;

/** Exact-match filters complement the shared free-text/page query. Empty values are ignored. */
public final class StockSearchFilters {
    private StockSearchFilters() {}
    @Getter @Setter
    public static class Balances {
        @Size(max=26) private String productId;
        @Size(max=26) private String positionId;
        @Size(max=26) private String lpnId;
        @Size(max=26) private String warehouseId;
        @Size(max=100) private String lot;
        @Size(max=30) private String blockStatus;
    }
    @Getter @Setter
    public static class Blocks {
        @Size(max=26) private String warehouseId;
        @Size(max=26) private String stockId;
        @Size(max=26) private String productId;
        @Size(max=26) private String positionId;
        @Size(max=100) private String lot;
        @Size(max=20) private String type;
        private Boolean active;
    }
    @Getter @Setter
    public static class Tasks {
        @Size(max=30) private String type;
        @Size(max=20) private String status;
        @Size(max=255) private String assignedUserId;
        @Size(max=26) private String orderId;
        @Size(max=26) private String productId;
        @Size(max=26) private String warehouseId;
        @Size(max=26) private String lpnId;
        @Size(max=26) private String waveId;
        @Size(max=26) private String originPositionId;
        @Size(max=26) private String destinationPositionId;
    }
    @Getter @Setter
    public static class Ledger {
        @Size(max=26) private String stockId;
        @Size(max=26) private String productId;
        @Size(max=26) private String operationId;
        @Size(max=26) private String lpnId;
        @Size(max=26) private String warehouseId;
        @Size(max=255) private String userId;
        @Size(max=50) private String movementType;
        @Size(max=100) private String lot;
        @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) private Instant from;
        @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) private Instant to;
        @AssertTrue(message="from must be earlier than or equal to to")
        public boolean isDateRangeValid() { return from==null || to==null || !from.isAfter(to); }
    }
    @Getter @Setter
    public static class Lpns {
        @Size(max=26) private String warehouseId;
        @Size(max=26) private String positionId;
        @Size(max=26) private String outboundOrderId;
        @Size(max=30) private String status;
    }
}
