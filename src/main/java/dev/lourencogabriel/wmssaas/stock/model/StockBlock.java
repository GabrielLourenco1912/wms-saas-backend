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
@Table(name = "bloqueios_estoque")
@Getter
@Setter
@NoArgsConstructor
public class StockBlock extends BaseEntity {
    @Column(name = "armazem_id", nullable = false, length = 26)
    private String warehouseId;

    @Column(name = "tipo", nullable = false, length = 15)
    private String type;

    @Column(name = "estoque_id", nullable = true, length = 26)
    private String stockId;

    @Column(name = "produto_id", nullable = true, length = 26)
    private String productId;

    @Column(name = "lote", nullable = true, length = 100)
    private String lot;

    @Column(name = "posicao_id", nullable = true, length = 26)
    private String positionId;

    @Column(name = "motivo", nullable = false, length = 255)
    private String reason;

    @Column(name = "ativo", nullable = false)
    private Boolean active = true;

    @Column(name = "criado_por_id", nullable = false, length = 255)
    private String createdById;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false)
    private Instant createdAt;

    @Column(name = "liberado_por_id", nullable = true, length = 255)
    private String releasedById;

    @Column(name = "liberado_em", nullable = true)
    private Instant releasedAt;

}
