package dev.lourencogabriel.wmssaas.auth.dto;

import dev.lourencogabriel.wmssaas.auth.model.AuthChallengePurpose;

import java.time.Instant;

public record AuthChallengeResponse(
        String challengeId,
        AuthChallengePurpose purpose,
        String maskedEmail,
        Instant expiresAt,
        int maxAttempts
) {
}
