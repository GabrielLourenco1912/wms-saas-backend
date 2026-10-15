package dev.lourencogabriel.wmssaas.task.model;

import dev.lourencogabriel.wmssaas.shared.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "historico_tarefas")
@Getter
@Setter
@NoArgsConstructor
@Immutable
public class TaskHistory extends BaseEntity {
    @Column(name = "tarefa_id", nullable = false, length = 26, updatable = false)
    private String taskId;

    @Column(name = "status_anterior", nullable = true, length = 20, updatable = false)
    private String previousStatus;

    @Column(name = "status_novo", nullable = false, length = 20, updatable = false)
    private String newStatus;

    @Column(name = "usuario_id", nullable = true, length = 255, updatable = false)
    private String userId;

    @Column(name = "operacao_id", nullable = false, length = 26, updatable = false)
    private String operationId;

    @Column(name = "motivo", nullable = true, length = 255, updatable = false)
    private String reason;

    @Column(name = "registrado_em", nullable = false, updatable = false)
    private Instant recordedAt;

    @PreUpdate
    @PreRemove
    private void preventMutation() {
        throw new IllegalStateException("historico_tarefas is append-only");
    }

}
