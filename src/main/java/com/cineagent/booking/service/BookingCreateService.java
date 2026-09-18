package com.cineagent.booking.service;

import com.cineagent.booking.domain.Booking;
import com.cineagent.booking.domain.BookingCharge;
import com.cineagent.booking.domain.BookingSeat;
import com.cineagent.booking.domain.BookingStatus;
import com.cineagent.booking.domain.BookingStatusHistory;
import com.cineagent.booking.domain.ChargeType;
import com.cineagent.booking.repository.BookingChargeRepository;
import com.cineagent.booking.repository.BookingRepository;
import com.cineagent.booking.repository.BookingSeatRepository;
import com.cineagent.booking.repository.BookingStatusHistoryRepository;
import com.cineagent.catalog.domain.SeatCategory;
import com.cineagent.common.error.ErrorCode;
import com.cineagent.common.error.UnprocessableException;
import com.cineagent.common.money.Money;
import com.cineagent.pricing.domain.DiscountCode;
import com.cineagent.pricing.service.DiscountService;
import com.cineagent.show.domain.Show;
import com.cineagent.show.domain.ShowPrice;
import com.cineagent.show.domain.ShowSeat;
import com.cineagent.show.service.SeatHoldService;
import com.cineagent.show.service.SeatHoldService.HeldSeatsSnapshot;
import com.cineagent.show.service.ShowService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a Booking + itemized charges from an active seat hold. Does NOT charge payment or
 * consume the hold — see {@link BookingPaymentService}: payment is a network call and must never
 * run inside the transaction that holds the seat lock (AGENTS.md §4.6).
 */
@Service
public class BookingCreateService {

  private final SeatHoldService seatHoldService;
  private final ShowService showService;
  private final DiscountService discountService;
  private final BookingRepository bookingRepository;
  private final BookingSeatRepository bookingSeatRepository;
  private final BookingChargeRepository bookingChargeRepository;
  private final BookingStatusHistoryRepository historyRepository;
  private final BookingReferenceGenerator referenceGenerator;
  private final Clock clock;

  public BookingCreateService(
      SeatHoldService seatHoldService,
      ShowService showService,
      DiscountService discountService,
      BookingRepository bookingRepository,
      BookingSeatRepository bookingSeatRepository,
      BookingChargeRepository bookingChargeRepository,
      BookingStatusHistoryRepository historyRepository,
      BookingReferenceGenerator referenceGenerator,
      Clock clock) {
    this.seatHoldService = seatHoldService;
    this.showService = showService;
    this.discountService = discountService;
    this.bookingRepository = bookingRepository;
    this.bookingSeatRepository = bookingSeatRepository;
    this.bookingChargeRepository = bookingChargeRepository;
    this.historyRepository = historyRepository;
    this.referenceGenerator = referenceGenerator;
    this.clock = clock;
  }

  @Transactional
  public Booking create(Long holdId, Long userId, String discountCode) {
    HeldSeatsSnapshot snapshot = seatHoldService.lockOwnedActiveHold(holdId, userId);
    Show show = snapshot.hold().getShow();
    Map<SeatCategory, BigDecimal> prices = new EnumMap<>(SeatCategory.class);
    for (ShowPrice p : showService.getPrices(show.getId())) {
      prices.put(p.getSeatCategory(), p.getBaseAmount());
    }

    List<ShowSeat> seats = snapshot.seats();
    List<BigDecimal> baseAmounts = new ArrayList<>(seats.size());
    BigDecimal subtotal = Money.zero();
    for (ShowSeat seat : seats) {
      SeatCategory category = seat.getSeat().getCategory();
      BigDecimal price = prices.get(category);
      if (price == null) {
        throw new UnprocessableException(
            ErrorCode.SHOW_NOT_BOOKABLE, "No price configured for category " + category + " on show " + show.getId());
      }
      baseAmounts.add(price);
      subtotal = subtotal.add(price);
    }

    DiscountCode discount = null;
    BigDecimal orderDiscount = Money.zero();
    if (discountCode != null && !discountCode.isBlank()) {
      discount = discountService.validate(discountCode, subtotal);
      orderDiscount = discount.computeDiscount(subtotal); // signed negative
    }

    String reference = referenceGenerator.generate();
    BigDecimal total = Money.clamp(subtotal.add(orderDiscount), null);
    Booking booking =
        bookingRepository.save(
            new Booking(
                reference,
                userId,
                show.getId(),
                holdId,
                seats.size(),
                total,
                show.getCurrency(),
                discount == null ? null : discount.getId()));

    List<BookingCharge> charges = new ArrayList<>();
    // Discount allocated pro-rata by each seat's base amount; the LAST seat absorbs the rounding
    // remainder so the itemized lines always sum exactly to orderDiscount (simpler than full
    // largest-remainder, same "sums exactly" guarantee).
    BigDecimal allocatedDiscount = Money.zero();
    for (int i = 0; i < seats.size(); i++) {
      ShowSeat seat = seats.get(i);
      BigDecimal base = baseAmounts.get(i);
      BookingSeat bookingSeat =
          bookingSeatRepository.save(
              new BookingSeat(booking.getId(), seat.getId(), seat.getSeat().getDisplayLabel(), seat.getSeat().getCategory()));
      charges.add(
          new BookingCharge(booking.getId(), bookingSeat.getId(), ChargeType.BASE, "Base fare", base, true));

      if (discount != null) {
        BigDecimal seatShare;
        if (i == seats.size() - 1) {
          seatShare = orderDiscount.subtract(allocatedDiscount);
        } else {
          seatShare = Money.round(orderDiscount.multiply(base).divide(subtotal, 10, java.math.RoundingMode.HALF_UP));
        }
        allocatedDiscount = allocatedDiscount.add(seatShare);
        charges.add(
            new BookingCharge(
                booking.getId(), bookingSeat.getId(), ChargeType.DISCOUNT, "Discount " + discount.getCode(), seatShare, true));
      }
    }
    bookingChargeRepository.saveAll(charges);

    if (discount != null) {
      // AFTER seat locks, per the global lock order (AGENTS.md §4.4).
      discountService.redeem(discount.getId());
    }

    historyRepository.save(
        new BookingStatusHistory(booking.getId(), null, BookingStatus.PENDING_PAYMENT, "Booking created", Instant.now(clock), "system"));

    return booking;
  }
}
