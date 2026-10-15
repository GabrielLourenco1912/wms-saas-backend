package dev.lourencogabriel.wmssaas.user.dto;

import dev.lourencogabriel.wmssaas.shared.model.UserAccountStatus;
import jakarta.validation.constraints.*;

public final class UserDtos {
    private UserDtos() {}

    public record UpdateRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Pattern(regexp = "GESTOR|OPERADOR") String profileCode,
            @NotNull UserAccountStatus status
    ) {}
}
