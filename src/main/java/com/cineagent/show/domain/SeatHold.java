package com.cineagent.show.domain;

import com.cineagent.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * The hold aggregate: one header row per hold attempt, referenced by the {@link ShowSeat} rows
 * it covers via {@code show_seat.hold_id}. Exists so the per-user active-hold cap is a one-row
 * count (not a cross-show scan of show_seat), hold extension is a one-row UPDATE, and the API has
 * a stable reference to hand back to the client. See AGENTS.md domain note on why this table
 * exists rather than holds being columns on show_seat alone.
 */
@Entity
@Table(name = "seat_hold")
public class SeatHold extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "show_id", nullable = false)
  private Show show;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private HoldStatus status = HoldStatus.ACTIVE;

  @Column(name = "seat_count", nullable = false)
  private int seatCount;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  protected SeatHold() {}

  public SeatHold(Show show, Long userId, int seatCount, Instant expiresAt) {
    this.show = show;
    this.userId = userId;
    this.seatCount = seatCount;
    this.expiresAt = expiresAt;
  }

  public Show getShow() {
    return show;
  }

  public Long getUserId() {
    return userId;
  }

  public HoldStatus getStatus() {
    return status;
  }

  public int getSeatCount() {
    return seatCount;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public void extend(Instant newExpiresAt) {
    this.expiresAt = newExpiresAt;
  }

  public void markConsumed() {
    this.status = HoldStatus.CONSUMED;
  }

  public void markExpired() {
    this.status = HoldStatus.EXPIRED;
  }

  public void markReleased() {
    this.status = HoldStatus.RELEASED;
  }

  /** Logical (Clock-derived) expiry check — see AGENTS.md: lazy expiry is the correctness mechanism. */
  public boolean isLogicallyExpired(Instant now) {
    return status == HoldStatus.ACTIVE && !expiresAt.isAfter(now);
  }
}
