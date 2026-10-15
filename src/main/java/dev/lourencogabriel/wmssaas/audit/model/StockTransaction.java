package dev.lourencogabriel.wmssaas.audit.model;

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
@Table(name = "transacoes_estoque")
@Getter
@Setter
@NoArgsConstructor
@Immutable
public class StockTransaction extends BaseEntity {
    @Column(name = "produto_id", nullable = false, length = 26, updatable = false)
    private String productId;

    @Column(name = "usuario_id", nullable = false, length = 255, updatable = false)
    private String userId;

    @Column(name = "tarefa_id", nullable = true, length = 26, updatable = false)
    private String taskId;

    @Column(name = "lpn_id", nullable = true, length = 26, updatable = false)
    private String lpnId;

    @Column(name = "tipo_movimento", nullable = false, length = 50, updatable = false)
    private String movementType;

    @Column(name = "quantidade", nullable = false, precision = 18, scale = 6, updatable = false)
    private BigDecimal quantity = BigDecimal.ZERO;

    @Column(name = "posicao_anterior_id", nullable = true, length = 26, updatable = false)
    private String previousPositionId;

    @Column(name = "posicao_nova_id", nullable = true, length = 26, updatable = false)
    private String newPositionId;

    @Column(name = "saldo_resultante", nullable = false, precision = 18, scale = 6, updatable = false)
    private BigDecimal resultingBalance;

    @Column(name = "data_hora", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "operacao_id", nullable = false, length = 26, updatable = false)
    private String operationId;

    @Column(name = "sequencia", nullable = false, updatable = false)
    private Integer sequence;

    @Column(name = "estoque_id", nullable = false, length = 26, updatable = false)
    private String stockId;

    @Column(name = "estoque_destino_id", nullable = true, length = 26, updatable = false)
    private String destinationStockId;

    @Column(name = "lpn_destino_id", nullable = true, length = 26, updatable = false)
    private String destinationLpnId;

    @Column(name = "lote_registrado", nullable = true, length = 100, updatable = false)
    private String recordedLot;

    @Column(name = "validade_registrada", nullable = true, updatable = false)
    private LocalDate recordedExpiryDate;

    @Column(name = "entrada_registrada_em", nullable = false, updatable = false)
    private Instant recordedEntryAt;

    @Column(name = "saldo_anterior", nullable = false, precision = 18, scale = 6, updatable = false)
    private BigDecimal previousBalance;

    @Column(name = "saldo_destino_anterior", nullable = true, precision = 18, scale = 6, updatable = false)
    private BigDecimal destinationPreviousBalance;

    @Column(name = "saldo_destino_resultante", nullable = true, precision = 18, scale = 6, updatable = false)
    private BigDecimal destinationResultingBalance;

    @Column(name = "transacao_compensada_id", nullable = true, length = 26, updatable = false)
    private String compensatedTransactionId;

    @Column(name = "motivo", nullable = true, length = 255, updatable = false)
    private String reason;

    @Column(name = "inventario_item_id", nullable = true, length = 26, updatable = false)
    private String inventoryItemId;

    @Column(name = "expedicao_item_id", nullable = true, length = 26, updatable = false)
    private String shipmentItemId;

    @PreUpdate
    @PreRemove
    private void preventMutation() {
        throw new IllegalStateException("transacoes_estoque is append-only");
    }

}
