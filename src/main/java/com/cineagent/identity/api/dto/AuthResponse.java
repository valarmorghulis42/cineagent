package com.cineagent.identity.api.dto;

import java.time.Instant;
import java.util.Set;

public record AuthResponse(
    String token, Instant expiresAt, Long userId, String email, String fullName, Set<String> roles) {}
