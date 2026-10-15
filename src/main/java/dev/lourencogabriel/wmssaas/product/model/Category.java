package dev.lourencogabriel.wmssaas.product.model;

import dev.lourencogabriel.wmssaas.shared.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "categorias")
@Getter
@Setter
@NoArgsConstructor
public class Category extends BaseEntity {
    @Column(name = "nome", nullable = false, length = 100)
    private String name;

    @Column(name = "descricao", nullable = true, columnDefinition = "TEXT")
    private String description;

    @Column(name = "ativo", nullable = false)
    private Boolean active = true;

}
