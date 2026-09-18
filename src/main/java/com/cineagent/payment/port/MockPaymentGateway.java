package com.cineagent.payment.port;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Deterministic mock — no real gateway credentials exist for this assignment. Fails
 * deterministically on one magic amount ({@link #FORCED_FAILURE_AMOUNT}) so the payment-decline
 * path and the seat-lost-after-payment compensation path are both demoable without randomness.
 */
@Component
public class MockPaymentGateway implements PaymentGateway {

  public static final BigDecimal FORCED_FAILURE_AMOUNT = new BigDecimal("13.13");

  @Override
  public PaymentResult charge(String idempotencyKey, BigDecimal amount, String currency) {
    if (amount.compareTo(FORCED_FAILURE_AMOUNT) == 0) {
      return new PaymentResult(false, null, "Card declined (simulated)");
    }
    return new PaymentResult(true, "mock-charge-" + UUID.randomUUID(), null);
  }

  @Override
  public PaymentResult refund(String originalGatewayReference, BigDecimal amount) {
    return new PaymentResult(true, "mock-refund-" + UUID.randomUUID(), null);
  }
}
