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
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "ondas_separacao")
@Getter
@Setter
@NoArgsConstructor
public class Wave extends BaseEntity {
    @Column(name = "codigo_onda", nullable = false, length = 50)
    private String code;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "atualizado_em", nullable = false)
    private Instant updatedAt;

    @Column(name = "armazem_id", nullable = false, length = 26)
    private String warehouseId;

    @Column(name = "criado_por_id", nullable = false, length = 255)
    private String createdById;

    @Column(name = "cancelado_em", nullable = true)
    private Instant cancelledAt;

    @Column(name = "cancelado_por_id", nullable = true, length = 255)
    private String cancelledById;

    @Column(name = "motivo_cancelamento", nullable = true, length = 255)
    private String cancellationReason;

}
