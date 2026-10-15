package dev.lourencogabriel.wmssaas.outbound.model;

import dev.lourencogabriel.wmssaas.shared.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "itens_expedicao")
@Getter
@Setter
@NoArgsConstructor
public class ShipmentItem extends BaseEntity {
    @Column(name = "expedicao_id", nullable = false, length = 26)
    private String shipmentId;

    @Column(name = "item_pedido_id", nullable = false, length = 26)
    private String orderItemId;

    @Column(name = "estoque_id", nullable = false, length = 26)
    private String stockId;

    @Column(name = "lpn_id", nullable = false, length = 26)
    private String lpnId;

    @Column(name = "quantidade", nullable = false, precision = 18, scale = 6)
    private BigDecimal quantity = BigDecimal.ZERO;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "conferido_por_id", nullable = true, length = 255)
    private String confirmedById;

    @Column(name = "conferido_em", nullable = true)
    private Instant confirmedAt;

}
