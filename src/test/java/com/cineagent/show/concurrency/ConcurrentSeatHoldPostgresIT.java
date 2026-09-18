package com.cineagent.show.concurrency;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Runs the shared concurrency proof against real, embedded Postgres — the intended production
 * engine, and the one that genuinely honours {@code SELECT ... FOR UPDATE} as an exclusive row
 * lock (AGENTS.md §4.5). Passing on H2 alone leaves this claim unproven; this class is what
 * closes that gap.
 *
 * <p>Uses the raw {@code io.zonky.test:embedded-postgres} API directly, not
 * {@code embedded-database-spring-test}/{@code @AutoConfigureEmbeddedDatabase} — that module is
 * unvalidated against Boot 4 / Spring Framework 7 / JUnit Platform 6 (AGENTS.md §7).
 *
 * <p><b>Fails loudly, never silently skips</b>, if the embedded binary can't start — an invisible
 * skip is how an unproven guarantee ships. The one sanctioned opt-out is explicit:
 * {@code -Dpostgres.it.skip=true}, for a reviewer running fully offline. Document this in the
 * README; a passing {@code mvn verify} must otherwise mean this class ran and passed.
 */
@DisabledIfSystemProperty(named = "postgres.it.skip", matches = "true")
@SpringBootTest(
    webEnvironment = WebEnvironment.MOCK,
    properties = {
      "spring.datasource.hikari.maximum-pool-size=40",
      "app.scheduling.enabled=false"
    })
@ActiveProfiles("postgres")
class ConcurrentSeatHoldPostgresIT extends AbstractConcurrentSeatHoldIT {

  private static EmbeddedPostgres pg;

  @BeforeAll
  static void startEmbeddedPostgres() throws IOException {
    // No try/catch-and-skip here on purpose (see class javadoc) — if this throws, mvn verify
    // goes red, which is the correct, honest outcome for an unstartable Postgres binary.
    pg = EmbeddedPostgres.builder().start();
  }

  @AfterAll
  static void stopEmbeddedPostgres() throws IOException {
    if (pg != null) {
      pg.close();
    }
  }

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> pg.getJdbcUrl("postgres", "postgres"));
    registry.add("spring.datasource.username", () -> "postgres");
    registry.add("spring.datasource.password", () -> "postgres");
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
  }
}
