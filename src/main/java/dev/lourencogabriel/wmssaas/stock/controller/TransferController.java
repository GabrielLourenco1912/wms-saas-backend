package dev.lourencogabriel.wmssaas.stock.controller;

import dev.lourencogabriel.wmssaas.shared.api.PageResponse;
import dev.lourencogabriel.wmssaas.stock.dto.StockDtos.*;
import dev.lourencogabriel.wmssaas.stock.service.StockService;
import dev.lourencogabriel.wmssaas.task.dto.TaskDtos;
import dev.lourencogabriel.wmssaas.web.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
public class TransferController {
    private final StockService service;

    @GetMapping
    @PreAuthorize("@wmsAuthorization.can(authentication, 'TRANSFER_VIEW')")
    public ApiResponse<PageResponse<TaskDtos.Response>> list(Pageable pageable) {
        return new ApiResponse<>(service.transfers(pageable).map(TaskDtos.Response::from),"Transfers");
    }
    @GetMapping("/{id}")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'TRANSFER_VIEW')")
    public ApiResponse<TaskDtos.Response> detail(@PathVariable String id) { return new ApiResponse<>(TaskDtos.Response.from(service.transfer(id)),"Transfer"); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@wmsAuthorization.can(authentication, 'TRANSFER_EXECUTE')")
    public ApiResponse<TaskDtos.Response> create(@Valid @RequestBody TransferRequest request, @RequestHeader("Idempotency-Key") String key) {
        return new ApiResponse<>(TaskDtos.Response.from(service.createTransfer(request,key)),"Transfer planned",HttpStatus.CREATED);
    }
    @PostMapping("/{id}/confirm")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'TRANSFER_EXECUTE')")
    public ApiResponse<TaskDtos.Response> confirm(@PathVariable String id, @Valid @RequestBody ConfirmTransferRequest request,
            @RequestHeader("Idempotency-Key") String key) {
        return new ApiResponse<>(TaskDtos.Response.from(service.confirmTransfer(id,request,key)),"Transfer confirmed");
    }
}
