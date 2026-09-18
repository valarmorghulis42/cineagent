package com.cineagent.booking.api.dto;

import com.cineagent.booking.domain.Booking;
import com.cineagent.booking.domain.BookingCharge;
import com.cineagent.booking.domain.BookingSeat;
import com.cineagent.booking.domain.BookingStatusHistory;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class BookingDtos {
  private BookingDtos() {}

  public record CreateBookingRequest(@NotNull Long holdId, String discountCode) {}

  public record CancelSeatsRequest(@NotEmpty List<Long> bookingSeatIds, String reason) {}

  public record BookingResponse(
      Long id,
      String bookingReference,
      String status,
      Long showId,
      int seatCount,
      BigDecimal totalAmount,
      String currency,
      Instant createdAt) {
    public static BookingResponse from(Booking b) {
      return new BookingResponse(
          b.getId(), b.getBookingReference(), b.getStatus().name(), b.getShowId(), b.getSeatCount(),
          b.getTotalAmount(), b.getCurrency(), b.getCreatedAt());
    }
  }

  public record BookingSeatResponse(Long id, String seatLabel, String category, String status) {
    public static BookingSeatResponse from(BookingSeat s) {
      return new BookingSeatResponse(s.getId(), s.getSeatLabel(), s.getSeatCategory().name(), s.getStatus().name());
    }
  }

  public record BookingChargeResponse(Long bookingSeatId, String chargeType, String description, BigDecimal amount) {
    public static BookingChargeResponse from(BookingCharge c) {
      return new BookingChargeResponse(c.getBookingSeatId(), c.getChargeType().name(), c.getDescription(), c.getAmount());
    }
  }

  public record BookingStatusHistoryResponse(String fromStatus, String toStatus, String reason, Instant changedAt) {
    public static BookingStatusHistoryResponse from(BookingStatusHistory h) {
      return new BookingStatusHistoryResponse(h.getFromStatus(), h.getToStatus(), h.getReason(), h.getChangedAt());
    }
  }
}
