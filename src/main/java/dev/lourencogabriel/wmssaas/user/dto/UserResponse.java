package dev.lourencogabriel.wmssaas.user.dto;

import dev.lourencogabriel.wmssaas.auth.authorization.WmsPermission;
import dev.lourencogabriel.wmssaas.shared.model.UserAccountStatus;
import dev.lourencogabriel.wmssaas.user.model.User;

import java.time.Instant;
import java.util.Set;

public record UserResponse(String id, String name, String email, UserAccountStatus status,
                           String profileCode, boolean profileActive, Set<WmsPermission> permissions,
                           Instant emailVerifiedAt, Instant lockedUntil, Instant lastLoginAt,
                           Instant createdAt, Instant updatedAt) {
    public static UserResponse from(User user) {
        var profile = user.getProfile();
        return new UserResponse(user.getUlid(), user.getName(), user.getEmail(), user.getStatus(),
                profile.getCode(), profile.isActive(),
                user.hasActiveAccess() ? Set.copyOf(profile.getPermissions()) : Set.of(),
                user.getEmailVerifiedAt(), user.getLockedUntil(), user.getLastLoginAt(),
                user.getCreatedAt(), user.getUpdatedAt());
    }
}
