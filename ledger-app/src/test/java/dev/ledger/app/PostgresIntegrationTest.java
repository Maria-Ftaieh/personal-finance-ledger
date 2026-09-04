package dev.ledger.app;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base for tests that need the real database.
 *
 * <p>SPEC §7.2 requires Testcontainers against a real PostgreSQL image rather than H2. H2 accepts
 * SQL that PostgreSQL rejects, so it would happily pass the migrations in {@code db/migration}
 * while hiding whether they actually work — and the schema is where most of what these tests check
 * lives: the partial index, the check constraints, {@code numeric(19,4)}, {@code
 * gen_random_uuid()}.
 *
 * <p><b>One container for the whole suite</b>, as §7.2 asks. It is started once in a static
 * initialiser and deliberately never stopped; Testcontainers' reaper removes it when the JVM exits.
 * The obvious-looking alternative — {@code @Testcontainers} with a {@code @Container} static field
 * on this class — is wrong here, and fails in a way that is easy to misread. That annotation stops
 * the container after <em>each</em> subclass, while Spring's context cache keeps the application
 * context and its connection pool alive across classes; the second test class then inherits a pool
 * pointing at a container that no longer exists, and every test in it dies with "Could not open JPA
 * EntityManager" after a long timeout.
 *
 * <p>Subclasses are skipped, not failed, on a machine with no Docker daemon. CI runs on a runner
 * that has one and checks for it before the build, so a silent skip is not possible there.
 */
@SpringBootTest
@RequiresDocker
public abstract class PostgresIntegrationTest {

  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:17-alpine");

  static {
    if (dockerIsAvailable()) {
      POSTGRES.start();
    }
  }

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  public static boolean dockerIsAvailable() {
    try {
      return DockerClientFactory.instance().isDockerAvailable();
    } catch (RuntimeException e) {
      return false;
    }
  }
}
