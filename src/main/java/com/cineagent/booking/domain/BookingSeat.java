package com.cineagent.booking.domain;

import com.cineagent.catalog.domain.SeatCategory;
import com.cineagent.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Not unique(show_seat_id) alone — book -> cancel -> rebook legitimately produces two rows
 * across different bookings; unique(booking_id, show_seat_id) is what's actually enforced. */
@Entity
@Table(name = "booking_seat", uniqueConstraints = @UniqueConstraint(columnNames = {"booking_id", "show_seat_id"}))
public class BookingSeat extends BaseEntity {

  @Column(name = "booking_id", nullable = false)
  private Long bookingId;

  @Column(name = "show_seat_id", nullable = false)
  private Long showSeatId;

  @Column(name = "seat_label", nullable = false, length = 10)
  private String seatLabel;

  @Enumerated(EnumType.STRING)
  @Column(name = "seat_category", nullable = false, length = 20)
  private SeatCategory seatCategory;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private BookingSeatStatus status = BookingSeatStatus.ACTIVE;

  protected BookingSeat() {}

  public BookingSeat(Long bookingId, Long showSeatId, String seatLabel, SeatCategory seatCategory) {
    this.bookingId = bookingId;
    this.showSeatId = showSeatId;
    this.seatLabel = seatLabel;
    this.seatCategory = seatCategory;
  }

  public Long getShowSeatId() {
    return showSeatId;
  }

  public String getSeatLabel() {
    return seatLabel;
  }

  public SeatCategory getSeatCategory() {
    return seatCategory;
  }

  public BookingSeatStatus getStatus() {
    return status;
  }

  public void cancel() {
    this.status = BookingSeatStatus.CANCELLED;
  }
}
