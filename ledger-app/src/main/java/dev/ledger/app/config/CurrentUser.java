package dev.ledger.app.config;

import java.util.UUID;

/**
 * Who the request is for.
 *
 * <p>The application is self-hosted and single user, and SPEC does not ask for authentication. It
 * does ask for the fingerprint uniqueness and the reporting index to be scoped by user, so every
 * row carries a {@code user_id} from the first migration and this is the one place that decides
 * what goes in it. Adding real authentication later replaces this class; it does not touch the
 * schema, the queries or the constraints.
 */
public final class CurrentUser {

  public static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

  private CurrentUser() {}
}
