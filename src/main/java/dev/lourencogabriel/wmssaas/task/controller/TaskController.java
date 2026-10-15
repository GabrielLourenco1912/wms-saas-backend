package dev.lourencogabriel.wmssaas.task.controller;

import dev.lourencogabriel.wmssaas.shared.api.PageResponse;
import dev.lourencogabriel.wmssaas.stock.dto.StockDtos.ReasonRequest;
import dev.lourencogabriel.wmssaas.stock.service.StockService;
import dev.lourencogabriel.wmssaas.task.dto.TaskDtos;
import dev.lourencogabriel.wmssaas.task.dto.TaskHistoryDtos;
import dev.lourencogabriel.wmssaas.web.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {
    private final StockService service;
    public record Detail(TaskDtos.Response task, List<TaskHistoryDtos.Response> history) {}

    @GetMapping
    @PreAuthorize("@wmsAuthorization.can(authentication, 'TASK_VIEW')")
    public ApiResponse<PageResponse<TaskDtos.Response>> list(@RequestParam(required=false) String type,
            @RequestParam(required=false) String status, @RequestParam(required=false) String assignedUserId, Pageable pageable) {
        return new ApiResponse<>(service.tasks(type,status,assignedUserId,pageable).map(TaskDtos.Response::from),"Tasks");
    }
    @GetMapping("/{id}")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'TASK_VIEW')")
    public ApiResponse<Detail> detail(@PathVariable String id) {
        var value=service.taskDetails(id);
        return new ApiResponse<>(new Detail(TaskDtos.Response.from(value.task()),value.history().stream().map(TaskHistoryDtos.Response::from).toList()),"Task history");
    }
    @PostMapping("/{id}/cancel")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'TASK_CANCEL')")
    public ApiResponse<TaskDtos.Response> cancel(@PathVariable String id, @Valid @RequestBody ReasonRequest request,
            @RequestHeader("Idempotency-Key") String key) {
        return new ApiResponse<>(TaskDtos.Response.from(service.cancelTask(id,request,key)),"Task cancelled");
    }
}
