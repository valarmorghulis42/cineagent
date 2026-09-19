package com.cineagent.common.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe binding for {@code app.eta.*}. {@code travelMinutes} is a flat, configurable
 * estimate — not a real maps/traffic API call, kept injectable on purpose so the nudge feature
 * demos deterministically with no external key (same design rule as {@code PaymentGateway}: a
 * port earns its place only when a second real implementation is imminent, and a real ETA
 * provider isn't).
 */
@ConfigurationProperties(prefix = "app.eta")
public record EtaNudgeProperties(int travelMinutes, Duration checkInterval) {}
