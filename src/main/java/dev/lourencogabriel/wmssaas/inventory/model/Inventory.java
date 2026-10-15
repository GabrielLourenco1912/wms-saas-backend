package dev.lourencogabriel.wmssaas.inventory.model;

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
@Table(name = "inventarios")
@Getter
@Setter
@NoArgsConstructor
public class Inventory extends BaseEntity {
    @Column(name = "armazem_id", nullable = false, length = 26)
    private String warehouseId;

    @Column(name = "posicao_id", nullable = false, length = 26)
    private String positionId;

    @Column(name = "usuario_responsavel_id", nullable = false, length = 255)
    private String responsibleUserId;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false)
    private Instant createdAt;

    @Column(name = "concluido_em", nullable = true)
    private Instant completedAt;

    @Column(name = "cancelado_em", nullable = true)
    private Instant cancelledAt;

}
