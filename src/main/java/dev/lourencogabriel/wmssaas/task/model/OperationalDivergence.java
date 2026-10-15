package dev.lourencogabriel.wmssaas.task.model;

import dev.lourencogabriel.wmssaas.shared.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "divergencias_operacionais")
@Getter
@Setter
@NoArgsConstructor
public class OperationalDivergence extends BaseEntity {
    @Column(name = "tarefa_id", nullable = false, length = 26)
    private String taskId;

    @Column(name = "estoque_id", nullable = true, length = 26)
    private String stockId;

    @Column(name = "posicao_id", nullable = false, length = 26)
    private String positionId;

    @Column(name = "usuario_id", nullable = false, length = 255)
    private String userId;

    @Column(name = "quantidade_prevista", nullable = false, precision = 18, scale = 6)
    private BigDecimal expectedQuantity;

    @Column(name = "quantidade_realizada", nullable = false, precision = 18, scale = 6)
    private BigDecimal actualQuantity;

    @Column(name = "quantidade_faltante", nullable = false, precision = 18, scale = 6)
    private BigDecimal missingQuantity;

    @Column(name = "tipo", nullable = false, length = 20)
    private String type;

    @Column(name = "motivo", nullable = false, length = 255)
    private String reason;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false)
    private Instant createdAt;

    @Column(name = "resolvido_em", nullable = true)
    private Instant resolvedAt;

    @Column(name = "autorizado_por_id", nullable = true, length = 255)
    private String authorizedById;

    @Column(name = "autorizado_em", nullable = true)
    private Instant authorizedAt;

}
