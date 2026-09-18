package com.cineagent.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Scheduling (the hold-expiry sweeper, the outbox dispatcher, the reminder job) is on by default
 * but can be switched off via {@code app.scheduling.enabled=false} — most integration tests do
 * this so they aren't racing a background thread; they invoke the scheduled methods directly
 * with an advanceable {@link java.time.Clock} instead. See the {@code concurrency-testing} skill.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(value = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {}
