package com.cineagent.common.error;

import java.util.Set;
import org.springframework.http.HttpStatus;

/** 404 — the resource does not exist, or (deliberately) is not owned by the caller. */
public class ResourceNotFoundException extends BusinessException {
  public ResourceNotFoundException(ErrorCode errorCode, String detail) {
    super(errorCode, detail, Set.of(HttpStatus.NOT_FOUND));
  }

  public static ResourceNotFoundException of(ErrorCode errorCode, Object identifier) {
    return new ResourceNotFoundException(errorCode, errorCode.getTitle() + ": " + identifier);
  }
}
