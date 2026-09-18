package com.cineagent.payment.repository;

import com.cineagent.payment.domain.Payment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
  Optional<Payment> findByBookingId(Long bookingId);
}
