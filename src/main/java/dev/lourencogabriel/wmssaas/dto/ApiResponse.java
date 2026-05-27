package dev.lourencogabriel.wmssaas.dto;

import java.time.LocalDateTime;

public record ApiResponse<T>(
        LocalDateTime timestamp,
        int status,
        String message,
        T data
) {
    public ApiResponse(T data, String message, int status) {
        this(
                LocalDateTime.now(),
                status,
                message,
                data
        );
    }

    public ApiResponse(T data, String message) {
        this(
                LocalDateTime.now(),
                200,
                message,
                data
        );
    }
}
