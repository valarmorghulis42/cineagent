package com.cineagent.show.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;

/**
 * The authoritative occupancy row for one (show, seat) pair. {@code status} is the truth about
 * what is occupied; {@code booking_seat} (added with the booking module) is merely the record of
 * what was sold. See AGENTS.md §4 for the full concurrency design this entity implements:
 * single-table PK-only locking, ascending-id lock ordering, lazy expiry as the correctness
 * mechanism, and {@code @Version} as a measured-and-justified (not assumed) backstop.
 *
 * <p>Does NOT extend {@code BaseEntity} — this is the hottest-written row in the system and does
 * not need createdBy/updatedBy audit columns; it has its own {@code id} and {@code @Version}.
 */
@Entity
@Table(name = "show_seat", uniqueConstraints = @UniqueConstraint(columnNames = {"show_id", "seat_id"}))
public class ShowSeat {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "show_id", nullable = false)
  private Show show;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "seat_id", nullable = false)
  private com.cineagent.catalog.domain.Seat seat;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private ShowSeatStatus status = ShowSeatStatus.AVAILABLE;

  @Column(name = "hold_id")
  private Long holdId;

  /** Denormalised from seat_hold.expires_at — always written in the same transaction as holdId. */
  @Column(name = "hold_expires_at")
  private Instant holdExpiresAt;

  @Column(name = "booking_id")
  private Long bookingId;

  @Version
  @Column(nullable = false)
  private Long version;

  protected ShowSeat() {}

  public ShowSeat(Show show, com.cineagent.catalog.domain.Seat seat) {
    this.show = show;
    this.seat = seat;
  }

  public Long getId() {
    return id;
  }

  public Show getShow() {
    return show;
  }

  public com.cineagent.catalog.domain.Seat getSeat() {
    return seat;
  }

  public ShowSeatStatus getStatus() {
    return status;
  }

  public Long getHoldId() {
    return holdId;
  }

  public Instant getHoldExpiresAt() {
    return holdExpiresAt;
  }

  public Long getBookingId() {
    return bookingId;
  }

  public Long getVersion() {
    return version;
  }

  /**
   * Re-derives status from the clock rather than trusting the stored value — the core of the
   * lazy-expiry design. Call this on every locked row before acting on its status. Returns
   * AVAILABLE if the stored status is HELD but the hold has logically expired, regardless of
   * whether the sweeper has run yet.
   */
  public ShowSeatStatus effectiveStatus(Instant now) {
    if (status == ShowSeatStatus.HELD && holdExpiresAt != null && !holdExpiresAt.isAfter(now)) {
      return ShowSeatStatus.AVAILABLE;
    }
    return status;
  }

  public void assignHold(Long holdId, Instant expiresAt) {
    this.status = ShowSeatStatus.HELD;
    this.holdId = holdId;
    this.holdExpiresAt = expiresAt;
  }

  public void releaseHold() {
    this.status = ShowSeatStatus.AVAILABLE;
    this.holdId = null;
    this.holdExpiresAt = null;
  }

  public void confirmBooking(Long bookingId) {
    this.status = ShowSeatStatus.BOOKED;
    this.holdId = null;
    this.holdExpiresAt = null;
    this.bookingId = bookingId;
  }

  /** A booked seat's cancellation (partial or full) releases it back to AVAILABLE for resale. */
  public void cancelBooking() {
    this.status = ShowSeatStatus.AVAILABLE;
    this.bookingId = null;
  }

  public void block() {
    this.status = ShowSeatStatus.BLOCKED;
  }

  public void unblock() {
    this.status = ShowSeatStatus.AVAILABLE;
  }
}
