package com.sop;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Shared base for PostgreSQL-backed integration tests (NFR-041 requires genuine
 * PostgreSQL). We launch a {@code postgres:16-alpine} container via the Docker CLI
 * because the Testcontainers runtime in this environment negotiates Docker API 1.32,
 * which modern engines reject. The container is launched in the static initializer of
 * this class so it is ready BEFORE the Spring context is loaded (Spring's
 * @SpringBootTest loads the context before any @BeforeAll method runs).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractPostgresTest {

  private static final int PG_HOST_PORT = 54321;
  private static final String DB = "sop";
  private static final String USER = "sop";
  private static final String PASS = "sop";
  private static final String IMAGE = "postgres:16-alpine";
  private static final String NAME = "sop_test_pg_" + PG_HOST_PORT;

  @Autowired
  protected JdbcTemplate jdbc;

  static {
    startPostgres();
    // Clean up the container once when this JVM exits (the container itself is not
    // tied to the JVM's lifetime). --rm removes it when stopped.
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      try {
        new ProcessBuilder("docker", "rm", "-f", NAME).redirectErrorStream(true).start().waitFor();
      } catch (Exception ignored) {}
    }));
  }

  @DynamicPropertySource
  static void overrides(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url",
        () -> "jdbc:postgresql://127.0.0.1:" + PG_HOST_PORT + "/" + DB + "?sslmode=disable");
    r.add("spring.datasource.username", () -> USER);
    r.add("spring.datasource.password", () -> PASS);
  }

  @BeforeEach
  void resetTables() {
    jdbc.execute("TRUNCATE TABLE sop_current, publications, drafts");
  }

  private static void startPostgres() {
    // Reuse an existing container bound to the same port.
    if (pgReady()) {
      return;
    }
    // Start fresh.
    try {
      new ProcessBuilder("docker", "rm", "-f", NAME).redirectErrorStream(true).start().waitFor();
    } catch (Exception ignored) {}
    final Process p;
    try {
      p = new ProcessBuilder(
          "docker", "run", "-d", "--rm",
          "--name", NAME,
          "-p", PG_HOST_PORT + ":5432",
          "-e", "POSTGRES_USER=" + USER,
          "-e", "POSTGRES_PASSWORD=" + PASS,
          "-e", "POSTGRES_DB=" + DB,
          IMAGE
      ).redirectErrorStream(true).start();
    } catch (Exception e) {
      throw new IllegalStateException("cannot spawn docker for postgres", e);
    }
    String out = readAll(p);
    int exit;
    try { exit = p.waitFor(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    if (exit != 0) {
      throw new IllegalStateException("docker run failed: " + out.trim());
    }
    long deadline = System.currentTimeMillis() + 60_000L;
    while (!pgReady()) {
      if (System.currentTimeMillis() > deadline) {
        throw new IllegalStateException("postgres did not become ready within 60s");
      }
      try { Thread.sleep(300); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }
  }

  private static boolean pgReady() {
    // Readiness = a real JDBC connection succeeds (Postgres must accept the
    // startup protocol, not just open the TCP port).
    String url = "jdbc:postgresql://127.0.0.1:" + PG_HOST_PORT + "/" + DB + "?sslmode=disable&connectTimeout=2";
    try (java.sql.Connection c = java.sql.DriverManager.getConnection(url, USER, PASS)) {
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  private static String readAll(Process p) {
    StringBuilder sb = new StringBuilder();
    try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
      String line;
      while ((line = br.readLine()) != null) sb.append(line).append('\n');
    } catch (Exception ignored) {}
    return sb.toString();
  }
}
