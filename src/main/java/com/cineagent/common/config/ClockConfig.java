package com.cineagent.common.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The single source of "now" for the whole application.
 *
 * <p>Every piece of code that needs the current time injects {@link Clock} and calls {@code
 * clock.instant()} — direct calls to {@code Instant.now()} / {@code LocalDate.now()} are banned
 * in {@code main} (see AGENTS.md §4.1). This is what makes hold-expiry and refund-window tests
 * deterministic: a test wires an overridable/fixed {@link Clock} bean instead of sleeping for
 * real seconds.
 */
@Configuration
public class ClockConfig {

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
