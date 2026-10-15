package dev.lourencogabriel.wmssaas.inventory.model;

import dev.lourencogabriel.wmssaas.shared.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "itens_inventario")
@Getter
@Setter
@NoArgsConstructor
public class InventoryItem extends BaseEntity {
    @Column(name = "inventario_id", nullable = false, length = 26)
    private String inventoryId;

    @Column(name = "estoque_id", nullable = true, length = 26)
    private String stockId;

    @Column(name = "produto_id", nullable = false, length = 26)
    private String productId;

    @Column(name = "lpn_id", nullable = false, length = 26)
    private String lpnId;

    @Column(name = "lote", nullable = true, length = 100)
    private String lot;

    @Column(name = "data_validade", nullable = true)
    private LocalDate expiryDate;

    @Column(name = "quantidade_teorica", nullable = false, precision = 18, scale = 6)
    private BigDecimal theoreticalQuantity;

    @Column(name = "quantidade_contada", nullable = true, precision = 18, scale = 6)
    private BigDecimal countedQuantity;

    @Column(name = "usuario_contagem_id", nullable = true, length = 255)
    private String countedById;

    @Column(name = "contado_em", nullable = true)
    private Instant countedAt;

    @Column(name = "aplicado_em", nullable = true)
    private Instant appliedAt;

    @Column(name = "operacao_aplicacao_id", nullable = true, length = 26)
    private String applicationOperationId;

}
