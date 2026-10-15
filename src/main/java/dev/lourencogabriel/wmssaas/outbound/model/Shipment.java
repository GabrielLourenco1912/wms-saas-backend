package dev.lourencogabriel.wmssaas.outbound.model;

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
@Table(name = "expedicoes")
@Getter
@Setter
@NoArgsConstructor
public class Shipment extends BaseEntity {
    @Column(name = "pedido_id", nullable = false, length = 26)
    private String orderId;

    @Column(name = "posicao_doca_id", nullable = false, length = 26)
    private String dockPositionId;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "criado_por_id", nullable = false, length = 255)
    private String createdById;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false)
    private Instant createdAt;

    @Column(name = "despachado_por_id", nullable = true, length = 255)
    private String dispatchedById;

    @Column(name = "despachado_em", nullable = true)
    private Instant dispatchedAt;

}
