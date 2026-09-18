package com.cineagent.booking.service;

import com.cineagent.booking.domain.Booking;
import com.cineagent.booking.domain.BookingCharge;
import com.cineagent.booking.domain.BookingSeat;
import com.cineagent.booking.domain.BookingSeatStatus;
import com.cineagent.booking.domain.BookingStatus;
import com.cineagent.booking.domain.BookingStatusHistory;
import com.cineagent.booking.repository.BookingChargeRepository;
import com.cineagent.booking.repository.BookingRepository;
import com.cineagent.booking.repository.BookingSeatRepository;
import com.cineagent.booking.repository.BookingStatusHistoryRepository;
import com.cineagent.common.error.ConflictException;
import com.cineagent.common.error.ErrorCode;
import com.cineagent.common.error.ResourceNotFoundException;
import com.cineagent.common.error.UnprocessableException;
import com.cineagent.common.event.NotificationRequested;
import com.cineagent.common.money.Money;
import com.cineagent.payment.domain.Payment;
import com.cineagent.payment.repository.PaymentRepository;
import com.cineagent.refund.service.RefundPolicyResolver;
import com.cineagent.refund.service.RefundService;
import com.cineagent.show.domain.Show;
import com.cineagent.show.service.SeatHoldService;
import com.cineagent.show.service.ShowService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Partial or full per-seat cancellation with pro-rata refund. Resolves the "book and cancel
 * seats" literal-plural requirement (AGENTS.md/plan): cancelling a subset of a booking's seats is
 * a first-class operation, not just whole-booking cancellation.
 *
 * <p>Same two-phase shape as {@link BookingPaymentService}: everything requiring a row lock
 * (releasing show_seat rows, computing the refund amount from booking_charge) happens in one
 * short transaction that commits BEFORE the refund's gateway call — never during it.
 */
@Service
public class BookingCancellationService {

  private final BookingRepository bookingRepository;
  private final BookingSeatRepository bookingSeatRepository;
  private final BookingChargeRepository bookingChargeRepository;
  private final BookingStatusHistoryRepository historyRepository;
  private final PaymentRepository paymentRepository;
  private final SeatHoldService seatHoldService;
  private final ShowService showService;
  private final RefundPolicyResolver refundPolicyResolver;
  private final RefundService refundService;
  private final ApplicationEventPublisher eventPublisher;
  private final TransactionTemplate txTemplate;
  private final Clock clock;

  public BookingCancellationService(
      BookingRepository bookingRepository,
      BookingSeatRepository bookingSeatRepository,
      BookingChargeRepository bookingChargeRepository,
      BookingStatusHistoryRepository historyRepository,
      PaymentRepository paymentRepository,
      SeatHoldService seatHoldService,
      ShowService showService,
      RefundPolicyResolver refundPolicyResolver,
      RefundService refundService,
      ApplicationEventPublisher eventPublisher,
      PlatformTransactionManager transactionManager,
      Clock clock) {
    this.bookingRepository = bookingRepository;
    this.bookingSeatRepository = bookingSeatRepository;
    this.bookingChargeRepository = bookingChargeRepository;
    this.historyRepository = historyRepository;
    this.paymentRepository = paymentRepository;
    this.seatHoldService = seatHoldService;
    this.showService = showService;
    this.refundPolicyResolver = refundPolicyResolver;
    this.refundService = refundService;
    this.eventPublisher = eventPublisher;
    this.txTemplate = new TransactionTemplate(transactionManager);
    this.clock = clock;
  }

  public Booking cancelSeats(Long bookingId, Long userId, List<Long> bookingSeatIds, String reason) {
    CancellationPrep prep = txTemplate.execute(status -> prepareCancellation(bookingId, userId, bookingSeatIds, reason));
    refundService.issueRefund(bookingId, prep.paymentId(), prep.gatewayReference(), prep.refundAmount(), reason);
    return bookingRepository.findById(bookingId).orElseThrow();
  }

  public Booking cancelEntireBooking(Long bookingId, Long userId, String reason) {
    List<Long> activeSeatIds =
        bookingSeatRepository.findByBookingId(bookingId).stream()
            .filter(s -> s.getStatus() == BookingSeatStatus.ACTIVE)
            .map(BookingSeat::getId)
            .toList();
    return cancelSeats(bookingId, userId, activeSeatIds, reason);
  }

  private CancellationPrep prepareCancellation(Long bookingId, Long userId, List<Long> bookingSeatIds, String reason) {
    if (bookingSeatIds.isEmpty()) {
      throw new UnprocessableException(ErrorCode.SEAT_NOT_IN_BOOKING, "No seats specified to cancel");
    }
    Booking booking =
        bookingRepository.findById(bookingId).orElseThrow(() -> ResourceNotFoundException.of(ErrorCode.BOOKING_NOT_FOUND, bookingId));
    if (!booking.getUserId().equals(userId)) {
      throw ResourceNotFoundException.of(ErrorCode.BOOKING_NOT_FOUND, bookingId);
    }
    if (booking.getStatus() != BookingStatus.CONFIRMED && booking.getStatus() != BookingStatus.PARTIALLY_CANCELLED) {
      throw new ConflictException(ErrorCode.INVALID_BOOKING_STATE, "Booking " + bookingId + " is not eligible for cancellation");
    }

    List<BookingSeat> targetSeats = bookingSeatRepository.findByBookingId(bookingId).stream()
        .filter(s -> bookingSeatIds.contains(s.getId()))
        .toList();
    if (targetSeats.size() != bookingSeatIds.size() || targetSeats.stream().anyMatch(s -> s.getStatus() != BookingSeatStatus.ACTIVE)) {
      throw new UnprocessableException(ErrorCode.SEAT_NOT_IN_BOOKING, "One or more seats are not active on this booking");
    }

    Payment payment =
        paymentRepository.findByBookingId(bookingId).orElseThrow(() -> ResourceNotFoundException.of(ErrorCode.BOOKING_NOT_FOUND, bookingId));

    Show show = showService.getShow(booking.getShowId());
    long minutesBeforeShow = Duration.between(Instant.now(clock), show.getStartsAt()).toMinutes();
    BigDecimal percentage =
        refundPolicyResolver.resolvePercentage(
            show.getId(), show.getScreen().getTheater().getId(), show.getCity().getId(), minutesBeforeShow);

    Set<Long> targetIds = targetSeats.stream().map(BookingSeat::getId).collect(java.util.stream.Collectors.toSet());
    BigDecimal refundableSum =
        bookingChargeRepository.findByBookingSeatIdIn(targetSeats.stream().map(BookingSeat::getId).toList()).stream()
            .filter(BookingCharge::isRefundable)
            .filter(c -> targetIds.contains(c.getBookingSeatId()))
            .map(BookingCharge::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal refundAmount = Money.clamp(Money.round(refundableSum.multiply(percentage).divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)), null);

    targetSeats.forEach(BookingSeat::cancel);
    seatHoldService.releaseBookedSeats(targetSeats.stream().map(BookingSeat::getShowSeatId).toList());

    booking.reduceSeatCount(targetSeats.size());
    BookingStatus target = booking.getSeatCount() <= 0 ? BookingStatus.CANCELLED : BookingStatus.PARTIALLY_CANCELLED;
    BookingStatus from = booking.getStatus();
    booking.transitionTo(target);
    historyRepository.save(new BookingStatusHistory(bookingId, from, target, reason, Instant.now(clock), "system"));

    eventPublisher.publishEvent(
        new NotificationRequested(
            "BOOKING_" + target,
            "BOOKING_" + target + ":" + bookingId + ":" + Instant.now(clock).toEpochMilli(),
            Map.of(
                "bookingId", String.valueOf(bookingId),
                "bookingReference", booking.getBookingReference(),
                "refundAmount", refundAmount.toString())));

    return new CancellationPrep(payment.getId(), payment.getGatewayReference(), refundAmount);
  }

  private record CancellationPrep(Long paymentId, String gatewayReference, BigDecimal refundAmount) {}
}
