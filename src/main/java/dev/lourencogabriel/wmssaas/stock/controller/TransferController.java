package dev.lourencogabriel.wmssaas.stock.controller;

import dev.lourencogabriel.wmssaas.shared.api.*;
import dev.lourencogabriel.wmssaas.stock.dto.StockSearchFilters;
import dev.lourencogabriel.wmssaas.stock.service.StockQueryService;
import dev.lourencogabriel.wmssaas.stock.dto.StockDtos.*;
import dev.lourencogabriel.wmssaas.stock.service.StockService;
import dev.lourencogabriel.wmssaas.task.dto.TaskDtos;
import dev.lourencogabriel.wmssaas.web.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TransferController {
    private final StockService service;
    private final StockQueryService queries;

    @GetMapping({"/transfers","/search/transfers"})
    @PreAuthorize("@wmsAuthorization.can(authentication, 'TRANSFER_VIEW')")
    public ApiResponse<PageResponse<TaskDtos.Response>> list(@Valid @ModelAttribute PageQuery query,
            @Valid @ModelAttribute StockSearchFilters.Tasks filters) {
        return new ApiResponse<>(queries.transfers(query,filters),"transfers");
    }
    @GetMapping("/lookups/transfers")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'TRANSFER_VIEW')")
    public ApiResponse<PageResponse<LookupOption>> lookup(@Valid @ModelAttribute LookupQuery query,
            @Valid @ModelAttribute StockSearchFilters.Tasks filters) {
        return new ApiResponse<>(queries.transferLookup(query,filters),"transfers options");
    }
    @GetMapping("/transfers/{id}")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'TRANSFER_VIEW')")
    public ApiResponse<TaskDtos.Response> detail(@PathVariable String id) { return new ApiResponse<>(TaskDtos.Response.from(service.transfer(id)),"Transfer"); }

    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@wmsAuthorization.can(authentication, 'TRANSFER_EXECUTE')")
    public ApiResponse<TaskDtos.Response> create(@Valid @RequestBody TransferRequest request, @RequestHeader("Idempotency-Key") String key) {
        return new ApiResponse<>(TaskDtos.Response.from(service.createTransfer(request,key)),"Transfer planned",HttpStatus.CREATED);
    }
    @PostMapping("/transfers/{id}/confirm")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'TRANSFER_EXECUTE')")
    public ApiResponse<TaskDtos.Response> confirm(@PathVariable String id, @Valid @RequestBody ConfirmTransferRequest request,
            @RequestHeader("Idempotency-Key") String key) {
        return new ApiResponse<>(TaskDtos.Response.from(service.confirmTransfer(id,request,key)),"Transfer confirmed");
    }
}
