package dev.lourencogabriel.wmssaas.order.model;

import dev.lourencogabriel.wmssaas.shared.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "itens_pedido")
@Getter
@Setter
@NoArgsConstructor
public class OrderItem extends BaseEntity {
    @Column(name = "pedido_id", nullable = false, length = 26)
    private String orderId;

    @Column(name = "produto_id", nullable = false, length = 26)
    private String productId;

    @Column(name = "quantidade_solicitada", nullable = false, precision = 18, scale = 6)
    private BigDecimal requestedQuantity;

    @Column(name = "quantidade_atendida", nullable = false, precision = 18, scale = 6)
    private BigDecimal fulfilledQuantity = BigDecimal.ZERO;

    @Column(name = "quantidade_separada", nullable = false, precision = 18, scale = 6)
    private BigDecimal pickedQuantity = BigDecimal.ZERO;

}
