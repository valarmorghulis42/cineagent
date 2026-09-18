package com.cineagent.booking.service;

import com.cineagent.booking.domain.Booking;
import com.cineagent.booking.domain.BookingStatus;
import com.cineagent.booking.domain.BookingStatusHistory;
import com.cineagent.booking.repository.BookingRepository;
import com.cineagent.booking.repository.BookingStatusHistoryRepository;
import com.cineagent.common.error.ConflictException;
import com.cineagent.common.error.ErrorCode;
import com.cineagent.common.error.PaymentFailedException;
import com.cineagent.common.error.ResourceNotFoundException;
import com.cineagent.common.event.NotificationRequested;
import com.cineagent.payment.domain.Payment;
import com.cineagent.payment.domain.PaymentStatus;
import com.cineagent.payment.port.PaymentGateway;
import com.cineagent.payment.port.PaymentGateway.PaymentResult;
import com.cineagent.payment.repository.PaymentRepository;
import com.cineagent.refund.service.RefundService;
import com.cineagent.show.service.SeatHoldService;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The payment orchestration described in AGENTS.md §4.6: the gateway call happens strictly
 * BETWEEN two short transactions, never inside one holding a seat lock. Deliberately NOT
 * {@code @Transactional} itself — each phase runs its own transaction via {@link
 * TransactionTemplate} (self-invocation of a same-class {@code @Transactional} method would
 * silently bypass the proxy and not actually open a new transaction).
 */
@Service
public class BookingPaymentService {

  private final BookingRepository bookingRepository;
  private final BookingStatusHistoryRepository historyRepository;
  private final PaymentRepository paymentRepository;
  private final SeatHoldService seatHoldService;
  private final PaymentGateway paymentGateway;
  private final RefundService refundService;
  private final ApplicationEventPublisher eventPublisher;
  private final TransactionTemplate txTemplate;
  private final Clock clock;

  public BookingPaymentService(
      BookingRepository bookingRepository,
      BookingStatusHistoryRepository historyRepository,
      PaymentRepository paymentRepository,
      SeatHoldService seatHoldService,
      PaymentGateway paymentGateway,
      RefundService refundService,
      ApplicationEventPublisher eventPublisher,
      PlatformTransactionManager transactionManager,
      Clock clock) {
    this.bookingRepository = bookingRepository;
    this.historyRepository = historyRepository;
    this.paymentRepository = paymentRepository;
    this.seatHoldService = seatHoldService;
    this.paymentGateway = paymentGateway;
    this.refundService = refundService;
    this.eventPublisher = eventPublisher;
    this.txTemplate = new TransactionTemplate(transactionManager);
    this.clock = clock;
  }

  public Booking pay(Long bookingId, Long userId) {
    Booking booking =
        txTemplate.execute(
            status -> {
              Booking b = getOwnedPendingBooking(bookingId, userId);
              // Pre-check before spending effort on the gateway call — still cheap to fail fast
              // here if the hold is already visibly gone.
              seatHoldService.lockOwnedActiveHold(b.getHoldId(), userId);
              return b;
            });

    PaymentResult chargeResult =
        paymentGateway.charge("booking-" + bookingId, booking.getTotalAmount(), booking.getCurrency());

    if (!chargeResult.success()) {
      // Persist the failure record first (committed), THEN throw -- throwing from inside the
      // TransactionTemplate callback would roll back the very failure record we need to keep.
      txTemplate.execute(status -> recordPaymentFailure(bookingId, chargeResult));
      throw new PaymentFailedException(
          ErrorCode.PAYMENT_FAILED,
          chargeResult.failureReason() == null ? "Payment declined" : chargeResult.failureReason());
    }

    boolean stillHeld = seatHoldService.seatsStillHeldByHold(booking.getHoldId());
    if (stillHeld) {
      return txTemplate.execute(status -> recordConfirmed(bookingId, chargeResult));
    }

    // Seat lost after a successful charge — the risk AGENTS.md §4.6 calls out explicitly.
    // Refund immediately, OUTSIDE any transaction (this is itself a network call).
    PaymentResult refundResult = paymentGateway.refund(chargeResult.gatewayReference(), booking.getTotalAmount());
    Booking result = txTemplate.execute(status -> recordSeatLostCompensation(bookingId, chargeResult, refundResult));
    ConflictException ex =
        new ConflictException(
            ErrorCode.SEAT_LOST_AFTER_PAYMENT,
            "Your seats were lost while payment was processing; a full refund was issued");
    ex.withProperty("refundReference", refundResult.gatewayReference());
    throw ex;
  }

  private Booking recordPaymentFailure(Long bookingId, PaymentResult result) {
    Booking booking = bookingRepository.findById(bookingId).orElseThrow();
    paymentRepository.save(
        new Payment(bookingId, booking.getTotalAmount(), booking.getCurrency(), PaymentStatus.FAILED, "failed"));
    transition(booking, BookingStatus.PAYMENT_FAILED, "Payment declined: " + result.failureReason());
    return booking;
  }

  private Booking recordConfirmed(Long bookingId, PaymentResult chargeResult) {
    Booking booking = bookingRepository.findById(bookingId).orElseThrow();
    paymentRepository.save(
        new Payment(
            bookingId, booking.getTotalAmount(), booking.getCurrency(), PaymentStatus.SUCCEEDED, chargeResult.gatewayReference()));
    seatHoldService.confirmHoldAsBooked(booking.getHoldId(), bookingId);
    transition(booking, BookingStatus.CONFIRMED, "Payment succeeded");
    eventPublisher.publishEvent(
        new NotificationRequested(
            "BOOKING_CONFIRMED",
            "BOOKING_CONFIRMED:" + bookingId,
            Map.of(
                "bookingId", String.valueOf(bookingId),
                "bookingReference", booking.getBookingReference(),
                "userId", String.valueOf(booking.getUserId()),
                "amount", booking.getTotalAmount().toString())));
    return booking;
  }

  private Booking recordSeatLostCompensation(Long bookingId, PaymentResult chargeResult, PaymentResult refundResult) {
    Booking booking = bookingRepository.findById(bookingId).orElseThrow();
    Payment payment =
        paymentRepository.save(
            new Payment(
                bookingId, booking.getTotalAmount(), booking.getCurrency(), PaymentStatus.SUCCEEDED, chargeResult.gatewayReference()));
    payment.markReversed();
    refundService.issueRefund(
        bookingId, payment.getId(), chargeResult.gatewayReference(), booking.getTotalAmount(), "Seats lost after payment");
    transition(booking, BookingStatus.SEAT_LOST_AFTER_PAYMENT, "Seats lost while payment was in flight; refunded");
    return booking;
  }

  private void transition(Booking booking, BookingStatus target, String reason) {
    BookingStatus from = booking.getStatus();
    booking.transitionTo(target);
    historyRepository.save(new BookingStatusHistory(booking.getId(), from, target, reason, Instant.now(clock), "system"));
  }

  private Booking getOwnedPendingBooking(Long bookingId, Long userId) {
    Booking booking =
        bookingRepository.findById(bookingId).orElseThrow(() -> ResourceNotFoundException.of(ErrorCode.BOOKING_NOT_FOUND, bookingId));
    if (!booking.getUserId().equals(userId)) {
      throw ResourceNotFoundException.of(ErrorCode.BOOKING_NOT_FOUND, bookingId);
    }
    if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
      throw new ConflictException(ErrorCode.INVALID_BOOKING_STATE, "Booking " + bookingId + " is not awaiting payment");
    }
    return booking;
  }
}
