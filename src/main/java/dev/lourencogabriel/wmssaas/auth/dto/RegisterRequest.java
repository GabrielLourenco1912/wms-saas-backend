package dev.lourencogabriel.wmssaas.auth.dto;

import dev.lourencogabriel.wmssaas.shared.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.*;

public record RegisterRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must not exceed 100 characters")
        String name,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 254, message = "Email must not exceed 254 characters")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must contain at least 8 characters")
        @MaxUtf8Bytes(value = 72, message = "Password must not exceed 72 bytes when UTF-8 encoded")
        String password,

        @NotBlank(message = "Profile is required")
        @Pattern(regexp = "GESTOR|OPERADOR", message = "Profile must be GESTOR or OPERADOR")
        String profileCode
) {}
