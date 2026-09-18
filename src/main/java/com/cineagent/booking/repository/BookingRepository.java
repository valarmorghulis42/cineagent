package com.cineagent.booking.repository;

import com.cineagent.booking.domain.Booking;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Long> {

  boolean existsByBookingReference(String bookingReference);

  @Query("select b from Booking b where b.userId = :userId order by b.id desc")
  Page<Booking> findByUserId(@Param("userId") Long userId, Pageable pageable);

  List<Booking> findByShowId(Long showId);
}
