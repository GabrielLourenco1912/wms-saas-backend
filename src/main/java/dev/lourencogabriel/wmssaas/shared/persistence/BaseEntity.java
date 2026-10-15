package dev.lourencogabriel.wmssaas.shared.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @GeneratedUlid
    @Column(name = "id", length = 26, nullable = false, updatable = false)
    private String id;
}
