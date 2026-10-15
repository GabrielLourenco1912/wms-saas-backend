package dev.lourencogabriel.wmssaas.user.model;

import dev.lourencogabriel.wmssaas.shared.model.UserAccountStatus;
import dev.lourencogabriel.wmssaas.shared.persistence.GeneratedUlid;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.jspecify.annotations.NullMarked;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(name = "uk_users_email", columnNames = "email"))
@Getter
@Setter
@NoArgsConstructor
public class User implements UserDetails {
    @Id
    @GeneratedUlid
    @Column(length = 26, nullable = false, updatable = false)
    private String ulid;

    @Column(length = 150, nullable = false)
    private String name;

    @Column(length = 254, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_code", nullable = false)
    private AccessProfile profile;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private UserAccountStatus status = UserAccountStatus.INVITED;

    @Column(name = "failed_authentication_attempts", nullable = false)
    private Integer failedAuthenticationAttempts = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_before_lock", length = 20)
    private UserAccountStatus statusBeforeLock;

    @Column(name = "token_version", nullable = false)
    private Long tokenVersion = 0L;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    public User(String name, String email, String password) {
        this.name = name;
        this.email = email;
        this.password = password;
    }

    @NullMarked
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (this.profile == null || !this.profile.isActive()) {
            return Set.of();
        }
        Set<GrantedAuthority> authorities = new HashSet<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + this.profile.getCode()));
        this.profile.getPermissions().forEach(permission ->
                authorities.add(new SimpleGrantedAuthority(permission.authority())));
        return Set.copyOf(authorities);
    }

    @NullMarked
    @Override
    public String getUsername() {
        return this.email;
    }

    @Override
    public boolean isAccountNonLocked() {
        return this.status != UserAccountStatus.LOCKED;
    }

    @Override
    public boolean isEnabled() {
        return (this.status == UserAccountStatus.ACTIVE || this.status == UserAccountStatus.INVITED)
                && this.profile != null && this.profile.isActive();
    }

    public boolean hasActiveAccess() {
        return this.status == UserAccountStatus.ACTIVE && this.emailVerifiedAt != null
                && this.profile != null && this.profile.isActive();
    }
}
