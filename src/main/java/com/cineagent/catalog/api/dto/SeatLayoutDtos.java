package com.cineagent.catalog.api.dto;

import com.cineagent.catalog.domain.Seat;
import com.cineagent.catalog.domain.SeatCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;

/**
 * Bulk seat-layout creation — a screen's layout is rows x seat-counts with a category per row,
 * not 250 individual POSTs. See AGENTS.md / architecture plan enhancement B3.
 *
 * <pre>{@code
 * { "rows": [
 *     {"rowLabel": "A", "seatCount": 10, "category": "REGULAR"},
 *     {"rowLabel": "B", "seatCount": 10, "category": "REGULAR"},
 *     {"rowLabel": "C", "seatCount": 8,  "category": "PREMIUM"}
 * ]}
 * }</pre>
 */
public final class SeatLayoutDtos {
  private SeatLayoutDtos() {}

  public record RowSpec(
      @NotBlank @jakarta.validation.constraints.Size(max = 5) String rowLabel,
      @Min(1) @Max(60) int seatCount,
      @NotNull SeatCategory category) {}

  public record BulkSeatLayoutRequest(@NotEmpty @Valid List<RowSpec> rows) {}

  public record SeatResponse(
      Long id, String rowLabel, int seatNumber, String displayLabel, SeatCategory category) {
    public static SeatResponse from(Seat s) {
      return new SeatResponse(
          s.getId(), s.getRowLabel(), s.getSeatNumber(), s.getDisplayLabel(), s.getCategory());
    }
  }
}
