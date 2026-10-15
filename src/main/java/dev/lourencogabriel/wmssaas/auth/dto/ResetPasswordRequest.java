package dev.lourencogabriel.wmssaas.auth.dto;

import dev.lourencogabriel.wmssaas.shared.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "Challenge id is required")
        @Size(max = 26, message = "Challenge id must not exceed 26 characters")
        String challengeId,

        @NotBlank(message = "Code is required")
        @Pattern(regexp = "\\d{6}", message = "Code must contain exactly 6 digits")
        String code,

        @NotBlank(message = "New password is required")
        @Size(min = 8, message = "New password must contain at least 8 characters")
        @MaxUtf8Bytes(
                value = 72,
                message = "New password must not exceed 72 bytes when UTF-8 encoded"
        )
        String newPassword
) {
}
