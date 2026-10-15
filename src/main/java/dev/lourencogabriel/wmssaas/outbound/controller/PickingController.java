package dev.lourencogabriel.wmssaas.outbound.controller;

import dev.lourencogabriel.wmssaas.operation.service.OperationService;
import dev.lourencogabriel.wmssaas.outbound.service.PickingService;
import dev.lourencogabriel.wmssaas.shared.api.PageResponse;
import dev.lourencogabriel.wmssaas.web.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import dev.lourencogabriel.wmssaas.shared.api.PageQuery;
import dev.lourencogabriel.wmssaas.shared.api.LookupQuery;
import dev.lourencogabriel.wmssaas.shared.api.LookupOption;
import dev.lourencogabriel.wmssaas.outbound.dto.OutboundFilters;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import static dev.lourencogabriel.wmssaas.outbound.dto.OutboundDtos.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PickingController {
    private final PickingService service;

    @GetMapping({"/picking-tasks", "/search/picking-tasks"})
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PICKING_VIEW')")
    public ApiResponse<PageResponse<TaskResponse>> list(@Valid @ModelAttribute PageQuery query,
            @ModelAttribute OutboundFilters.PickingTasks filters) {
        return new ApiResponse<>(service.tasks(query, filters), "Consulta paginada");
    }

    @GetMapping("/lookups/picking-tasks")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PICKING_VIEW')")
    public ApiResponse<PageResponse<LookupOption>> lookup(@Valid @ModelAttribute LookupQuery query,
            @ModelAttribute OutboundFilters.PickingTasks filters) {
        return new ApiResponse<>(service.taskLookups(query, filters), "Opções de seleção");
    }

    @GetMapping("/picking-tasks/{id}")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PICKING_VIEW')")
    public ApiResponse<TaskResponse> detail(@PathVariable String id) {
        return new ApiResponse<>(service.task(id), "Tarefa de picking");
    }

    @PostMapping("/picking-tasks/{id}/start")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PICKING_EXECUTE')")
    public ApiResponse<TaskResponse> start(@PathVariable String id, @Valid @RequestBody StartRequest request) {
        return new ApiResponse<>(service.start(id, request, OperationService.currentActorId()), "Picking iniciado");
    }

    @PostMapping("/picking-tasks/{id}/complete")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PICKING_EXECUTE')")
    public ApiResponse<TaskResponse> complete(@PathVariable String id, @Valid @RequestBody PickingRequest request) {
        return new ApiResponse<>(service.complete(id, request, OperationService.currentActorId()), "Picking concluído");
    }

    @PostMapping("/picking-tasks/{id}/divergences")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PICKING_REPORT_DIVERGENCE')")
    public ApiResponse<ShortageResponse> shortage(@PathVariable String id, @Valid @RequestBody ShortageRequest request) {
        return new ApiResponse<>(service.shortage(id, request, OperationService.currentActorId()), "Falta registrada e realocação processada");
    }

    @PostMapping("/tasks/{id}/authorize-excess")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'TASK_QUANTITY_OVERRIDE')")
    public ApiResponse<TaskResponse> authorizeExcess(@PathVariable String id, @Valid @RequestBody ExcessRequest request) {
        return new ApiResponse<>(service.authorizeExcess(id, request, OperationService.currentActorId()), "Excesso autorizado");
    }
}
