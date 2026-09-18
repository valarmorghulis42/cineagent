package com.cineagent.booking.domain;

import com.cineagent.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** The itemized, signed breakdown, persisted at booking time and never recomputed from today's
 * prices later. bookingSeatId is null for order-level lines (a discount, a convenience fee);
 * non-null lines are what pro-rata per-seat refunds sum over. */
@Entity
@Table(name = "booking_charge")
public class BookingCharge extends BaseEntity {

  @Column(name = "booking_id", nullable = false)
  private Long bookingId;

  @Column(name = "booking_seat_id")
  private Long bookingSeatId;

  @Enumerated(EnumType.STRING)
  @Column(name = "charge_type", nullable = false, length = 20)
  private ChargeType chargeType;

  @Column(nullable = false, length = 200)
  private String description;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal amount;

  @Column(nullable = false)
  private boolean refundable = true;

  protected BookingCharge() {}

  public BookingCharge(
      Long bookingId, Long bookingSeatId, ChargeType chargeType, String description, BigDecimal amount, boolean refundable) {
    this.bookingId = bookingId;
    this.bookingSeatId = bookingSeatId;
    this.chargeType = chargeType;
    this.description = description;
    this.amount = amount;
    this.refundable = refundable;
  }

  public Long getBookingSeatId() {
    return bookingSeatId;
  }

  public ChargeType getChargeType() {
    return chargeType;
  }

  public String getDescription() {
    return description;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public boolean isRefundable() {
    return refundable;
  }
}
