package dev.lourencogabriel.wmssaas.exception;

import dev.lourencogabriel.wmssaas.dto.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.UnexpectedTypeException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidation(MethodArgumentNotValidException ex) {

        var errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(err -> Map.of(
                        "field", err.getField(),
                        "message", err.getDefaultMessage()
                ))
                .toList();

        return buildResponse(HttpStatus.BAD_REQUEST, "Validation Error", errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleJsonError(HttpMessageNotReadableException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Request body is invalid");
    }

    @ExceptionHandler(UnexpectedTypeException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidationConfigError(UnexpectedTypeException ex) {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid validation annotation usage", ex.getMessage());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraint(ConstraintViolationException ex) {

        var errors = ex.getConstraintViolations()
                .stream()
                .map(err -> Map.of(
                        "field", err.getPropertyPath().toString(),
                        "message", err.getMessage()
                ))
                .toList();

        return buildResponse(HttpStatus.BAD_REQUEST, "Constraint Violation", errors);
    }

    @ExceptionHandler(EmailAlreadyInUseException.class)
    public ResponseEntity<ApiResponse<Object>> handleEmailAlreadyInUse(EmailAlreadyInUseException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        return buildResponse(HttpStatus.CONFLICT, "Request violates a database constraint");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGeneric(Exception ex) {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<ApiResponse<Object>> handleGeneric(Throwable ex) {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
    }

    private ResponseEntity<ApiResponse<Object>> buildResponse(HttpStatus status, String message) {
        return buildResponse(status, message, null);
    }

    private ResponseEntity<ApiResponse<Object>> buildResponse(HttpStatus status, String message, Object data) {
        return ResponseEntity.status(status).body(new ApiResponse<>(data, message, status));
    }
}
