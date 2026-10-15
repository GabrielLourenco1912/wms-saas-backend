package dev.lourencogabriel.wmssaas.stock.model;

import dev.lourencogabriel.wmssaas.shared.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "estoque")
@Getter
@Setter
@NoArgsConstructor
public class Stock extends BaseEntity {
    @Column(name = "produto_id", nullable = false, length = 26)
    private String productId;

    @Column(name = "lpn_id", nullable = true, length = 26)
    private String lpnId;

    @Column(name = "posicao_id", nullable = false, length = 26)
    private String positionId;

    @Column(name = "lote", nullable = true, length = 100)
    private String lot;

    @Column(name = "data_validade", nullable = true)
    private LocalDate expiryDate;

    @Column(name = "quantidade", nullable = false, precision = 18, scale = 6)
    private BigDecimal quantity = BigDecimal.ZERO;

    @Column(name = "status_bloqueio", nullable = true, length = 30)
    private String blockStatus;

    @UpdateTimestamp
    @Column(name = "atualizado_em", nullable = false)
    private Instant updatedAt;

    @Column(name = "data_entrada_em", nullable = false)
    private Instant entryAt;

    @Column(name = "conferencia_origem_id", nullable = true, length = 26)
    private String originConfirmationId;

    @Version
    @Column(name = "versao", nullable = false)
    private Long version;

}
