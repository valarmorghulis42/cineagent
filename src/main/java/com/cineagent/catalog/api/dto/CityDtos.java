package com.cineagent.catalog.api.dto;

import com.cineagent.catalog.domain.City;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class CityDtos {
  private CityDtos() {}

  public record CityRequest(
      @NotBlank @Size(max = 100) String name,
      @Size(max = 100) String state,
      @NotBlank @Size(max = 100) String country,
      @NotBlank @Size(max = 50) String timezone) {}

  public record CityResponse(
      Long id, String name, String state, String country, String timezone, boolean active) {
    public static CityResponse from(City c) {
      return new CityResponse(
          c.getId(), c.getName(), c.getState(), c.getCountry(), c.getTimezone(), c.isActive());
    }
  }
}
