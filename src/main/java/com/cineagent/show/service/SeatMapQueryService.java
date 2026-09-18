package com.cineagent.show.service;

import com.cineagent.common.error.ErrorCode;
import com.cineagent.common.error.UnprocessableException;
import com.cineagent.show.api.dto.SeatMapDtos.SeatMapEntry;
import com.cineagent.show.domain.ShowSeatStatus;
import com.cineagent.show.repository.ShowSeatRepository;
import com.cineagent.show.repository.ShowSeatRepository.SeatMapRow;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serves a show's seat map via ONE projection query (see SeatMapQueryCountTest) and resolves
 * effective status against the injected Clock, so a customer browsing never sees a stale HELD
 * seat even if the sweeper hasn't run yet — see AGENTS.md §4 on lazy expiry.
 */
@Service
public class SeatMapQueryService {

  private final ShowSeatRepository showSeatRepository;
  private final Clock clock;

  public SeatMapQueryService(ShowSeatRepository showSeatRepository, Clock clock) {
    this.showSeatRepository = showSeatRepository;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<SeatMapEntry> getSeatMap(Long showId) {
    List<SeatMapRow> rows = showSeatRepository.findSeatMapByShowId(showId);
    Instant now = Instant.now(clock);

    Set<String> missingPriceCategories = new LinkedHashSet<>();
    List<SeatMapEntry> entries =
        rows.stream()
            .map(
                row -> {
                  if (row.getBaseAmount() == null) {
                    missingPriceCategories.add(row.getCategory().name());
                  }
                  String effectiveStatus = effectiveStatus(row, now);
                  return new SeatMapEntry(
                      row.getShowSeatId(),
                      row.getRowLabel(),
                      row.getSeatNumber(),
                      row.getDisplayLabel(),
                      row.getCategory(),
                      effectiveStatus,
                      row.getBaseAmount());
                })
            .toList();

    if (!missingPriceCategories.isEmpty()) {
      // A missing price is a hard configuration error, never a silent 0.00 — see the domain
      // note on show_price in AGENTS.md / the architecture plan.
      throw new UnprocessableException(
          ErrorCode.SHOW_NOT_BOOKABLE,
          "Show "
              + showId
              + " has no price configured for seat categories: "
              + missingPriceCategories);
    }
    return entries;
  }

  private String effectiveStatus(SeatMapRow row, Instant now) {
    if (row.getStatus() == ShowSeatStatus.HELD
        && row.getHoldExpiresAt() != null
        && !row.getHoldExpiresAt().isAfter(now)) {
      return ShowSeatStatus.AVAILABLE.name();
    }
    return row.getStatus().name();
  }
}
