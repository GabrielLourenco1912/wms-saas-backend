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
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "lpns")
@Getter
@Setter
@NoArgsConstructor
public class Lpn extends BaseEntity {
    @Column(name = "codigo_rastreio", nullable = false, length = 100)
    private String trackingCode;

    @Column(name = "posicao_id", nullable = true, length = 26)
    private String positionId;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "atualizado_em", nullable = false)
    private Instant updatedAt;

    @Column(name = "pedido_saida_id", nullable = true, length = 26)
    private String outboundOrderId;

}
