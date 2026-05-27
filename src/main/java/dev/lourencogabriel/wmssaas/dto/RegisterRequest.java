package dev.lourencogabriel.wmssaas.dto;

public record RegisterRequest(
        String name,
        String email,
        String password
) {}
