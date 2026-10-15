package dev.lourencogabriel.wmssaas.inbound.controller;

import dev.lourencogabriel.wmssaas.inbound.service.InboundService;
import dev.lourencogabriel.wmssaas.shared.api.PageResponse;
import dev.lourencogabriel.wmssaas.web.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;
import static dev.lourencogabriel.wmssaas.inbound.dto.InboundDtos.*;

@RestController
@RequestMapping("/api/putaway-tasks")
@RequiredArgsConstructor
public class PutawayController {
    private final InboundService service;

    @GetMapping
    public ApiResponse<PageResponse<PutawayTaskResponse>> list(@RequestParam(required=false) String warehouseId,
            @RequestParam(required=false) String status, @RequestParam(required=false) String assignedUserId, Pageable pageable) {
        return new ApiResponse<>(service.putawayTasks(warehouseId,status,assignedUserId,pageable),"Tarefas de armazenagem");
    }
    @GetMapping("/{id}")
    public ApiResponse<PutawayTaskResponse> detail(@PathVariable String id) {
        return new ApiResponse<>(service.putawayTask(id),"Tarefa de armazenagem");
    }
    @PostMapping("/{id}/start")
    public ApiResponse<PutawayTaskResponse> start(@PathVariable String id, @RequestHeader("Idempotency-Key") String key) {
        return new ApiResponse<>(service.startPutaway(id,key),"Armazenagem iniciada");
    }
    @PostMapping("/{id}/complete")
    public ApiResponse<PutawayTaskResponse> complete(@PathVariable String id, @Valid @RequestBody CompletePutawayRequest request,
                                                    @RequestHeader("Idempotency-Key") String key) {
        return new ApiResponse<>(service.completePutaway(id,request,key),"Armazenagem concluída");
    }
}
