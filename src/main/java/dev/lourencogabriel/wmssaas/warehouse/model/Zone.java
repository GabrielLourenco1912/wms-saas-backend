package dev.lourencogabriel.wmssaas.warehouse.model;

import dev.lourencogabriel.wmssaas.shared.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "zonas")
@Getter
@Setter
@NoArgsConstructor
public class Zone extends BaseEntity {
    @Column(name = "armazem_id", nullable = false, length = 26)
    private String warehouseId;

    @Column(name = "nome", nullable = false, length = 50)
    private String name;

    @Column(name = "tipo_controle", nullable = true, length = 50)
    private String controlType;

    @Column(name = "ativo", nullable = false)
    private Boolean active = true;

}
