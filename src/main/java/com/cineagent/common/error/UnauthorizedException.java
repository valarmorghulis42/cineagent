package com.cineagent.common.error;

import java.util.Set;
import org.springframework.http.HttpStatus;

/**
 * 401 — a business-logic authentication failure (e.g. wrong password at login), as distinct from
 * a filter-level authentication failure (missing/invalid JWT), which Spring Security's own entry
 * point handles before a request ever reaches a controller.
 */
public class UnauthorizedException extends BusinessException {
  public UnauthorizedException(ErrorCode errorCode, String detail) {
    super(errorCode, detail, Set.of(HttpStatus.UNAUTHORIZED));
  }
}
