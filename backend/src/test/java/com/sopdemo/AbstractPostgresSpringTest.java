package com.sopdemo;

import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base for integration tests: one real PostgreSQL per test JVM (NFR-041), with a
 * fresh schema per run. Flyway applies the migrations; the `demo` seed profile
 * stays inactive here.
 *
 * <p>The container is started once (static initializer) and intentionally NOT
 * managed by the Testcontainers JUnit extension: every integration test class
 * shares the Spring test context and therefore must keep the same database
 * alive for the whole JVM. Testcontainers removes the container on JVM exit.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractPostgresSpringTest {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("sopdemo").withUsername("sopdemo");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
