package dev.lourencogabriel.wmssaas.operation.service;

import dev.lourencogabriel.wmssaas.operation.model.WmsOperation;
import dev.lourencogabriel.wmssaas.operation.repository.WmsOperationRepository;

import dev.lourencogabriel.wmssaas.user.repository.UserRepository;
import dev.lourencogabriel.wmssaas.web.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** The operation envelope and every physical effect commit or roll back together. */
@Service
@RequiredArgsConstructor
public class OperationService {
    private final WmsOperationRepository operations;
    private final UserRepository users;
    private final ObjectMapper mapper;

    public static String currentActorId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new org.springframework.security.access.AccessDeniedException("Authentication is required");
        }
        return authentication.getName();
    }

    @Transactional
    public <T> T execute(String key, Object request, String userId, String type,
                         Class<T> responseType, Function<String, T> work) {
        if (key == null || key.isBlank() || key.length() > 64) {
            throw new BusinessRuleException("An idempotency key with at most 64 characters is required");
        }
        // Serializes retries by the same actor, before any domain locks are acquired.
        // A global UNIQUE key still rejects a key concurrently claimed by another actor.
        users.findByIdForUpdate(userId).orElseThrow(() -> new BusinessRuleException("Actor is unavailable"));
        String hash = hash(request);
        var existing = operations.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            var operation = existing.get();
            if (!Objects.equals(hash, operation.getRequestHash()) || !Objects.equals(type, operation.getType())
                    || !Objects.equals(userId, operation.getUserId())) {
                throw new BusinessRuleException("The idempotency key belongs to a different request");
            }
            if (!"CONCLUIDA".equals(operation.getStatus())) {
                throw new BusinessRuleException("The operation is already being processed");
            }
            return mapper.readValue(operation.getResult(), responseType);
        }
        var operation = new WmsOperation();
        operation.setIdempotencyKey(key);
        operation.setType(type);
        operation.setUserId(userId);
        operation.setRequestHash(hash);
        operation.setStatus("EM_EXECUCAO");
        operation.setCreatedAt(Instant.now());
        operations.saveAndFlush(operation);
        T response = work.apply(operation.getId());
        operation.setResult(mapper.writeValueAsString(response));
        operation.setStatus("CONCLUIDA");
        operation.setCompletedAt(Instant.now());
        operations.save(operation);
        return response;
    }

    private String hash(Object request) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(mapper.writeValueAsBytes(canonical(mapper.valueToTree(request)))));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** Object property order is irrelevant; array order and every scalar value remain significant. */
    private JsonNode canonical(JsonNode node) {
        if (node.isObject()) {
            var sorted = mapper.createObjectNode();
            node.properties().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> sorted.set(entry.getKey(), canonical(entry.getValue())));
            return sorted;
        }
        if (node.isArray()) {
            var array = mapper.createArrayNode();
            node.forEach(element -> array.add(canonical(element)));
            return array;
        }
        return node;
    }
}
