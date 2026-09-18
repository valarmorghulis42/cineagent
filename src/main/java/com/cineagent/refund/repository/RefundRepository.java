package com.cineagent.refund.repository;

import com.cineagent.refund.domain.Refund;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundRepository extends JpaRepository<Refund, Long> {
  List<Refund> findByBookingId(Long bookingId);
}
