package com.cineagent.show.api.dto;

import com.cineagent.catalog.domain.SeatCategory;
import com.cineagent.show.domain.Show;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class ShowDtos {
  private ShowDtos() {}

  public record PriceSpec(@NotNull SeatCategory category, @DecimalMin("0.00") BigDecimal baseAmount) {}

  public record CreateShowRequest(
      @NotNull Long screenId,
      @NotNull Long movieId,
      @NotNull @Future Instant startsAt,
      @NotNull Instant endsAt,
      Instant salesOpenAt,
      Instant salesCloseAt,
      @NotEmpty @Valid List<PriceSpec> prices) {}

  public record ShowResponse(
      Long id,
      Long screenId,
      String screenName,
      Long theaterId,
      String theaterName,
      Long movieId,
      String movieTitle,
      Long cityId,
      String cityName,
      Instant startsAt,
      Instant endsAt,
      String status,
      Instant salesOpenAt,
      Instant salesCloseAt,
      String currency) {
    public static ShowResponse from(Show s) {
      return new ShowResponse(
          s.getId(),
          s.getScreen().getId(),
          s.getScreen().getName(),
          s.getScreen().getTheater().getId(),
          s.getScreen().getTheater().getName(),
          s.getMovie().getId(),
          s.getMovie().getTitle(),
          s.getCity().getId(),
          s.getCity().getName(),
          s.getStartsAt(),
          s.getEndsAt(),
          s.getStatus().name(),
          s.getSalesOpenAt(),
          s.getSalesCloseAt(),
          s.getCurrency());
    }
  }
}
