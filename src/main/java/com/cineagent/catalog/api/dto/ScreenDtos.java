package com.cineagent.catalog.api.dto;

import com.cineagent.catalog.domain.Screen;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class ScreenDtos {
  private ScreenDtos() {}

  public record ScreenRequest(@NotNull Long theaterId, @NotBlank @Size(max = 50) String name) {}

  public record ScreenResponse(
      Long id, Long theaterId, String theaterName, String name, int totalSeats, boolean active) {
    public static ScreenResponse from(Screen s) {
      return new ScreenResponse(
          s.getId(),
          s.getTheater().getId(),
          s.getTheater().getName(),
          s.getName(),
          s.getTotalSeats(),
          s.isActive());
    }
  }
}
