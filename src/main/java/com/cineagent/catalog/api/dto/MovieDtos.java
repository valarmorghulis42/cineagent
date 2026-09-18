package com.cineagent.catalog.api.dto;

import com.cineagent.catalog.domain.Movie;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public final class MovieDtos {
  private MovieDtos() {}

  public record MovieRequest(
      @NotBlank @Size(max = 300) String title,
      @NotBlank @Size(max = 50) String language,
      @Min(1) int durationMinutes,
      @Size(max = 20) String certification,
      @Size(max = 2000) String synopsis,
      LocalDate releaseDate) {}

  public record MovieResponse(
      Long id,
      String title,
      String language,
      int durationMinutes,
      String certification,
      String synopsis,
      LocalDate releaseDate,
      boolean active) {
    public static MovieResponse from(Movie m) {
      return new MovieResponse(
          m.getId(),
          m.getTitle(),
          m.getLanguage(),
          m.getDurationMinutes(),
          m.getCertification(),
          m.getSynopsis(),
          m.getReleaseDate(),
          m.isActive());
    }
  }
}
