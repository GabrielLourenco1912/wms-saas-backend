package dev.lourencogabriel.wmssaas.warehouse.controller;

import dev.lourencogabriel.wmssaas.shared.api.PageResponse;
import dev.lourencogabriel.wmssaas.warehouse.dto.WarehouseDtos.*;
import dev.lourencogabriel.wmssaas.warehouse.model.Position;
import dev.lourencogabriel.wmssaas.warehouse.model.Warehouse;
import dev.lourencogabriel.wmssaas.warehouse.model.Zone;
import dev.lourencogabriel.wmssaas.warehouse.service.WarehouseService;
import dev.lourencogabriel.wmssaas.web.dto.ApiResponse;
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
    public ApiResponse<PageResponse<WarehouseResponse>> warehouses(Pageable pageable) {
        return new ApiResponse<>(PageResponse.from(service.warehouses(pageable).map(WarehouseResponse::from)), "Warehouse list");
    }

    @GetMapping("/warehouses/{id}")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'WAREHOUSE_VIEW')")
    public ApiResponse<WarehouseResponse> getWarehouse(@PathVariable String id) {
        return new ApiResponse<>(WarehouseResponse.from(service.get(Warehouse.class, id)), "Warehouse");
    }

    @PostMapping("/warehouses")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@wmsAuthorization.can(authentication, 'WAREHOUSE_CREATE')")
    public ApiResponse<WarehouseResponse> createWarehouse(@Valid @RequestBody WarehouseRequest body) {
        return new ApiResponse<>(WarehouseResponse.from(service.warehouse(null, body)), "Warehouse created", HttpStatus.CREATED);
    }

    @PutMapping("/warehouses/{id}")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'WAREHOUSE_EDIT')")
    public ApiResponse<WarehouseResponse> updateWarehouse(@PathVariable String id, @Valid @RequestBody WarehouseRequest body) {
        return new ApiResponse<>(WarehouseResponse.from(service.warehouse(id, body)), "Warehouse updated");
    }

    @GetMapping("/zones")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'WAREHOUSE_VIEW')")
    public ApiResponse<PageResponse<ZoneResponse>> zones(@RequestParam(required = false) String warehouseId, Pageable pageable) {
        return new ApiResponse<>(PageResponse.from(service.zones(warehouseId, pageable).map(ZoneResponse::from)), "Zone list");
    }

    @GetMapping("/zones/{id}")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'WAREHOUSE_VIEW')")
    public ApiResponse<ZoneResponse> getZone(@PathVariable String id) {
        return new ApiResponse<>(ZoneResponse.from(service.get(Zone.class, id)), "Zone");
    }

    @PostMapping("/zones")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@wmsAuthorization.can(authentication, 'WAREHOUSE_CREATE')")
    public ApiResponse<ZoneResponse> createZone(@Valid @RequestBody ZoneRequest body) {
        return new ApiResponse<>(ZoneResponse.from(service.zone(null, body)), "Zone created", HttpStatus.CREATED);
    }

    @PutMapping("/zones/{id}")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'WAREHOUSE_EDIT')")
    public ApiResponse<ZoneResponse> updateZone(@PathVariable String id, @Valid @RequestBody ZoneRequest body) {
        return new ApiResponse<>(ZoneResponse.from(service.zone(id, body)), "Zone updated");
    }

    @GetMapping("/positions")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'WAREHOUSE_VIEW')")
    public ApiResponse<PageResponse<PositionResponse>> positions(@RequestParam(required = false) String zoneId, Pageable pageable) {
        return new ApiResponse<>(PageResponse.from(service.positions(zoneId, pageable).map(PositionResponse::from)), "Position list");
    }

    @GetMapping("/positions/{id}")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'WAREHOUSE_VIEW')")
    public ApiResponse<PositionResponse> getPosition(@PathVariable String id) {
        return new ApiResponse<>(PositionResponse.from(service.get(Position.class, id)), "Position");
    }

    @PostMapping("/positions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@wmsAuthorization.can(authentication, 'WAREHOUSE_CREATE')")
    public ApiResponse<PositionResponse> createPosition(@Valid @RequestBody PositionRequest body) {
        return new ApiResponse<>(PositionResponse.from(service.position(null, body)), "Position created", HttpStatus.CREATED);
    }

    @PutMapping("/positions/{id}")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'WAREHOUSE_EDIT')")
    public ApiResponse<PositionResponse> updatePosition(@PathVariable String id, @Valid @RequestBody PositionRequest body) {
        return new ApiResponse<>(PositionResponse.from(service.position(id, body)), "Position updated");
    }
}
