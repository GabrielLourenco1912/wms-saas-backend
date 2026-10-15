package dev.lourencogabriel.wmssaas.product.model;

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
@Table(name = "produtos")
@Getter
@Setter
@NoArgsConstructor
public class Product extends BaseEntity {
    @Column(name = "categoria_id", nullable = false, length = 26)
    private String categoryId;

    @Column(name = "sku", nullable = false, length = 50)
    private String sku;

    @Column(name = "nome", nullable = false, length = 150)
    private String name;

    @Column(name = "controle_lote", nullable = false)
    private Boolean lotControlled = false;

    @Column(name = "controle_validade", nullable = false)
    private Boolean expiryControlled = false;

    @Column(name = "dias_shelf_life", nullable = true)
    private Integer shelfLifeDays;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false)
    private Instant createdAt;

    @Column(name = "ativo", nullable = false)
    private Boolean active = true;

    @UpdateTimestamp
    @Column(name = "atualizado_em", nullable = false)
    private Instant updatedAt;

    @Column(name = "unidade_base", nullable = false, length = 10)
    private String baseUnit = "UN";

    @Column(name = "peso_unitario_kg", nullable = false, precision = 18, scale = 6)
    private BigDecimal unitWeightKg = BigDecimal.ZERO;

    @Column(name = "volume_unitario_m3", nullable = false, precision = 18, scale = 6)
    private BigDecimal unitVolumeM3 = BigDecimal.ZERO;

}
