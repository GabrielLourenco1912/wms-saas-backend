package dev.lourencogabriel.wmssaas.dto;

import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

public record ApiResponse<T>(
        LocalDateTime timestamp,
        HttpStatus status,
        String message,
        T data
) {
    public ApiResponse(T data, String message, HttpStatus status) {
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
                HttpStatus.OK,
                message,
                data
        );
    }
}
