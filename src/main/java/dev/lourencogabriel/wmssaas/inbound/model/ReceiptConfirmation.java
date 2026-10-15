package dev.lourencogabriel.wmssaas.inbound.model;

import dev.lourencogabriel.wmssaas.shared.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "conferencias_recebimento")
@Getter
@Setter
@NoArgsConstructor
public class ReceiptConfirmation extends BaseEntity {
    @Column(name = "item_pedido_id", nullable = false, length = 26)
    private String orderItemId;

    @Column(name = "embalagem_id", nullable = false, length = 26)
    private String packagingId;

    @Column(name = "lpn_id", nullable = false, length = 26)
    private String lpnId;

    @Column(name = "posicao_id", nullable = false, length = 26)
    private String positionId;

    @Column(name = "quantidade_informada", nullable = false, precision = 18, scale = 6)
    private BigDecimal informedQuantity;

    @Column(name = "quantidade_base", nullable = false, precision = 18, scale = 6)
    private BigDecimal baseQuantity;

    @Column(name = "lote", nullable = true, length = 100)
    private String lot;

    @Column(name = "data_validade", nullable = true)
    private LocalDate expiryDate;

    @Column(name = "usuario_id", nullable = false, length = 255)
    private String userId;

    @Column(name = "operacao_id", nullable = false, length = 26)
    private String operationId;

    @Column(name = "conferido_em", nullable = false)
    private Instant confirmedAt;

}
