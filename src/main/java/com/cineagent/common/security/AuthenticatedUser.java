package com.cineagent.common.security;

import java.util.Set;

/**
 * The JWT principal set on {@link org.springframework.security.core.Authentication} by the
 * identity module's authentication filter. Carries just enough to authorize and attribute a
 * request without a DB round trip per request — the userId and role set are both signed claims
 * inside the JWT itself.
 *
 * <p>Every feature depends on this type (it's the "who is calling" contract), but never on the
 * identity module's internal JWT machinery — see AGENTS.md §3's dependency rule.
 */
public record AuthenticatedUser(Long userId, String email, Set<String> roles) {

  public boolean isAdmin() {
    return roles.contains("ADMIN");
  }
}
