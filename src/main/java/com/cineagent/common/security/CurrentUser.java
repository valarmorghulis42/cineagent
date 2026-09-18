package com.cineagent.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

/**
 * Readable alias for {@code @AuthenticationPrincipal AuthenticatedUser} on a controller method
 * parameter — the JWT filter sets an {@link AuthenticatedUser} as the authentication principal,
 * so this resolves for free via Spring Security's existing argument resolver.
 *
 * <pre>{@code
 * @PostMapping("/bookings")
 * public BookingResponse create(@CurrentUser AuthenticatedUser user, @RequestBody CreateBookingRequest req) { ... }
 * }</pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@AuthenticationPrincipal
public @interface CurrentUser {}
