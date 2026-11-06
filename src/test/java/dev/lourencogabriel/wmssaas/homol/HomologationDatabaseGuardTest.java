package dev.lourencogabriel.wmssaas.homol;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class HomologationDatabaseGuardTest {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"wms_saas", "mysql", "production", "wms_saas_homol_prod", "wms_acceptance"})
    void rejectsProductionAndAmbiguousCatalogs(String name) {
        assertThrows(IllegalStateException.class, () -> HomologationDatabaseGuard.requireIsolatedCatalog(name));
    }

    @ParameterizedTest
    @ValueSource(strings = {"wms_saas_homol", "WMS_SAAS_HOMOL", "wms_acceptance_abc123"})
    void acceptsExplicitlyIsolatedCatalogs(String name) {
        assertDoesNotThrow(() -> HomologationDatabaseGuard.requireIsolatedCatalog(name));
    }
}
