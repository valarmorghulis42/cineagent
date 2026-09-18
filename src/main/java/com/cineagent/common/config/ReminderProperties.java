package com.cineagent.common.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Type-safe binding for {@code app.reminder.*} — see application.yml. */
@ConfigurationProperties(prefix = "app.reminder")
public record ReminderProperties(int leadTimeHours, Duration checkInterval) {}
