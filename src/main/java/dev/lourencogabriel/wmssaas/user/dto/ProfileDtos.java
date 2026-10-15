package dev.lourencogabriel.wmssaas.user.dto;

import dev.lourencogabriel.wmssaas.auth.authorization.WmsPermission;
import dev.lourencogabriel.wmssaas.user.model.AccessProfile;
import jakarta.validation.constraints.*;

import java.util.Set;

public final class ProfileDtos {
    private ProfileDtos() {}

    public record UpdateRequest(
            @NotBlank @Size(max = 100) String name,
            @NotNull Boolean active,
            @NotNull Set<@NotNull WmsPermission> permissions
    ) {}

    public record Response(String code, String name, boolean active, Set<WmsPermission> permissions) {
        public static Response from(AccessProfile profile) {
            return new Response(profile.getCode(), profile.getName(), profile.isActive(),
                    Set.copyOf(profile.getPermissions()));
        }
    }
}
