package com.cineagent.booking.domain;

import java.util.Map;
import java.util.Set;

public enum BookingStatus {
  PENDING_PAYMENT,
  CONFIRMED,
  PARTIALLY_CANCELLED,
  CANCELLED,
  PAYMENT_FAILED,
  SEAT_LOST_AFTER_PAYMENT;

  private static final Map<BookingStatus, Set<BookingStatus>> ALLOWED =
      Map.of(
          PENDING_PAYMENT, Set.of(CONFIRMED, PAYMENT_FAILED, SEAT_LOST_AFTER_PAYMENT),
          CONFIRMED, Set.of(PARTIALLY_CANCELLED, CANCELLED),
          PARTIALLY_CANCELLED, Set.of(PARTIALLY_CANCELLED, CANCELLED),
          CANCELLED, Set.of(),
          PAYMENT_FAILED, Set.of(),
          SEAT_LOST_AFTER_PAYMENT, Set.of());

  public boolean canTransitionTo(BookingStatus target) {
    return ALLOWED.getOrDefault(this, Set.of()).contains(target);
  }
}
