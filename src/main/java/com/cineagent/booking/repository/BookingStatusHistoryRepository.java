package com.cineagent.booking.repository;

import com.cineagent.booking.domain.BookingStatusHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingStatusHistoryRepository extends JpaRepository<BookingStatusHistory, Long> {
  List<BookingStatusHistory> findByBookingIdOrderByChangedAtAsc(Long bookingId);
}
