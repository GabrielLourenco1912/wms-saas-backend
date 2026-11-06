package dev.lourencogabriel.wmssaas.homol;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles({"test", "homol"})
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class HomologationMigrationIntegrationTest {
    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0")
            .withDatabaseName("wms_saas_homol").withUsername("fixture").withPassword("fixture");
    static final String HASH = new BCryptPasswordEncoder().encode("fixture-test-password");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", MYSQL::getJdbcUrl);
        r.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);
        r.add("spring.flyway.url", MYSQL::getJdbcUrl);
        r.add("spring.flyway.user", () -> "root");
        r.add("spring.flyway.password", MYSQL::getPassword);
        r.add("spring.flyway.placeholders.homol_manager_hash", () -> HASH);
        r.add("spring.flyway.placeholders.homol_operator_hash", () -> HASH);
    }

    @Autowired JdbcTemplate jdbc;

    @Test void loadsSyntheticDataAndProtectsItsHistoryInRealMySql() {
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class));
        String stored = jdbc.queryForObject("SELECT password FROM users WHERE email = 'gestor@homol.wms.local'", String.class);
        assertTrue(new BCryptPasswordEncoder().matches("fixture-test-password", stored));
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM estoque", Integer.class));
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM transacoes_estoque", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM tarefas", Integer.class));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE", Integer.class));
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> jdbc.update("DELETE FROM transacoes_estoque"));
        // Restart/validate must not rerun the dataset or duplicate its records.
        var flyway = org.flywaydb.core.Flyway.configure().dataSource(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword())
                .locations("classpath:db/migration", "classpath:db/homol")
                .placeholders(java.util.Map.of("homol_manager_hash", HASH, "homol_operator_hash", HASH)).load();
        assertEquals(0, flyway.migrate().migrationsExecuted);
    }
}
