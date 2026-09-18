package com.cineagent.show.api.dto;

import com.cineagent.catalog.domain.SeatCategory;
import java.math.BigDecimal;

public final class SeatMapDtos {
  private SeatMapDtos() {}

  /**
   * One seat's entry in a show's seat map: identity, category, PRICE, and effective status
   * (already resolved against the current instant — a HELD row whose hold has logically expired
   * comes back AVAILABLE here, never leaking a stale hold to a browsing customer).
   */
  public record SeatMapEntry(
      Long showSeatId,
      String rowLabel,
      int seatNumber,
      String displayLabel,
      SeatCategory category,
      String status,
      BigDecimal price) {}
}
