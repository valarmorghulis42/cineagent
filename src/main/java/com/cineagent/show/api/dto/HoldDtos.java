package com.cineagent.show.api.dto;

import com.cineagent.show.domain.SeatHold;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class HoldDtos {
  private HoldDtos() {}

  public record CreateHoldRequest(@NotEmpty @Size(max = 10) List<Long> seatIds) {}

  public record HoldResponse(Long holdId, Long showId, String status, int seatCount, Instant expiresAt) {
    public static HoldResponse from(SeatHold hold) {
      return new HoldResponse(
          hold.getId(),
          hold.getShow().getId(),
          hold.getStatus().name(),
          hold.getSeatCount(),
          hold.getExpiresAt());
    }
  }
}
