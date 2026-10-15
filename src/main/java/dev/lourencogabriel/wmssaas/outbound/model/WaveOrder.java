package dev.lourencogabriel.wmssaas.outbound.model;

import dev.lourencogabriel.wmssaas.shared.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "onda_pedidos")
@Getter
@Setter
@NoArgsConstructor
@IdClass(WaveOrderId.class)
public class WaveOrder {
    @Id
    @Column(name = "onda_id", nullable = false, length = 26)
    private String waveId;

    @Id
    @Column(name = "pedido_id", nullable = false, length = 26)
    private String orderId;

}
