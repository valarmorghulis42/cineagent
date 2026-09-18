package com.cineagent.catalog.api.dto;

import com.cineagent.catalog.domain.Theater;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class TheaterDtos {
  private TheaterDtos() {}

  public record TheaterRequest(
      @NotNull Long cityId,
      @NotBlank @Size(max = 200) String name,
      @NotBlank @Size(max = 500) String address) {}

  public record TheaterResponse(
      Long id, Long cityId, String cityName, String name, String address, boolean active) {
    public static TheaterResponse from(Theater t) {
      return new TheaterResponse(
          t.getId(),
          t.getCity().getId(),
          t.getCity().getName(),
          t.getName(),
          t.getAddress(),
          t.isActive());
    }
  }
}
