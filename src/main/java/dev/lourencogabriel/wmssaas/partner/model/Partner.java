package dev.lourencogabriel.wmssaas.partner.model;

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
@Table(name = "terceiros")
@Getter
@Setter
@NoArgsConstructor
public class Partner extends BaseEntity {
    @Column(name = "razao_social", nullable = false, length = 150)
    private String legalName;

    @Column(name = "cpf_cnpj", nullable = true, length = 14)
    private String taxId;

    @Column(name = "tipo", nullable = false, length = 30)
    private String type;

    @Column(name = "ativo", nullable = false)
    private Boolean active = true;

    @UpdateTimestamp
    @Column(name = "atualizado_em", nullable = false)
    private Instant updatedAt;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false)
    private Instant createdAt;

}
