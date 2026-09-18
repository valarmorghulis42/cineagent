package com.cineagent.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** The audit-trail half of "view booking history" (the other half is the plain booking list).
 * Does not extend BaseEntity -- append-only, never updated, no updatedBy/updatedAt needed. */
@Entity
@Table(name = "booking_status_history")
public class BookingStatusHistory {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "booking_id", nullable = false)
  private Long bookingId;

  @Column(name = "from_status", length = 24)
  private String fromStatus;

  @Column(name = "to_status", nullable = false, length = 24)
  private String toStatus;

  @Column(length = 500)
  private String reason;

  @Column(name = "changed_at", nullable = false)
  private Instant changedAt;

  @Column(name = "changed_by", nullable = false, length = 128)
  private String changedBy;

  protected BookingStatusHistory() {}

  public BookingStatusHistory(
      Long bookingId, BookingStatus fromStatus, BookingStatus toStatus, String reason, Instant changedAt, String changedBy) {
    this.bookingId = bookingId;
    this.fromStatus = fromStatus == null ? null : fromStatus.name();
    this.toStatus = toStatus.name();
    this.reason = reason;
    this.changedAt = changedAt;
    this.changedBy = changedBy;
  }

  public Long getId() {
    return id;
  }

  public String getFromStatus() {
    return fromStatus;
  }

  public String getToStatus() {
    return toStatus;
  }

  public String getReason() {
    return reason;
  }

  public Instant getChangedAt() {
    return changedAt;
  }
}
