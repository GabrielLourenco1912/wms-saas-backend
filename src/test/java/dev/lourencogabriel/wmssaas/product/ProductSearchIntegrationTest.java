package dev.lourencogabriel.wmssaas.product;

import dev.lourencogabriel.wmssaas.auth.authorization.WmsPermission;
import dev.lourencogabriel.wmssaas.auth.service.TokenService;
import dev.lourencogabriel.wmssaas.product.model.*;
import dev.lourencogabriel.wmssaas.product.repository.*;
import dev.lourencogabriel.wmssaas.shared.model.UserAccountStatus;
import dev.lourencogabriel.wmssaas.user.model.User;
import dev.lourencogabriel.wmssaas.user.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:product-search;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class ProductSearchIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired AccessProfileRepository profiles;
    @Autowired TokenService tokens;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired ProductPackagingRepository packages;
    String manager;
    String operator;

    @BeforeEach
    void authenticate() { manager = token("manager@product-search.test", "GESTOR"); operator = token("operator@product-search.test", "OPERADOR"); }

    @Test
    void shouldPaginateProductsAndNestedPackagingWithoutLoadingAnUnboundedCollection() throws Exception {
        Category category = category("Drinks", true);
        Product first = null;
        for (int i = 0; i < 23; i++) { Product created = product(category, "SKU-" + i, "Drink " + i, true); if (first == null) first = created; }
        query("/api/products").andExpect(status().isOk()).andExpect(jsonPath("$.data.content", hasSize(20)))
                .andExpect(jsonPath("$.data.totalElements").value(23));
        for (int i = 0; i < 22; i++) packaging(first, "BAR-" + i, true);
        query("/api/products/" + first.getId() + "/packages").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(20))).andExpect(jsonPath("$.data.totalElements").value(22));
        query("/api/products/" + first.getId() + "/packages?page=1").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(2))).andExpect(jsonPath("$.data.page").value(1));
        query("/api/search/product-packages?productId=" + first.getId() + "&size=100").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(22)));
        query("/api/products/" + first.getId() + "/packages?size=101").andExpect(status().isBadRequest());
        query("/api/products/00000000000000000000000000/packages").andExpect(status().isNotFound());
    }

    @Test
    void shouldFindProductsByLiteralPackagingBarcodeAndCombineCategoryAndControlFilters() throws Exception {
        Category drinks = category("Drinks", true); Category food = category("Food", true);
        Product expected = product(drinks, "SKU-JUICE", "Juice", true); expected.setLotControlled(true); products.saveAndFlush(expected);
        Product other = product(food, "SKU-OTHER", "Other juice", true);
        packaging(expected, "READ%_!0001", true); packaging(other, "READABC0001", true);
        mvc.perform(get("/api/search/products").header("Authorization", manager).param("q", "%_!")
                        .param("categoryId", drinks.getId()).param("lotControlled", "true").param("expiryControlled", "false").param("active", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(expected.getId()));
        mvc.perform(get("/api/search/products").header("Authorization", manager).param("q", "%_!").param("categoryId", food.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(get("/api/products").header("Authorization", manager).param("barcode", "read%_!0001").param("q", "JUICE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].id").value(expected.getId()));
        query("/api/search/categories?q=drINks").andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].id").value(drinks.getId()));
        query("/api/lookups/products?q=SKU-JUICE").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].label").value("SKU-JUICE — Juice"));
    }

    @Test
    void shouldReturnCompactActiveLookupPagesForCategoriesProductsAndPackages() throws Exception {
        Category category = category("Active", true); category("Inactive", false);
        Product expected = product(category, "LIVE", "Active product", true); product(category, "OLD", "Inactive product", false);
        for (int i = 0; i < 8; i++) packaging(expected, "PACK-" + i, i < 7);
        query("/api/lookups/categories").andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].label").value("Active"));
        query("/api/lookups/categories?active=false").andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].label").value("Inactive"));
        query("/api/lookups/products").andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].sku").doesNotExist());
        query("/api/lookups/product-packages?productId=" + expected.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(5)).andExpect(jsonPath("$.data.content", hasSize(5)))
                .andExpect(jsonPath("$.data.totalElements").value(7)).andExpect(jsonPath("$.data.content[0].label").isString())
                .andExpect(jsonPath("$.data.content[0].barcode").doesNotExist());
        query("/api/lookups/product-packages?productId=" + expected.getId() + "&active=false").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
        query("/api/lookups/products?size=6").andExpect(status().isBadRequest());
        query("/api/search/product-packages?productId=" + expected.getId() + "&type=UN&q=pack-6").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void shouldRequireProductViewForEveryCatalogSearchAndLookup() throws Exception {
        for (String resource : new String[]{"categories", "products", "product-packages"}) {
            mvc.perform(get("/api/search/" + resource).header("Authorization", operator)).andExpect(status().isOk());
            mvc.perform(get("/api/lookups/" + resource).header("Authorization", operator)).andExpect(status().isOk());
        }
        var profile = profiles.findById("OPERADOR").orElseThrow(); profile.getPermissions().remove(WmsPermission.PRODUCT_VIEW); profiles.saveAndFlush(profile);
        for (String resource : new String[]{"categories", "products", "product-packages"}) {
            mvc.perform(get("/api/search/" + resource).header("Authorization", operator)).andExpect(status().isForbidden());
            mvc.perform(get("/api/lookups/" + resource).header("Authorization", operator)).andExpect(status().isForbidden());
        }
    }

    private ResultActions query(String path) throws Exception { return mvc.perform(get(path).header("Authorization", manager)); }
    private Category category(String name, boolean active) { Category value = new Category(); value.setName(name); value.setActive(active); return categories.saveAndFlush(value); }
    private Product product(Category category, String sku, String name, boolean active) {
        Product value = new Product(); value.setCategoryId(category.getId()); value.setSku(sku); value.setName(name); value.setActive(active); return products.saveAndFlush(value);
    }
    private void packaging(Product product, String barcode, boolean active) {
        ProductPackaging value = new ProductPackaging(); value.setProductId(product.getId()); value.setType("UN"); value.setBarcode(barcode);
        value.setConversionFactor(BigDecimal.ONE); value.setActive(active); packages.saveAndFlush(value);
    }
    private String token(String email, String profile) {
        User value = new User(profile, email, "unused"); value.setProfile(profiles.findById(profile).orElseThrow()); value.setStatus(UserAccountStatus.ACTIVE);
        value.setEmailVerifiedAt(Instant.now()); return "Bearer " + tokens.generateTokens(users.saveAndFlush(value).getUlid()).token();
    }
}
