package com.cineagent.booking.service;

import com.cineagent.booking.domain.Booking;
import com.cineagent.booking.domain.BookingCharge;
import com.cineagent.booking.domain.BookingSeat;
import com.cineagent.booking.domain.BookingStatusHistory;
import com.cineagent.booking.repository.BookingChargeRepository;
import com.cineagent.booking.repository.BookingRepository;
import com.cineagent.booking.repository.BookingSeatRepository;
import com.cineagent.booking.repository.BookingStatusHistoryRepository;
import com.cineagent.common.error.ErrorCode;
import com.cineagent.common.error.ResourceNotFoundException;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingQueryService {

  private final BookingRepository bookingRepository;
  private final BookingSeatRepository bookingSeatRepository;
  private final BookingChargeRepository bookingChargeRepository;
  private final BookingStatusHistoryRepository historyRepository;

  public BookingQueryService(
      BookingRepository bookingRepository,
      BookingSeatRepository bookingSeatRepository,
      BookingChargeRepository bookingChargeRepository,
      BookingStatusHistoryRepository historyRepository) {
    this.bookingRepository = bookingRepository;
    this.bookingSeatRepository = bookingSeatRepository;
    this.bookingChargeRepository = bookingChargeRepository;
    this.historyRepository = historyRepository;
  }

  @Transactional(readOnly = true)
  public Booking getOwned(Long bookingId, Long userId) {
    Booking booking =
        bookingRepository.findById(bookingId).orElseThrow(() -> ResourceNotFoundException.of(ErrorCode.BOOKING_NOT_FOUND, bookingId));
    if (!booking.getUserId().equals(userId)) {
      throw ResourceNotFoundException.of(ErrorCode.BOOKING_NOT_FOUND, bookingId);
    }
    return booking;
  }

  @Transactional(readOnly = true)
  public Page<Booking> listMine(Long userId, Pageable pageable) {
    return bookingRepository.findByUserId(userId, pageable);
  }

  @Transactional(readOnly = true)
  public List<BookingSeat> getSeats(Long bookingId) {
    return bookingSeatRepository.findByBookingId(bookingId);
  }

  @Transactional(readOnly = true)
  public List<BookingCharge> getCharges(Long bookingId) {
    return bookingChargeRepository.findByBookingId(bookingId);
  }

  @Transactional(readOnly = true)
  public List<BookingStatusHistory> getHistory(Long bookingId) {
    return historyRepository.findByBookingIdOrderByChangedAtAsc(bookingId);
  }
}
