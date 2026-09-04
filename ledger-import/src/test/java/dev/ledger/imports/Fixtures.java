package dev.ledger.imports;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/** Loads the synthetic corpus from the test classpath. */
public final class Fixtures {

  private Fixtures() {}

  public static byte[] bytes(String name) {
    try (InputStream in = Fixtures.class.getResourceAsStream("/fixtures/" + name)) {
      if (in == null) {
        throw new IllegalStateException(
            "missing fixture " + name + "; regenerate with FixtureGenerator");
      }
      return in.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
