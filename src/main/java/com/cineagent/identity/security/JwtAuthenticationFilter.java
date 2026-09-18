package com.cineagent.identity.security;

import com.cineagent.common.security.AuthenticatedUser;
import com.cineagent.identity.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads {@code Authorization: Bearer <token>}, verifies it via {@link JwtService}, and — if
 * valid — sets an {@link AuthenticatedUser} as the authentication principal. Stateless: no
 * session, no DB lookup per request, the JWT itself carries userId/email/roles as signed claims.
 *
 * <p>An invalid/missing/expired token does not throw here; the request simply proceeds
 * unauthenticated, and {@code SecurityConfig}'s authorization rules (or the entry point on a
 * protected route) decide what happens next.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtService jwtService;

  public JwtAuthenticationFilter(JwtService jwtService) {
    this.jwtService = jwtService;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      String token = header.substring(7);
      Optional<AuthenticatedUser> user = jwtService.parse(token);
      user.ifPresent(
          u -> {
            List<? extends GrantedAuthority> authorities =
                u.roles().stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
            var auth = new UsernamePasswordAuthenticationToken(u, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(auth);
          });
    }
    filterChain.doFilter(request, response);
  }
}
