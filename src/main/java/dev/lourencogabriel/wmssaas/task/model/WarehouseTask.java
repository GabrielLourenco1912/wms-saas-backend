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
@Table(name = "tarefas")
@Getter
@Setter
@NoArgsConstructor
public class WarehouseTask extends BaseEntity {
    @Column(name = "tipo", nullable = false, length = 30)
    private String type;

    @Column(name = "prioridade", nullable = true)
    private Integer priority = 0;

    @Column(name = "onda_id", nullable = true, length = 26)
    private String waveId;

    @Column(name = "pedido_id", nullable = true, length = 26)
    private String orderId;

    @Column(name = "usuario_atribuido_id", nullable = true, length = 255)
    private String assignedUserId;

    @Column(name = "posicao_origem_id", nullable = true, length = 26)
    private String originPositionId;

    @Column(name = "posicao_destino_id", nullable = true, length = 26)
    private String destinationPositionId;

    @Column(name = "lpn_id", nullable = true, length = 26)
    private String lpnId;

    @Column(name = "produto_id", nullable = true, length = 26)
    private String productId;

    @Column(name = "quantidade_alvo", nullable = false, precision = 18, scale = 6)
    private BigDecimal targetQuantity;

    @Column(name = "quantidade_realizada", nullable = true, precision = 18, scale = 6)
    private BigDecimal actualQuantity;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "item_pedido_id", nullable = true, length = 26)
    private String orderItemId;

    @Column(name = "estoque_origem_id", nullable = true, length = 26)
    private String sourceStockId;

    @Column(name = "lpn_destino_id", nullable = true, length = 26)
    private String destinationLpnId;

    @Column(name = "tarefa_origem_id", nullable = true, length = 26)
    private String originTaskId;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false)
    private Instant createdAt;

    @Column(name = "iniciado_em", nullable = true)
    private Instant startedAt;

    @Column(name = "concluido_em", nullable = true)
    private Instant completedAt;

    @Column(name = "cancelado_em", nullable = true)
    private Instant cancelledAt;

    @Column(name = "cancelado_por_id", nullable = true, length = 255)
    private String cancelledById;

    @Column(name = "motivo_cancelamento", nullable = true, length = 255)
    private String cancellationReason;

    @Version
    @Column(name = "versao", nullable = false)
    private Long version;

}
