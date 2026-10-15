package dev.lourencogabriel.wmssaas.warehouse.controller;

import dev.lourencogabriel.wmssaas.shared.api.PageResponse;
import dev.lourencogabriel.wmssaas.shared.api.PageQuery;
import dev.lourencogabriel.wmssaas.shared.api.LookupQuery;
import dev.lourencogabriel.wmssaas.shared.api.LookupOption;
import dev.lourencogabriel.wmssaas.warehouse.dto.WarehouseDtos.*;
import dev.lourencogabriel.wmssaas.warehouse.model.Position;
import dev.lourencogabriel.wmssaas.warehouse.model.Warehouse;
import dev.lourencogabriel.wmssaas.warehouse.model.Zone;
import dev.lourencogabriel.wmssaas.warehouse.service.WarehouseService;
import dev.lourencogabriel.wmssaas.web.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class WarehouseController {
    private final WarehouseService service;

    @GetMapping({"/warehouses", "/search/warehouses"})
    @PreAuthorize("@wmsAuthorization.can(authentication, 'WAREHOUSE_VIEW')")
    public ApiResponse<PageResponse<WarehouseResponse>> warehouses(@Valid @ModelAttribute PageQuery query,
            @RequestParam(required = false) Boolean active) {
        return new ApiResponse<>(PageResponse.from(service.warehouses(query, active).map(WarehouseResponse::from)), "Warehouse list");
    }

    @GetMapping("/lookups/warehouses")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'WAREHOUSE_VIEW')")
    public ApiResponse<PageResponse<LookupOption>> warehouseLookup(@Valid @ModelAttribute LookupQuery query,
            @RequestParam(defaultValue = "true") Boolean active) {
        return new ApiResponse<>(PageResponse.from(service.warehouses(query, active)
                .map(w -> new LookupOption(w.getId(), w.getName()))), "Warehouse options");
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

    @GetMapping({"/zones", "/search/zones"})
    @PreAuthorize("@wmsAuthorization.can(authentication, 'WAREHOUSE_VIEW')")
    public ApiResponse<PageResponse<ZoneResponse>> zones(@Valid @ModelAttribute PageQuery query,
            @RequestParam(required = false) String warehouseId, @RequestParam(required = false) Boolean active) {
        return new ApiResponse<>(PageResponse.from(service.zones(query, warehouseId, active).map(ZoneResponse::from)), "Zone list");
    }

    @GetMapping("/lookups/zones")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'WAREHOUSE_VIEW')")
    public ApiResponse<PageResponse<LookupOption>> zoneLookup(@Valid @ModelAttribute LookupQuery query,
            @RequestParam(required = false) String warehouseId, @RequestParam(defaultValue = "true") Boolean active) {
        return new ApiResponse<>(PageResponse.from(service.zones(query, warehouseId, active)
                .map(z -> new LookupOption(z.getId(), z.getName()))), "Zone options");
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

    @GetMapping({"/positions", "/search/positions"})
    @PreAuthorize("@wmsAuthorization.can(authentication, 'WAREHOUSE_VIEW')")
    public ApiResponse<PageResponse<PositionResponse>> positions(@Valid @ModelAttribute PageQuery query,
            @RequestParam(required = false) String zoneId, @RequestParam(required = false) String warehouseId,
            @RequestParam(required = false) String status, @RequestParam(required = false) String type,
            @RequestParam(required = false) String purpose, @RequestParam(required = false) Boolean active) {
        return new ApiResponse<>(PageResponse.from(service.positions(query, zoneId, warehouseId, status, type, purpose, active)
                .map(PositionResponse::from)), "Position list");
    }

    @GetMapping("/lookups/positions")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'WAREHOUSE_VIEW')")
    public ApiResponse<PageResponse<LookupOption>> positionLookup(@Valid @ModelAttribute LookupQuery query,
            @RequestParam(required = false) String zoneId, @RequestParam(required = false) String warehouseId,
            @RequestParam(required = false) String status, @RequestParam(required = false) String type,
            @RequestParam(required = false) String purpose, @RequestParam(defaultValue = "true") Boolean active) {
        return new ApiResponse<>(PageResponse.from(service.positions(query, zoneId, warehouseId, status, type, purpose, active)
                .map(p -> new LookupOption(p.getId(), p.getCode()))), "Position options");
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
