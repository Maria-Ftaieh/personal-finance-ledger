package dev.ledger.app;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Skips a test class when there is no Docker daemon to run PostgreSQL in.
 *
 * <p>JUnit's {@code @EnabledIf} is not inherited, so putting it on {@link PostgresIntegrationTest}
 * alone would leave every subclass unguarded. This composed annotation is {@code @Inherited}, which
 * makes the guard apply to the whole hierarchy and means a new integration test cannot forget it.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@EnabledIf(
    value = "dev.ledger.app.PostgresIntegrationTest#dockerIsAvailable",
    disabledReason = "no Docker daemon; the PostgreSQL integration tests need one")
public @interface RequiresDocker {}
