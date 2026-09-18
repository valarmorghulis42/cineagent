package com.cineagent.identity.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Type-safe binding for {@code app.jwt.*} — see application.yml. */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, int expirationMinutes) {}
