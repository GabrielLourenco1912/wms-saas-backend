package dev.lourencogabriel.wmssaas.auth.http;

import dev.lourencogabriel.wmssaas.web.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;
    private final BearerTokenAuthenticationEntryPoint bearerTokenEntryPoint;

    public ApiAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.bearerTokenEntryPoint = new BearerTokenAuthenticationEntryPoint();
    }

    @Override
    public void commence(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull AuthenticationException authException
    ) throws IOException {
        this.bearerTokenEntryPoint.commence(request, response, authException);

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        HttpStatus status = HttpStatus.valueOf(response.getStatus());
        String message = resolveMessage(authException);
        ApiResponse<Void> body = new ApiResponse<>(null, message, status);

        this.objectMapper.writeValue(response.getOutputStream(), body);
    }

    private String resolveMessage(AuthenticationException authException) {
        if (authException instanceof InvalidBearerTokenException) {
            return "Token expirado ou inválido.";
        }

        if (authException instanceof OAuth2AuthenticationException) {
            return "Token Bearer ausente ou malformado.";
        }

        return "Autenticação necessária.";
    }
}
