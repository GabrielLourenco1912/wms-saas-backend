package dev.lourencogabriel.wmssaas.operation.model;

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
@Table(name = "operacoes_wms")
@Getter
@Setter
@NoArgsConstructor
public class WmsOperation extends BaseEntity {
    @Column(name = "chave_idempotencia", nullable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "tipo", nullable = false, length = 40)
    private String type;

    @Column(name = "usuario_id", nullable = true, length = 255)
    private String userId;

    @Column(name = "hash_requisicao", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false)
    private Instant createdAt;

    @Column(name = "concluido_em", nullable = true)
    private Instant completedAt;

    @Column(name = "resultado", nullable = true, columnDefinition = "TEXT")
    private String result;

}
