package dev.lourencogabriel.wmssaas.auth.authorization;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies a fresh schema and the upgrade from the authentication baseline. */
class WmsAuthenticationMigrationTest {
    @Test
    void shouldCreateAuthenticationProfilesAndCompleteWarehouseDomainFromAnEmptyDatabase() {
        String url = databaseUrl();
        var flyway = Flyway.configure().dataSource(url, "sa", "").load();
        assertEquals(3, flyway.migrate().migrationsExecuted);
        var jdbc = jdbc(url);
        assertEquals(2, jdbc.queryForObject("select count(*) from access_profiles", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from users", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from transacoes_estoque", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from reservas_estoque", Integer.class));
        assertEquals("3", flyway.info().current().getVersion().getVersion());
        assertEquals(0, flyway.migrate().migrationsExecuted);
    }

    @Test
    void shouldAddTheDomainWhenTheCompleteAuthenticationBaselineIsAlreadyPresent() {
        String url = databaseUrl();
        var jdbc = jdbc(url);
        var resource = new org.springframework.core.io.ClassPathResource(
                "db/migration/V1__existing_authentication_schema.sql");
        try (var connection = jdbc.getDataSource().getConnection()) {
            org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection, resource);
        } catch (java.sql.SQLException exception) {
            throw new AssertionError(exception);
        }
        var flyway = Flyway.configure().dataSource(url, "sa", "")
                .baselineOnMigrate(true).baselineVersion("1").load();
        assertEquals(2, flyway.migrate().migrationsExecuted);
        assertEquals("3", flyway.info().current().getVersion().getVersion());
        assertEquals(2, jdbc.queryForObject("select count(*) from access_profiles", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from itens_inventario", Integer.class));
    }

    @Test
    void shouldEnforceReferencesCapacityAndGlobalIdempotencyKeysInTheSchema() {
        String url = databaseUrl();
        Flyway.configure().dataSource(url, "sa", "").load().migrate();
        var jdbc = jdbc(url);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into zonas (id, armazem_id, nome, ativo) values ('zone', 'missing', 'A', true)"));
        jdbc.update("insert into armazens (id, nome, criado_em, atualizado_em, ativo) "
                + "values ('warehouse', 'Principal', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, true)");
        jdbc.update("insert into zonas (id, armazem_id, nome, ativo) values ('zone', 'warehouse', 'A', true)");
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into posicoes (id, zona_id, codigo, tipo, capacidade_peso_kg, status, ativo, finalidade) "
                        + "values ('position', 'zone', 'A1', 'FIXA', -1, 'LIVRE', true, 'ARMAZENAGEM')"));
        String operationSql = "insert into operacoes_wms "
                + "(id, chave_idempotencia, tipo, hash_requisicao, status, criado_em) "
                + "values (?, 'same-operation', 'TESTE', ?, 'CONCLUIDA', CURRENT_TIMESTAMP)";
        jdbc.update(operationSql, "op-1", "a".repeat(64));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update(operationSql, "op-2", "b".repeat(64)));
    }

    private static String databaseUrl() {
        return "jdbc:h2:mem:migration-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
    }

    private static JdbcTemplate jdbc(String url) {
        return new JdbcTemplate(new DriverManagerDataSource(url, "sa", ""));
    }
}
