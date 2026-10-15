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
@Table(name = "produto_embalagens")
@Getter
@Setter
@NoArgsConstructor
public class ProductPackaging extends BaseEntity {
    @Column(name = "produto_id", nullable = false, length = 26)
    private String productId;

    @Column(name = "tipo", nullable = false, length = 20)
    private String type;

    @Column(name = "fator_conversao", nullable = false, precision = 18, scale = 6)
    private BigDecimal conversionFactor;

    @Column(name = "codigo_barras", nullable = true, length = 100)
    private String barcode;

    @Column(name = "peso_kg", nullable = true, precision = 18, scale = 6)
    private BigDecimal weightKg;

    @Column(name = "volume_m3", nullable = true, precision = 18, scale = 6)
    private BigDecimal volumeM3;

    @Column(name = "ativo", nullable = false)
    private Boolean active = true;

}
