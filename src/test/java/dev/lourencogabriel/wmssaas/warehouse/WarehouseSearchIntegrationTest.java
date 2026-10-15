package dev.lourencogabriel.wmssaas.warehouse;

import dev.lourencogabriel.wmssaas.auth.authorization.WmsPermission;
import dev.lourencogabriel.wmssaas.auth.service.TokenService;
import dev.lourencogabriel.wmssaas.shared.model.UserAccountStatus;
import dev.lourencogabriel.wmssaas.user.model.User;
import dev.lourencogabriel.wmssaas.user.repository.*;
import dev.lourencogabriel.wmssaas.warehouse.model.*;
import dev.lourencogabriel.wmssaas.warehouse.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.*;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:warehouse-search;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class WarehouseSearchIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired AccessProfileRepository profiles;
    @Autowired TokenService tokens;
    @Autowired WarehouseRepository warehouses;
    @Autowired ZoneRepository zones;
    @Autowired PositionRepository positions;
    String manager;
    String operator;

    @BeforeEach
    void authenticate() { manager = token("manager@warehouse-search.test", "GESTOR"); operator = token("operator@warehouse-search.test", "OPERADOR"); }

    @Test
    void shouldPaginateCollectionsAndSearchAliasesWithIdenticalStableResults() throws Exception {
        for (int i = 0; i < 27; i++) warehouse("Warehouse " + i, i != 26);
        query("/api/warehouses").andExpect(status().isOk()).andExpect(jsonPath("$.data.content", hasSize(20)))
                .andExpect(jsonPath("$.data.size").value(20)).andExpect(jsonPath("$.data.totalElements").value(27));
        String collection = query("/api/warehouses?page=1&size=10").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1)).andExpect(jsonPath("$.data.content", hasSize(10)))
                .andReturn().getResponse().getContentAsString();
        String search = query("/api/search/warehouses?page=1&size=10").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Assertions.assertEquals(mapper.readTree(collection).path("data"), mapper.readTree(search).path("data"));
        query("/api/warehouses?page=3&size=10").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(0))).andExpect(jsonPath("$.data.totalElements").value(27));
    }

    @Test
    void shouldCombineLiteralTextSearchWithWarehouseZoneAndPositionFilters() throws Exception {
        Warehouse first = warehouse("North", true); Warehouse second = warehouse("South", true);
        Zone firstZone = zone(first, "Picking area"); Zone secondZone = zone(second, "Picking area");
        Position expected = position(firstZone, "A%_!Rack"); position(firstZone, "AXXRack"); position(secondZone, "B%_!Rack");
        mvc.perform(get("/api/search/positions").header("Authorization", manager).param("q", "%_!")
                        .param("warehouseId", first.getId()).param("zoneId", firstZone.getId()).param("status", "LIVRE")
                        .param("purpose", "ARMAZENAGEM").param("type", "FIXA").param("active", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(expected.getId()));
        mvc.perform(get("/api/positions").header("Authorization", manager).param("warehouseId", second.getId()).param("zoneId", firstZone.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(get("/api/search/zones").header("Authorization", manager).param("warehouseId", first.getId()).param("q", "PICKING"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].id").value(firstZone.getId()));
        query("/api/lookups/positions?zoneId=" + firstZone.getId() + "&q=xxrack")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].label").value("AXXRack"));
        query("/api/lookups/zones?warehouseId=" + second.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(secondZone.getId()));
    }

    @Test
    void shouldLimitDropdownsToFiveActiveOptionsAndRejectInvalidPageArguments() throws Exception {
        for (int i = 0; i < 9; i++) warehouse("Warehouse " + i, i < 7);
        query("/api/lookups/warehouses").andExpect(status().isOk()).andExpect(jsonPath("$.data.size").value(5))
                .andExpect(jsonPath("$.data.totalElements").value(7)).andExpect(jsonPath("$.data.content", hasSize(5)))
                .andExpect(jsonPath("$.data.content[0].label").isString()).andExpect(jsonPath("$.data.content[0].name").doesNotExist());
        query("/api/lookups/warehouses?page=1").andExpect(status().isOk()).andExpect(jsonPath("$.data.content", hasSize(2)));
        query("/api/lookups/warehouses?active=false").andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2));
        for (String suffix : List.of("size=6", "size=0", "page=-1")) query("/api/lookups/warehouses?" + suffix).andExpect(status().isBadRequest());
        for (String suffix : List.of("size=101", "size=0", "page=-1", "size=abc", "page=2147483647&size=100", "q=" + "x".repeat(161)))
            query("/api/warehouses?" + suffix).andExpect(status().isBadRequest());
    }

    @Test
    void shouldApplyTheCollectionPermissionToSearchAndLookupControllers() throws Exception {
        mvc.perform(get("/api/search/warehouses").header("Authorization", operator)).andExpect(status().isOk());
        mvc.perform(get("/api/lookups/warehouses").header("Authorization", operator)).andExpect(status().isOk());
        var profile = profiles.findById("OPERADOR").orElseThrow(); profile.getPermissions().remove(WmsPermission.WAREHOUSE_VIEW); profiles.saveAndFlush(profile);
        mvc.perform(get("/api/search/warehouses").header("Authorization", operator)).andExpect(status().isForbidden());
        mvc.perform(get("/api/lookups/warehouses").header("Authorization", operator)).andExpect(status().isForbidden());
        mvc.perform(get("/api/lookups/warehouses")).andExpect(status().isUnauthorized());
    }

    private ResultActions query(String path) throws Exception { return mvc.perform(get(path).header("Authorization", manager)); }
    private Warehouse warehouse(String name, boolean active) { Warehouse value = new Warehouse(); value.setName(name); value.setActive(active); return warehouses.saveAndFlush(value); }
    private Zone zone(Warehouse warehouse, String name) { Zone value = new Zone(); value.setWarehouseId(warehouse.getId()); value.setName(name); return zones.saveAndFlush(value); }
    private Position position(Zone zone, String code) { Position value = new Position(); value.setZoneId(zone.getId()); value.setCode(code); return positions.saveAndFlush(value); }
    private String token(String email, String profile) {
        User value = new User(profile, email, "unused"); value.setProfile(profiles.findById(profile).orElseThrow());
        value.setStatus(UserAccountStatus.ACTIVE); value.setEmailVerifiedAt(Instant.now()); return "Bearer " + tokens.generateTokens(users.saveAndFlush(value).getUlid()).token();
    }
}
