package com.cineagent.identity.config;

import com.cineagent.identity.security.AppUserDetailsService;
import com.cineagent.identity.security.JwtAuthenticationFilter;
import com.cineagent.identity.service.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * URL-level role gating (coarse, total, auditable in one place) + method-level {@code
 * @PreAuthorize} as a backstop (see {@code @EnableMethodSecurity} below) — ownership checks
 * (e.g. "is this my booking?") are neither: they're explicit domain checks in the owning
 * service, returning 404 rather than 403 to avoid leaking existence. See AGENTS.md §5 / the
 * {@code api-contract} skill for the full reasoning.
 *
 * <p>Stateless JWT, no CSRF (no cookies/sessions to protect), no OAuth/SSO/MFA — see AGENTS.md
 * §2 on why this is the right amount of auth for this assignment's stated scope.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public AuthenticationManager authenticationManager(
      AppUserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
    provider.setPasswordEncoder(passwordEncoder);
    return new org.springframework.security.authentication.ProviderManager(provider);
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService)
      throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/api/v1/auth/**").permitAll()
                    .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html")
                    .permitAll()
                    .requestMatchers("/actuator/health").permitAll()
                    .requestMatchers("/h2-console/**").permitAll() // dev/demo convenience only
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/cities/**",
                        "/api/v1/movies/**",
                        "/api/v1/theaters/**",
                        "/api/v1/shows/**",
                        "/api/v1/screens/**")
                    .permitAll()
                    .requestMatchers("/api/v1/admin/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())
        .headers(h -> h.frameOptions(f -> f.sameOrigin())) // h2-console renders in a frame
        .addFilterBefore(
            new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }
}
