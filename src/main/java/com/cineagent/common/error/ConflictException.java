package com.cineagent.common.error;

import java.util.Set;
import org.springframework.http.HttpStatus;

/**
 * 409 — the request is well-formed but conflicts with the current state of the resource: a seat
 * was taken, a hold expired, an illegal state transition was attempted, or a concurrency loss
 * occurred. See the {@code api-contract} skill's status taxonomy.
 */
public class ConflictException extends BusinessException {
  public ConflictException(ErrorCode errorCode, String detail) {
    super(errorCode, detail, Set.of(HttpStatus.CONFLICT));
  }
}
