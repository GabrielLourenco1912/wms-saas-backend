package dev.lourencogabriel.wmssaas.operation;

import dev.lourencogabriel.wmssaas.operation.model.WmsOperation;
import dev.lourencogabriel.wmssaas.operation.repository.WmsOperationRepository;
import dev.lourencogabriel.wmssaas.operation.service.OperationService;
import dev.lourencogabriel.wmssaas.user.model.User;
import dev.lourencogabriel.wmssaas.user.repository.UserRepository;
import dev.lourencogabriel.wmssaas.web.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OperationServiceTest {
    @Mock WmsOperationRepository operations;
    @Mock UserRepository users;
    final Map<String, WmsOperation> persisted = new HashMap<>();
    final AtomicInteger executions = new AtomicInteger();
    OperationService service;

    @BeforeEach
    void prepare() {
        service = new OperationService(operations, users, JsonMapper.builder().build());
        when(users.findByIdForUpdate(anyString())).thenReturn(Optional.of(new User("User", "user@example.com", "unused")));
        when(operations.findByIdempotencyKey(anyString())).thenAnswer(call -> Optional.ofNullable(persisted.get(call.getArgument(0))));
        when(operations.saveAndFlush(any(WmsOperation.class))).thenAnswer(call -> {
            WmsOperation operation = call.getArgument(0); operation.setId("operation-id");
            persisted.put(operation.getIdempotencyKey(), operation); return operation;
        });
    }

    @Test
    void shouldReplayNestedMapsRegardlessOfInsertionOrderIncludingMapsInsideArrays() {
        var first = ordered("z", List.of(ordered("b", 2, "a", 1)), "a", ordered("right", true, "left", "value"));
        var reordered = ordered("a", ordered("left", "value", "right", true), "z", List.of(ordered("a", 1, "b", 2)));
        Response initial = execute(first);
        assertEquals(initial, execute(reordered));
        assertEquals(1, executions.get());
        assertEquals(64, persisted.get("key").getRequestHash().length());
        verify(operations, times(1)).saveAndFlush(any());
    }

    @Test
    void shouldReplayEquivalentDtoPropertiesAndTreeNodesWithAFreshMapperInstance() {
        var request = new ForwardRequest("last", ordered("second", 2, "first", 1));
        Response initial = execute(request);
        assertEquals("019ecff5753977d5da4445f26bfa204bca385d455babfd13842de804d4bcc277", persisted.get("key").getRequestHash());
        service = new OperationService(operations, users, JsonMapper.builder().build());
        assertEquals(initial, execute(new ReverseRequest(ordered("first", 1, "second", 2), "last")));
        var tree = JsonMapper.builder().build().readTree("{\"z\":\"last\",\"a\":{\"second\":2,\"first\":1}}");
        assertEquals(initial, execute(tree));
        assertEquals(1, executions.get());
    }

    @Test
    void shouldRejectAnAlteredScalarOrArrayOrderInsteadOfReplayingAnotherRequest() {
        execute(Map.of("quantity", 3, "items", List.of("A", "B")));
        assertThrows(BusinessRuleException.class, () -> execute(Map.of("items", List.of("A", "B"), "quantity", 4)));
        assertThrows(BusinessRuleException.class, () -> execute(Map.of("items", List.of("B", "A"), "quantity", 3)));
        assertEquals(1, executions.get());
    }

    @Test
    void shouldNeverReplayAKeyForAnotherUserOrOperationType() {
        var request = Map.of("quantity", 3);
        execute(request);
        assertThrows(BusinessRuleException.class, () -> service.execute("key", request, "other-user", "PICKING", Response.class,
                id -> { executions.incrementAndGet(); return new Response("incorrect", 0); }));
        assertThrows(BusinessRuleException.class, () -> service.execute("key", request, "user", "RECEIPT", Response.class,
                id -> { executions.incrementAndGet(); return new Response("incorrect", 0); }));
        assertEquals(1, executions.get());
    }

    private Response execute(Object request) {
        return service.execute("key", request, "user", "PICKING", Response.class,
                id -> { executions.incrementAndGet(); return new Response("completed", 42); });
    }
    private static Map<String, Object> ordered(String firstKey, Object firstValue, String secondKey, Object secondValue) {
        var map = new LinkedHashMap<String, Object>(); map.put(firstKey, firstValue); map.put(secondKey, secondValue); return map;
    }
    record ForwardRequest(String z, Map<String, Object> a) {}
    record ReverseRequest(Map<String, Object> a, String z) {}
    record Response(String status, int value) {}
}
