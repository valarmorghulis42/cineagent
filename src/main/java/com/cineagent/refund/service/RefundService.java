package com.cineagent.refund.service;

import com.cineagent.payment.port.PaymentGateway;
import com.cineagent.refund.domain.Refund;
import com.cineagent.refund.domain.RefundStatus;
import com.cineagent.refund.repository.RefundRepository;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deliberately booking-agnostic (AGENTS.md dependency rule: refund does not depend on booking) —
 * callers compute the refundable amount themselves (from their own itemized data) and pass it in.
 * The gateway call happens here, outside any seat/row lock the caller might otherwise be holding.
 */
@Service
public class RefundService {

  private final RefundRepository refundRepository;
  private final PaymentGateway paymentGateway;

  public RefundService(RefundRepository refundRepository, PaymentGateway paymentGateway) {
    this.refundRepository = refundRepository;
    this.paymentGateway = paymentGateway;
  }

  /** Calls the gateway and persists the Refund row in one method — callers must not be holding
   * any lock when they call this. */
  @Transactional
  public Refund issueRefund(
      Long bookingId, Long paymentId, String originalGatewayReference, BigDecimal amount, String reason) {
    if (amount.compareTo(BigDecimal.ZERO) <= 0) {
      return refundRepository.save(
          new Refund(bookingId, paymentId, BigDecimal.ZERO, reason, RefundStatus.COMPLETED, "no-op-zero-amount"));
    }
    PaymentGateway.PaymentResult result = paymentGateway.refund(originalGatewayReference, amount);
    RefundStatus status = result.success() ? RefundStatus.COMPLETED : RefundStatus.FAILED;
    String reference = result.success() ? result.gatewayReference() : "failed";
    return refundRepository.save(new Refund(bookingId, paymentId, amount, reason, status, reference));
  }
}
