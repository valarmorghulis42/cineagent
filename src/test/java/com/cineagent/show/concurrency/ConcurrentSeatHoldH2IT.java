package com.cineagent.show.concurrency;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

/**
 * Runs the shared concurrency proof against the default H2 profile — the engine every reviewer
 * actually launches. H2 does not document {@code SELECT ... FOR UPDATE} as taking a true
 * exclusive row lock (AGENTS.md §4.5), so a green run here is what actually backs the
 * no-double-booking claim on the shipped default, not just on the "real" production engine.
 *
 * <p>Pool size raised to 40 (worker-thread counts here top out at 32) and the sweeper disabled —
 * see the {@code concurrency-testing} skill, rule 2. Uses {@code WebEnvironment.MOCK}, not
 * {@code NONE} — {@code NONE} skips web autoconfiguration entirely, and {@code SecurityConfig}'s
 * {@code SecurityFilterChain} bean needs an {@code HttpSecurity} bean that only exists in at
 * least a mock web context. No server actually starts; these tests call the service layer
 * directly.
 */
@SpringBootTest(
    webEnvironment = WebEnvironment.MOCK,
    properties = {
      "spring.datasource.hikari.maximum-pool-size=40",
      "app.scheduling.enabled=false"
    })
class ConcurrentSeatHoldH2IT extends AbstractConcurrentSeatHoldIT {}
