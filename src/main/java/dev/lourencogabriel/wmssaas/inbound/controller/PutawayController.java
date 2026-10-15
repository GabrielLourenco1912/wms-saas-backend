package dev.lourencogabriel.wmssaas.inbound.controller;

import dev.lourencogabriel.wmssaas.inbound.service.InboundService;
import dev.lourencogabriel.wmssaas.shared.api.PageResponse;
import dev.lourencogabriel.wmssaas.web.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import dev.lourencogabriel.wmssaas.shared.api.PageQuery;
import dev.lourencogabriel.wmssaas.shared.api.LookupQuery;
import dev.lourencogabriel.wmssaas.shared.api.LookupOption;
import dev.lourencogabriel.wmssaas.inbound.dto.InboundFilters;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import static dev.lourencogabriel.wmssaas.inbound.dto.InboundDtos.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PutawayController {
    private final InboundService service;

    @GetMapping({"/putaway-tasks", "/search/putaway-tasks"})
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PUTAWAY_VIEW')")
    public ApiResponse<PageResponse<PutawayTaskResponse>> list(@Valid @ModelAttribute PageQuery query,
            @ModelAttribute InboundFilters.PutawayTasks filters) {
        return new ApiResponse<>(service.putawayTasks(query, filters), "Consulta paginada");
    }

    @GetMapping("/lookups/putaway-tasks")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PUTAWAY_VIEW')")
    public ApiResponse<PageResponse<LookupOption>> lookup(@Valid @ModelAttribute LookupQuery query,
            @ModelAttribute InboundFilters.PutawayTasks filters) {
        return new ApiResponse<>(service.putawayLookups(query, filters), "Opções de seleção");
    }
    @GetMapping("/putaway-tasks/{id}")
    public ApiResponse<PutawayTaskResponse> detail(@PathVariable String id) {
        return new ApiResponse<>(service.putawayTask(id),"Tarefa de armazenagem");
    }
    @PostMapping("/putaway-tasks/{id}/start")
    public ApiResponse<PutawayTaskResponse> start(@PathVariable String id, @RequestHeader("Idempotency-Key") String key) {
        return new ApiResponse<>(service.startPutaway(id,key),"Armazenagem iniciada");
    }
    @PostMapping("/putaway-tasks/{id}/complete")
    public ApiResponse<PutawayTaskResponse> complete(@PathVariable String id, @Valid @RequestBody CompletePutawayRequest request,
                                                    @RequestHeader("Idempotency-Key") String key) {
        return new ApiResponse<>(service.completePutaway(id,request,key),"Armazenagem concluída");
    }
}
