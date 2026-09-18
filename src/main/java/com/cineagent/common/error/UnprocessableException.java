package com.cineagent.common.error;

import java.util.Set;
import org.springframework.http.HttpStatus;

/**
 * 422 — the request is well-formed and internally consistent, but rejected by a business rule:
 * an expired discount code, a refund window that has passed, etc.
 */
public class UnprocessableException extends BusinessException {
  public UnprocessableException(ErrorCode errorCode, String detail) {
    super(errorCode, detail, Set.of(HttpStatus.UNPROCESSABLE_ENTITY));
  }
}
