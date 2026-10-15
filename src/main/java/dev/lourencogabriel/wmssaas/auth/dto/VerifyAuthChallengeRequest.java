package dev.lourencogabriel.wmssaas.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VerifyAuthChallengeRequest(
        @NotBlank
        @Size(max = 26)
        String challengeId,
        @NotBlank
        @Pattern(regexp = "\\d{6}", message = "Code must contain exactly 6 digits")
        String code
) {
}
