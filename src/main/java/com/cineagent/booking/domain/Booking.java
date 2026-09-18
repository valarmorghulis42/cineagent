package com.cineagent.booking.domain;

import com.cineagent.common.error.ConflictException;
import com.cineagent.common.error.ErrorCode;
import com.cineagent.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "booking")
public class Booking extends BaseEntity {

  @Column(name = "booking_reference", nullable = false, unique = true, length = 20)
  private String bookingReference;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "show_id", nullable = false)
  private Long showId;

  @Column(name = "hold_id", nullable = false)
  private Long holdId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private BookingStatus status = BookingStatus.PENDING_PAYMENT;

  @Column(name = "seat_count", nullable = false)
  private int seatCount;

  @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
  private BigDecimal totalAmount;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "discount_code_id")
  private Long discountCodeId;

  protected Booking() {}

  public Booking(
      String bookingReference,
      Long userId,
      Long showId,
      Long holdId,
      int seatCount,
      BigDecimal totalAmount,
      String currency,
      Long discountCodeId) {
    this.bookingReference = bookingReference;
    this.userId = userId;
    this.showId = showId;
    this.holdId = holdId;
    this.seatCount = seatCount;
    this.totalAmount = totalAmount;
    this.currency = currency;
    this.discountCodeId = discountCodeId;
  }

  public void transitionTo(BookingStatus target) {
    if (!status.canTransitionTo(target)) {
      throw new ConflictException(
          ErrorCode.INVALID_BOOKING_STATE, "Cannot move booking from " + status + " to " + target);
    }
    this.status = target;
  }

  public String getBookingReference() {
    return bookingReference;
  }

  public Long getUserId() {
    return userId;
  }

  public Long getShowId() {
    return showId;
  }

  public Long getHoldId() {
    return holdId;
  }

  public BookingStatus getStatus() {
    return status;
  }

  public int getSeatCount() {
    return seatCount;
  }

  public BigDecimal getTotalAmount() {
    return totalAmount;
  }

  public String getCurrency() {
    return currency;
  }

  public Long getDiscountCodeId() {
    return discountCodeId;
  }

  public void reduceSeatCount(int cancelledSeats) {
    this.seatCount -= cancelledSeats;
  }
}
