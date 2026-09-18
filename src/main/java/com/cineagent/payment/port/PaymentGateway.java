package com.cineagent.payment.port;

import java.math.BigDecimal;

/**
 * The one real port in this codebase besides {@code NotificationSender} — a second
 * implementation (a real gateway) is the whole reason to keep this abstract. Called strictly
 * BETWEEN transactions, never while holding a seat lock (AGENTS.md §4.6).
 */
public interface PaymentGateway {

  PaymentResult charge(String idempotencyKey, BigDecimal amount, String currency);

  PaymentResult refund(String originalGatewayReference, BigDecimal amount);

  record PaymentResult(boolean success, String gatewayReference, String failureReason) {}
}
