package com.cineagent.common.error;

import java.util.Set;
import org.springframework.http.HttpStatus;

/** 402 — the payment gateway declined the charge. */
public class PaymentFailedException extends BusinessException {
  public PaymentFailedException(ErrorCode errorCode, String detail) {
    super(errorCode, detail, Set.of(HttpStatus.PAYMENT_REQUIRED));
  }
}
