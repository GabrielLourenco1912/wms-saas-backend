package dev.lourencogabriel.wmssaas.task.controller;

import dev.lourencogabriel.wmssaas.shared.api.*;
import dev.lourencogabriel.wmssaas.stock.dto.StockSearchFilters;
import dev.lourencogabriel.wmssaas.stock.service.StockQueryService;
import dev.lourencogabriel.wmssaas.stock.dto.StockDtos.ReasonRequest;
import dev.lourencogabriel.wmssaas.stock.service.StockService;
import dev.lourencogabriel.wmssaas.task.dto.TaskDtos;
import dev.lourencogabriel.wmssaas.task.dto.TaskHistoryDtos;
import dev.lourencogabriel.wmssaas.web.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TaskController {
    private final StockService service;
    private final StockQueryService queries;
    public record Detail(TaskDtos.Response task, List<TaskHistoryDtos.Response> history) {}

    @GetMapping({"/tasks","/search/tasks"})
    @PreAuthorize("@wmsAuthorization.can(authentication, 'TASK_VIEW')")
    public ApiResponse<PageResponse<TaskDtos.Response>> list(@Valid @ModelAttribute PageQuery query,
            @Valid @ModelAttribute StockSearchFilters.Tasks filters) {
        return new ApiResponse<>(queries.tasks(query,filters),"tasks");
    }
    @GetMapping("/lookups/tasks")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'TASK_VIEW')")
    public ApiResponse<PageResponse<LookupOption>> lookup(@Valid @ModelAttribute LookupQuery query,
            @Valid @ModelAttribute StockSearchFilters.Tasks filters) {
        return new ApiResponse<>(queries.taskLookup(query,filters),"tasks options");
    }
    @GetMapping("/tasks/{id}")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'TASK_VIEW')")
    public ApiResponse<Detail> detail(@PathVariable String id) {
        var value=service.taskDetails(id);
        return new ApiResponse<>(new Detail(TaskDtos.Response.from(value.task()),value.history().stream().map(TaskHistoryDtos.Response::from).toList()),"Task history");
    }
    @GetMapping("/tasks/{id}/history")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'TASK_VIEW')")
    public ApiResponse<PageResponse<TaskHistoryDtos.Response>> history(@PathVariable String id,@Valid @ModelAttribute PageQuery query) {
        return new ApiResponse<>(queries.history(id,query),"Task status history");
    }
    @PostMapping("/tasks/{id}/cancel")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'TASK_CANCEL')")
    public ApiResponse<TaskDtos.Response> cancel(@PathVariable String id, @Valid @RequestBody ReasonRequest request,
            @RequestHeader("Idempotency-Key") String key) {
        return new ApiResponse<>(TaskDtos.Response.from(service.cancelTask(id,request,key)),"Task cancelled");
    }
}
