package dev.ledger.app;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base for tests that need the real database.
 *
 * <p>SPEC §7.2 requires Testcontainers against a real PostgreSQL image rather than H2. H2 accepts
 * SQL that PostgreSQL rejects, so it would happily pass the migrations in {@code db/migration}
 * while hiding whether they actually work — and the schema is where most of what these tests check
 * lives: the partial index, the check constraints, {@code numeric(19,4)}, {@code
 * gen_random_uuid()}.
 *
 * <p>The container is {@code static}, so one instance is shared by every test in the suite and
 * Spring's context cache keeps the application context alive alongside it.
 *
 * <p>Subclasses are skipped, not failed, on a machine with no Docker daemon; CI runs on a runner
 * that has one, and its workflow checks for it before the build so a silent skip is not possible
 * there.
 */
@SpringBootTest
@Testcontainers
@RequiresDocker
public abstract class PostgresIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  public static boolean dockerIsAvailable() {
    try {
      return DockerClientFactory.instance().isDockerAvailable();
    } catch (RuntimeException e) {
      return false;
    }
  }
}
