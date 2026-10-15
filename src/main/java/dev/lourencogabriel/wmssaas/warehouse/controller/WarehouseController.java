package dev.lourencogabriel.wmssaas.warehouse.controller;

import dev.lourencogabriel.wmssaas.warehouse.model.*;
import dev.lourencogabriel.wmssaas.warehouse.dto.WarehouseDtos;
import dev.lourencogabriel.wmssaas.warehouse.service.WarehouseService;
import dev.lourencogabriel.wmssaas.web.dto.ApiResponse;
import dev.lourencogabriel.wmssaas.shared.api.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class WarehouseController {
    private final WarehouseService service;

    @GetMapping("/warehouses")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'WAREHOUSE_VIEW')")
    public ApiResponse<?> warehouses(Pageable p) { return new ApiResponse<>(PageResponse.from(service.warehouses(p).map(WarehouseDtos.WarehouseResponse::from)),"Warehouse list"); }

    @GetMapping("/warehouses/{id}")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'WAREHOUSE_VIEW')")
    public ApiResponse<?> getWarehouse(@PathVariable String id) { return new ApiResponse<>(WarehouseDtos.WarehouseResponse.from(service.get(Warehouse.class,id)),"Warehouse"); }

    @PostMapping("/warehouses")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'WAREHOUSE_CREATE')")
    public ApiResponse<?> createWarehouse(@Valid @RequestBody WarehouseDtos.WarehouseRequest body) { return new ApiResponse<>(WarehouseDtos.WarehouseResponse.from(service.warehouse(null,body)),"Warehouse created",HttpStatus.CREATED); }

    @PutMapping("/warehouses/{id}")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'WAREHOUSE_EDIT')")
    public ApiResponse<?> updateWarehouse(@PathVariable String id,@Valid @RequestBody WarehouseDtos.WarehouseRequest body) { return new ApiResponse<>(WarehouseDtos.WarehouseResponse.from(service.warehouse(id,body)),"Warehouse updated"); }

    @GetMapping("/zones")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'WAREHOUSE_VIEW')")
    public ApiResponse<?> zones(@RequestParam(required=false) String warehouseId, Pageable p) { return new ApiResponse<>(PageResponse.from(service.zones(warehouseId,p).map(WarehouseDtos.ZoneResponse::from)),"Zone list"); }

    @GetMapping("/zones/{id}")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'WAREHOUSE_VIEW')")
    public ApiResponse<?> getZone(@PathVariable String id) { return new ApiResponse<>(WarehouseDtos.ZoneResponse.from(service.get(Zone.class,id)),"Zone"); }

    @PostMapping("/zones")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'WAREHOUSE_CREATE')")
    public ApiResponse<?> createZone(@Valid @RequestBody WarehouseDtos.ZoneRequest body) { return new ApiResponse<>(WarehouseDtos.ZoneResponse.from(service.zone(null,body)),"Zone created",HttpStatus.CREATED); }

    @PutMapping("/zones/{id}")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'WAREHOUSE_EDIT')")
    public ApiResponse<?> updateZone(@PathVariable String id,@Valid @RequestBody WarehouseDtos.ZoneRequest body) { return new ApiResponse<>(WarehouseDtos.ZoneResponse.from(service.zone(id,body)),"Zone updated"); }

    @GetMapping("/positions")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'WAREHOUSE_VIEW')")
    public ApiResponse<?> positions(@RequestParam(required=false) String zoneId, Pageable p) { return new ApiResponse<>(PageResponse.from(service.positions(zoneId,p).map(WarehouseDtos.PositionResponse::from)),"Position list"); }

    @GetMapping("/positions/{id}")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'WAREHOUSE_VIEW')")
    public ApiResponse<?> getPosition(@PathVariable String id) { return new ApiResponse<>(WarehouseDtos.PositionResponse.from(service.get(Position.class,id)),"Position"); }

    @PostMapping("/positions")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'WAREHOUSE_CREATE')")
    public ApiResponse<?> createPosition(@Valid @RequestBody WarehouseDtos.PositionRequest body) { return new ApiResponse<>(WarehouseDtos.PositionResponse.from(service.position(null,body)),"Position created",HttpStatus.CREATED); }

    @PutMapping("/positions/{id}")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'WAREHOUSE_EDIT')")
    public ApiResponse<?> updatePosition(@PathVariable String id,@Valid @RequestBody WarehouseDtos.PositionRequest body) { return new ApiResponse<>(WarehouseDtos.PositionResponse.from(service.position(id,body)),"Position updated"); }
}
