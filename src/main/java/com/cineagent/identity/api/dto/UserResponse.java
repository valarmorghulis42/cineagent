package com.cineagent.identity.api.dto;

import com.cineagent.identity.domain.User;
import java.util.Set;
import java.util.stream.Collectors;

public record UserResponse(Long id, String email, String fullName, String phone, Set<String> roles) {
  public static UserResponse from(User user) {
    return new UserResponse(
        user.getId(),
        user.getEmail(),
        user.getFullName(),
        user.getPhone(),
        user.getRoles().stream().map(Enum::name).collect(Collectors.toSet()));
  }
}
