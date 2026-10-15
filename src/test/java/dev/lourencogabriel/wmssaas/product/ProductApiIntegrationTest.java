package dev.lourencogabriel.wmssaas.product;

import dev.lourencogabriel.wmssaas.auth.service.TokenService;
import dev.lourencogabriel.wmssaas.product.repository.ProductPackagingRepository;
import dev.lourencogabriel.wmssaas.product.repository.ProductRepository;
import dev.lourencogabriel.wmssaas.shared.model.UserAccountStatus;
import dev.lourencogabriel.wmssaas.stock.model.Stock;
import dev.lourencogabriel.wmssaas.user.model.User;
import dev.lourencogabriel.wmssaas.user.repository.AccessProfileRepository;
import dev.lourencogabriel.wmssaas.user.repository.UserRepository;
import dev.lourencogabriel.wmssaas.warehouse.model.Position;
import dev.lourencogabriel.wmssaas.warehouse.model.Warehouse;
import dev.lourencogabriel.wmssaas.warehouse.model.Zone;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercises real controllers, validation, authorization and persisted catalog data. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:wms-product-api;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class ProductApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired TokenService tokens;
    @Autowired UserRepository users;
    @Autowired AccessProfileRepository profiles;
    @Autowired PasswordEncoder passwords;
    @Autowired ProductRepository products;
    @Autowired ProductPackagingRepository packages;
    @Autowired EntityManager entityManager;
    String bearer;

    @BeforeEach
    void authenticateManager() {
        var actor = new User("Catalog manager", "catalog.manager@example.com", passwords.encode("catalog-password-123"));
        actor.setProfile(profiles.findById("GESTOR").orElseThrow());
        actor.setStatus(UserAccountStatus.ACTIVE);
        actor.setEmailVerifiedAt(Instant.now());
        users.saveAndFlush(actor);
        bearer = "Bearer " + tokens.generateTokens(actor.getUlid()).token();
    }

    @Test
    void shouldCreateReadListAndEditTheCompleteCatalogWithRealCreatedStatus() throws Exception {
        String categoryId = createCategory("Beverages", true);
        putJson("/api/categories/" + categoryId, Map.of("name", "Beverages revised", "description", "Drinks", "active", true))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("Beverages revised"));
        getJson("/api/categories/" + categoryId).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(categoryId));
        getJson("/api/categories?page=0&size=10").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        var body = productBody(categoryId, "SKU-DRINK");
        String productId = createdId(postJson("/api/products", body));
        getJson("/api/products/" + productId).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sku").value("SKU-DRINK"))
                .andExpect(jsonPath("$.data.baseUnit").value("UN"));
        getJson("/api/products?page=0&size=10").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(productId));
        body.put("name", "Drink revised");
        putJson("/api/products/" + productId, body).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Drink revised"));

        var packaging = packagingBody("GTIN-DRINK", "12");
        String packageId = createdId(postJson("/api/products/" + productId + "/packages", packaging));
        getJson("/api/products/" + productId + "/packages").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(packageId))
                .andExpect(jsonPath("$.data.content[0].conversionFactor").value(12));
        packaging.put("barcode", "GTIN-DRINK-REVISED");
        putJson("/api/products/" + productId + "/packages/" + packageId, packaging)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.barcode").value("GTIN-DRINK-REVISED"));
        assertEquals(productId, packages.findById(packageId).orElseThrow().getProductId());
    }

    @Test
    void shouldRejectDuplicateSkuIgnoringCaseWhileAllowingTheSameProductToKeepItsSku() throws Exception {
        String category = createCategory("Products", true);
        String first = createProduct(category, "SKU-FIRST");
        String second = createProduct(category, "SKU-SECOND");
        putJson("/api/products/" + first, productBody(category, "SKU-FIRST"))
                .andExpect(status().isOk());
        postJson("/api/products", productBody(category, "sku-first"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.message").value("SKU is already used"));
        putJson("/api/products/" + second, productBody(category, "sku-first"))
                .andExpect(status().isUnprocessableContent());
        assertEquals(2, products.count());
        assertEquals("SKU-SECOND", products.findById(second).orElseThrow().getSku());
    }

    @Test
    void shouldRejectDuplicateBarcodesIgnoringCaseAndValidateThePackagingOwner() throws Exception {
        String category = createCategory("Products", true);
        String first = createProduct(category, "SKU-FIRST");
        String second = createProduct(category, "SKU-SECOND");
        String packageId = createdId(postJson("/api/products/" + first + "/packages", packagingBody("BARCODE-1", "6")));
        putJson("/api/products/" + first + "/packages/" + packageId, packagingBody("BARCODE-1", "6"))
                .andExpect(status().isOk());
        postJson("/api/products/" + second + "/packages", packagingBody("barcode-1", "6"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.message").value("Barcode is already used"));
        putJson("/api/products/" + second + "/packages/" + packageId, packagingBody("OTHER-BARCODE", "6"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("Packaging does not belong to this product"));
        assertEquals(1, packages.count());
        assertEquals(first, packages.findById(packageId).orElseThrow().getProductId());
    }

    @Test
    void shouldPreventSkuAndBarcodeCollisionsAcrossDifferentProducts() throws Exception {
        String category = createCategory("Products", true);
        String first = createProduct(category, "SKU-FIRST");
        String second = createProduct(category, "SKU-SECOND");
        createdId(postJson("/api/products/" + first + "/packages", packagingBody("BARCODE-FIRST", "6")));
        // A product may use its own SKU on an individual package; another product may not claim it.
        createdId(postJson("/api/products/" + first + "/packages", packagingBody("SKU-FIRST", "1")));
        postJson("/api/products", productBody(category, "barcode-first"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("SKU conflicts with another product barcode"));
        postJson("/api/products/" + first + "/packages", packagingBody("sku-second", "1"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("Barcode conflicts with another SKU"));
        assertEquals(2, products.count());
    }

    @Test
    void shouldRejectZeroConversionAndChangesToAnExistingConversionFactor() throws Exception {
        String category = createCategory("Products", true);
        String product = createProduct(category, "SKU-BOX");
        String packageId = createdId(postJson("/api/products/" + product + "/packages", packagingBody("BARCODE-BOX", "12")));
        postJson("/api/products/" + product + "/packages", packagingBody("BARCODE-ZERO", "0"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.data[0].field").value("conversionFactor"));
        putJson("/api/products/" + product + "/packages/" + packageId, packagingBody("BARCODE-BOX", "24"))
                .andExpect(status().isUnprocessableContent());
        assertEquals(0, new BigDecimal("12").compareTo(packages.findById(packageId).orElseThrow().getConversionFactor()));
        assertEquals(1, packages.count());
    }

    @Test
    void shouldKeepStockReferencedUnitsPhysicalParametersAndTraceabilityStable() throws Exception {
        String category = createCategory("Products", true);
        String product = createProduct(category, "SKU-STOCK");
        createPhysicalStock(product);
        var allowed = productBody(category, "SKU-STOCK");
        allowed.put("name", "Name may change");
        putJson("/api/products/" + product, allowed).andExpect(status().isOk());
        var changes = Map.<String, Object>of("baseUnit", "KG", "unitWeightKg", new BigDecimal("99"),
                "unitVolumeM3", new BigDecimal("9"), "lotControlled", true, "expiryControlled", true);
        for (var change : changes.entrySet()) {
            var body = productBody(category, "SKU-STOCK");
            body.put(change.getKey(), change.getValue());
            putJson("/api/products/" + product, body).andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.message").value("Referenced product unit, physical parameters and traceability cannot change"));
        }
        var persisted = products.findById(product).orElseThrow();
        assertEquals("UN", persisted.getBaseUnit());
        assertEquals(0, new BigDecimal("1.5").compareTo(persisted.getUnitWeightKg()));
        assertFalse(persisted.getLotControlled());
        assertFalse(persisted.getExpiryControlled());
    }

    @Test
    void shouldRejectInactiveParentsAndReturnNotFoundForMissingCatalogResources() throws Exception {
        String category = createCategory("Inactive category", false);
        String activeCategory = createCategory("Active category", true);
        var inactiveProductBody = productBody(activeCategory, "SKU-INACTIVE");
        inactiveProductBody.put("active", false);
        String product = createdId(postJson("/api/products", inactiveProductBody));
        getJson("/api/products/00000000000000000000000000").andExpect(status().isNotFound());
        getJson("/api/categories/00000000000000000000000000").andExpect(status().isNotFound());
        getJson("/api/products/00000000000000000000000000/packages").andExpect(status().isNotFound());
        postJson("/api/products", productBody(category, "SKU-REJECTED"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.message").value("Category must be active"));
        postJson("/api/products/" + product + "/packages", packagingBody("BARCODE-REJECTED", "1"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.message").value("Product must be active"));
    }

    private String createCategory(String name, boolean active) throws Exception {
        return createdId(postJson("/api/categories", Map.of("name", name, "description", "Catalog test", "active", active)));
    }

    private String createProduct(String categoryId, String sku) throws Exception {
        return createdId(postJson("/api/products", productBody(categoryId, sku)));
    }

    private Map<String, Object> productBody(String categoryId, String sku) {
        var body = new LinkedHashMap<String, Object>();
        body.put("categoryId", categoryId);
        body.put("sku", sku);
        body.put("name", "Catalog product");
        body.put("lotControlled", false);
        body.put("expiryControlled", false);
        body.put("shelfLifeDays", 0);
        body.put("baseUnit", "UN");
        body.put("unitWeightKg", new BigDecimal("1.5"));
        body.put("unitVolumeM3", new BigDecimal("0.01"));
        body.put("active", true);
        return body;
    }

    private Map<String, Object> packagingBody(String barcode, String conversion) {
        return new LinkedHashMap<>(Map.of("type", "CAIXA", "conversionFactor", new BigDecimal(conversion),
                "barcode", barcode, "weightKg", new BigDecimal("18"), "volumeM3", new BigDecimal("0.12"), "active", true));
    }

    private ResultActions getJson(String path) throws Exception {
        return mvc.perform(get(path).header("Authorization", bearer));
    }

    private ResultActions postJson(String path, Object body) throws Exception {
        return mvc.perform(post(path).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)));
    }

    private ResultActions putJson(String path, Object body) throws Exception {
        return mvc.perform(put(path).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)));
    }

    private String createdId(ResultActions response) throws Exception {
        var result = response.andExpect(status().isCreated()).andExpect(jsonPath("$.data.id").isString()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asText();
    }

    private void createPhysicalStock(String productId) {
        var warehouse = new Warehouse();
        warehouse.setName("Catalog stock warehouse");
        entityManager.persist(warehouse);
        var zone = new Zone();
        zone.setWarehouseId(warehouse.getId());
        zone.setName("Stock zone");
        entityManager.persist(zone);
        var position = new Position();
        position.setZoneId(zone.getId());
        position.setCode("PRODUCT-STOCK-POSITION");
        entityManager.persist(position);
        var stock = new Stock();
        stock.setProductId(productId);
        stock.setPositionId(position.getId());
        stock.setQuantity(BigDecimal.TEN);
        stock.setEntryAt(Instant.now());
        entityManager.persist(stock);
        entityManager.flush();
    }
}
