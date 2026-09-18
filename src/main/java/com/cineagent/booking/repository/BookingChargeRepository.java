package com.cineagent.booking.repository;

import com.cineagent.booking.domain.BookingCharge;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingChargeRepository extends JpaRepository<BookingCharge, Long> {
  List<BookingCharge> findByBookingId(Long bookingId);

  List<BookingCharge> findByBookingSeatIdIn(List<Long> bookingSeatIds);
}
