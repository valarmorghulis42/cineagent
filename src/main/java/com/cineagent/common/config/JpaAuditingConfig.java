package com.cineagent.common.config;

import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Wires {@code createdBy}/{@code updatedBy} auditing ({@link com.cineagent.common.persistence.BaseEntity})
 * to the current authenticated principal, falling back to "system" for unauthenticated writes
 * (e.g. Flyway seed data touched via JDBC, or scheduled jobs running outside a request).
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaAuditingConfig {

  @Bean
  public AuditorAware<String> auditorAware() {
    return () -> {
      Authentication auth = SecurityContextHolder.getContext().getAuthentication();
      if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
        return Optional.of("system");
      }
      return Optional.ofNullable(auth.getName());
    };
  }
}
