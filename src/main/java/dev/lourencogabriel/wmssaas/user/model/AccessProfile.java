package dev.lourencogabriel.wmssaas.user.model;

import dev.lourencogabriel.wmssaas.auth.authorization.WmsPermission;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "access_profiles")
@Getter
@Setter
@NoArgsConstructor
public class AccessProfile {
    @Id
    @Column(length = 32, nullable = false, updatable = false)
    private String code;

    @Column(length = 100, nullable = false)
    private String name;

    @Column(nullable = false)
    private boolean active = true;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "profile_permissions", joinColumns = @JoinColumn(name = "profile_code"))
    @Enumerated(EnumType.STRING)
    @Column(name = "permission", length = 64, nullable = false)
    private Set<WmsPermission> permissions = new HashSet<>();

    @Version
    private long version;

    public AccessProfile(String code, String name, Set<WmsPermission> permissions) {
        this.code = code;
        this.name = name;
        this.permissions = new HashSet<>(permissions);
    }
}
