package dev.lourencogabriel.wmssaas.dto;

public record LoginRequest(
        String email,
        String password
) {}