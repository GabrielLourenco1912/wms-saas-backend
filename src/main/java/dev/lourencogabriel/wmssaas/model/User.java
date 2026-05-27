package dev.lourencogabriel.wmssaas.model;

import com.github.f4b6a3.ulid.UlidCreator;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.jspecify.annotations.NullMarked;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import jakarta.persistence.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "users")
public class User implements UserDetails {

    @Id
    private String ulid;

    @NotNull
    @Column(nullable = false)
    private String name;

    @NotNull
    @Column(nullable = false)
    private String email;

    @NotNull
    @Column(nullable = false)
    private String password;

    @NotNull
    @Column(nullable = false, updatable = false)
    private LocalDateTime created_at;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime updated_at;

    public User(String name, String email, String password) {
        this.name = name;
        this.email = email;
        this.password = password;
    }

    @NullMarked
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return this.password;
    }

    @NullMarked
    @Override
    public String getUsername() {
        return this.email;
    }

//    @Override
//    public boolean isAccountNonExpired() {
//        return true;
//    }

//    @Override
//    public boolean isAccountNonLocked() {
//        return true;
//    }

//    @Override
//    public boolean isCredentialsNonExpired() {
//        return true;
//    }

//    @Override
//    public boolean isEnabled() {
//        return true;
//    }

    @PrePersist
    private void initAttributes() {
        if (this.ulid == null) {
            this.ulid = UlidCreator.getUlid().toString();
        }
        if(this.created_at == null) {
            this.created_at = LocalDateTime.now();
        }
        this.updated_at = LocalDateTime.now();
    }
}