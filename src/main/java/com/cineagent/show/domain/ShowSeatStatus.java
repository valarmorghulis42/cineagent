package com.cineagent.show.domain;

/**
 * The authoritative occupancy state of one seat for one show.
 *
 * <p>{@code HELD} is only meaningful together with {@code hold_expires_at}: a row whose hold has
 * logically expired is treated as {@code AVAILABLE} by every code path, whether or not the
 * sweeper has run yet. See AGENTS.md §4 and {@code SeatHoldService.effectiveStatus}.
 */
public enum ShowSeatStatus {
  AVAILABLE,
  HELD,
  BOOKED,
  BLOCKED
}
