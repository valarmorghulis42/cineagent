package com.cineagent.booking.service;

import com.cineagent.booking.domain.Booking;
import com.cineagent.booking.domain.BookingStatus;
import com.cineagent.booking.repository.BookingRepository;
import com.cineagent.show.service.ShowService;
import java.util.List;
import org.springframework.stereotype.Service;

/** Admin show cancellation cascades into a bulk per-booking refund (plan enhancement B5) — lives
 * in booking, not show, since show must not depend on booking (AGENTS.md dependency rule). */
@Service
public class AdminShowCancellationService {

  private final ShowService showService;
  private final BookingRepository bookingRepository;
  private final BookingCancellationService cancellationService;

  public AdminShowCancellationService(
      ShowService showService, BookingRepository bookingRepository, BookingCancellationService cancellationService) {
    this.showService = showService;
    this.bookingRepository = bookingRepository;
    this.cancellationService = cancellationService;
  }

  public void cancelShowAndRefundAll(Long showId) {
    showService.cancel(showId);
    List<Booking> affected =
        bookingRepository.findByShowId(showId).stream()
            .filter(b -> b.getStatus() == BookingStatus.CONFIRMED || b.getStatus() == BookingStatus.PARTIALLY_CANCELLED)
            .toList();
    for (Booking booking : affected) {
      cancellationService.cancelEntireBooking(booking.getId(), booking.getUserId(), "Show cancelled by admin");
    }
  }
}
