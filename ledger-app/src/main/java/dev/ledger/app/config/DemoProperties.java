package dev.ledger.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for a publicly reachable demonstration deployment.
 *
 * <p>The application is single user and has no authentication, which is fine on a machine only its
 * owner can reach and completely wrong on the open internet. Rather than bolt on half an auth
 * system for a demo, {@code readOnly} makes the deployment safe to expose: everything can be read,
 * nothing can be changed.
 *
 * <p>Off by default. A self-hosted instance is not a demo.
 *
 * @param readOnly refuse every state-changing request, and parse uploads without storing them
 */
@ConfigurationProperties("ledger.demo")
public record DemoProperties(boolean readOnly) {}
