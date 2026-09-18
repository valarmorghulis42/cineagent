package com.cineagent.booking.repository;

import com.cineagent.booking.domain.BookingSeat;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingSeatRepository extends JpaRepository<BookingSeat, Long> {
  List<BookingSeat> findByBookingId(Long bookingId);
}
