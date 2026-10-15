package dev.lourencogabriel.wmssaas.task.dto;

import dev.lourencogabriel.wmssaas.task.model.TaskHistory;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class TaskHistoryDtos {
    private TaskHistoryDtos() {}

    public record Response(String id,
            String taskId,
            String previousStatus,
            String newStatus,
            String userId,
            String operationId,
            String reason,
            Instant recordedAt) {
        public static Response from(TaskHistory value) {
            return new Response(value.getId(),
                    value.getTaskId(),
                    value.getPreviousStatus(),
                    value.getNewStatus(),
                    value.getUserId(),
                    value.getOperationId(),
                    value.getReason(),
                    value.getRecordedAt());
        }
    }
}
