package dev.lourencogabriel.wmssaas.order.model;

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
@Table(name = "pedidos")
@Getter
@Setter
@NoArgsConstructor
public class WarehouseOrder extends BaseEntity {
    @Column(name = "terceiro_id", nullable = false, length = 26)
    private String partnerId;

    @Column(name = "numero_documento", nullable = true, length = 50)
    private String documentNumber;

    @Column(name = "tipo", nullable = false, length = 20)
    private String type;

    @Column(name = "data_prevista", nullable = true)
    private Instant expectedAt;

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
