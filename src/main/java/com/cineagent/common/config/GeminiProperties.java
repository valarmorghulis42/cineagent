package com.cineagent.common.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe binding for {@code app.ai.gemini.*}. Disabled by default — the deterministic parser
 * (see {@code DeterministicQueryInterpreter}) is what every test and an offline reviewer uses.
 * Set {@code app.ai.gemini.enabled=true} with a real {@code GEMINI_API_KEY} to switch the live
 * NL-search endpoint over to Gemini; {@code GeminiQueryInterpreter} falls back to the
 * deterministic parser on any API failure, so a bad key or an outage degrades the feature, never
 * breaks the endpoint.
 */
@ConfigurationProperties(prefix = "app.ai.gemini")
public record GeminiProperties(boolean enabled, String apiKey, String model, Duration timeout) {}
