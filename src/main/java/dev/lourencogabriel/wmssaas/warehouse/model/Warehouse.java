package dev.lourencogabriel.wmssaas.warehouse.model;

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
@Table(name = "armazens")
@Getter
@Setter
@NoArgsConstructor
public class Warehouse extends BaseEntity {
    @Column(name = "nome", nullable = false, length = 100)
    private String name;

    @Column(name = "cnpj", nullable = true, length = 14)
    private String taxId;

    @Column(name = "endereco", nullable = true, length = 255)
    private String address;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false)
    private Instant createdAt;

    @Column(name = "ativo", nullable = false)
    private Boolean active = true;

    @UpdateTimestamp
    @Column(name = "atualizado_em", nullable = false)
    private Instant updatedAt;

}
