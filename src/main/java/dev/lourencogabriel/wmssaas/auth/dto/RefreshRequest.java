package dev.lourencogabriel.wmssaas.auth.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RefreshRequest(
        @Size(max = 43, message = "Refresh token must not exceed 43 characters")
        @Pattern(
                regexp = "^(?:\\s*|[A-Za-z0-9_-]{43})$",
                message = "Refresh token has an invalid format"
        )
        String refreshToken
) {
}
