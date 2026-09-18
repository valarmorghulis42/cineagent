package com.cineagent.common.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Type-safe binding for {@code app.hold.*} — see application.yml. */
@ConfigurationProperties(prefix = "app.hold")
public record HoldProperties(
    int ttlMinutes,
    int paymentWindowMinutes,
    int maxSeatsPerHold,
    int maxActiveHoldsPerUser,
    Duration sweepInterval,
    int sweepBatchSize) {}
