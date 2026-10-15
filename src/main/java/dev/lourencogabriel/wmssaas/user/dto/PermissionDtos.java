package dev.lourencogabriel.wmssaas.user.dto;

public final class PermissionDtos {
    private PermissionDtos() {}

    public record Response(String code, String functionalRequirement) {}
}
