package dev.lourencogabriel.wmssaas.stock.model;

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
@Table(name = "reservas_estoque")
@Getter
@Setter
@NoArgsConstructor
public class StockReservation extends BaseEntity {
    @Column(name = "estoque_id", nullable = false, length = 26)
    private String stockId;

    @Column(name = "tarefa_id", nullable = false, length = 26)
    private String taskId;

    @Column(name = "item_pedido_id", nullable = false, length = 26)
    private String orderItemId;

    @Column(name = "quantidade_reservada", nullable = false, precision = 18, scale = 6)
    private BigDecimal reservedQuantity;

    @Column(name = "quantidade_consumida", nullable = false, precision = 18, scale = 6)
    private BigDecimal consumedQuantity = BigDecimal.ZERO;

    @Column(name = "quantidade_liberada", nullable = false, precision = 18, scale = 6)
    private BigDecimal releasedQuantity = BigDecimal.ZERO;

    @Column(name = "status", nullable = false, length = 15)
    private String status;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false)
    private Instant createdAt;

    @Column(name = "encerrado_em", nullable = true)
    private Instant closedAt;

    @Column(name = "motivo_encerramento", nullable = true, length = 255)
    private String closureReason;

    public BigDecimal remainingQuantity() {
        return reservedQuantity.subtract(consumedQuantity).subtract(releasedQuantity);
    }

}
