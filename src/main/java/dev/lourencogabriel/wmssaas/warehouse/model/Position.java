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
@Table(name = "posicoes")
@Getter
@Setter
@NoArgsConstructor
public class Position extends BaseEntity {
    @Column(name = "zona_id", nullable = false, length = 26)
    private String zoneId;

    @Column(name = "codigo", nullable = false, length = 50)
    private String code;

    @Column(name = "tipo", nullable = false, length = 50)
    private String type = "FIXA";

    @Column(name = "capacidade_peso_kg", nullable = true, precision = 18, scale = 6)
    private BigDecimal weightCapacityKg;

    @Column(name = "capacidade_volume_m3", nullable = true, precision = 18, scale = 6)
    private BigDecimal volumeCapacityM3;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "LIVRE";

    @Column(name = "ativo", nullable = false)
    private Boolean active = true;

    @Column(name = "finalidade", nullable = false, length = 20)
    private String purpose = "ARMAZENAGEM";

}
