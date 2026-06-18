package dev.lourencogabriel.wmssaas.repository;

import dev.lourencogabriel.wmssaas.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {
    RefreshTokenRepository findByTokenHash(String tokenHash);
}
