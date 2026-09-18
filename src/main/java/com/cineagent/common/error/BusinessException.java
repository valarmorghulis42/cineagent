package com.cineagent.common.error;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;

/**
 * Base type for every domain error in the system. Carries an {@link ErrorCode} (which supplies
 * the default HTTP status and title) plus optional extension properties that {@link
 * GlobalExceptionHandler} copies onto the {@link org.springframework.http.ProblemDetail}
 * response (e.g. {@code conflictingSeats}).
 *
 * <p>Concrete subtypes exist per HTTP-status family — {@link ResourceNotFoundException} (404),
 * {@link ConflictException} (409), {@link UnprocessableException} (422), {@link
 * PaymentFailedException} (402), {@link UnauthorizedException} (401). Do not add a sixth subtype
 * without updating the {@code api-contract} skill's status taxonomy.
 *
 * <p><b>The subtype ↔ status invariant is enforced here, not just by convention.</b> {@link
 * GlobalExceptionHandler} derives the HTTP status entirely from {@code errorCode.getDefaultStatus()}
 * — the subtype is otherwise decorative, which is exactly what let three real mismatches ship
 * silently (a genuine 409 returning 404, a 401 built via the wrong subtype, one of them echoing
 * user input into the response). Every subtype now declares the status family it's allowed to
 * carry, and construction fails fast if an {@link ErrorCode} from the wrong family is passed to
 * it — a mismatch is now a startup-time/test-time failure, not a silent wrong response.
 */
public abstract class BusinessException extends RuntimeException {

  private final ErrorCode errorCode;
  private final Map<String, Object> properties = new LinkedHashMap<>();

  protected BusinessException(ErrorCode errorCode, String detail, Set<HttpStatus> allowedStatuses) {
    super(detail);
    if (!allowedStatuses.contains(errorCode.getDefaultStatus())) {
      throw new IllegalArgumentException(
          "%s carries status %s via %s, but %s only accepts %s — use a different exception subtype"
              .formatted(
                  errorCode,
                  errorCode.getDefaultStatus(),
                  getClass().getSimpleName(),
                  getClass().getSimpleName(),
                  allowedStatuses));
    }
    this.errorCode = errorCode;
  }

  public BusinessException withProperty(String key, Object value) {
    properties.put(key, value);
    return this;
  }

  public ErrorCode getErrorCode() {
    return errorCode;
  }

  public Map<String, Object> getProperties() {
    return properties;
  }
}
