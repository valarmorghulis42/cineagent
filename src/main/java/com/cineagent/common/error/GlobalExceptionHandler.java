package com.cineagent.common.error;

import com.cineagent.common.web.RequestTraceFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * The single error-handling seam for the whole API. Extends {@link
 * ResponseEntityExceptionHandler} so Spring's own exceptions (bean validation failures,
 * malformed JSON, unmapped routes, wrong HTTP verb) come back in the *same* {@link ProblemDetail}
 * shape as our own {@link BusinessException} hierarchy — see the {@code api-contract} skill.
 *
 * <p>Never add a second error envelope anywhere else in the codebase.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
  private static final URI PROBLEM_BASE = URI.create("https://cineagent.example/problems/");

  private final Clock clock;

  public GlobalExceptionHandler(Clock clock) {
    this.clock = clock;
  }

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ProblemDetail> handleBusiness(
      BusinessException ex, HttpServletRequest request) {
    ErrorCode code = ex.getErrorCode();
    ProblemDetail pd = baseProblem(code.getDefaultStatus(), code, ex.getMessage(), request);
    ex.getProperties().forEach(pd::setProperty);
    if (code.getDefaultStatus().is5xxServerError()) {
      log.error("Business error [{}] on {} {}", code, request.getMethod(), request.getRequestURI(), ex);
    } else {
      log.warn("Business error [{}] on {} {}: {}", code, request.getMethod(), request.getRequestURI(), ex.getMessage());
    }
    return ResponseEntity.status(code.getDefaultStatus()).body(pd);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ProblemDetail> handleDataIntegrity(
      DataIntegrityViolationException ex, HttpServletRequest request) {
    // A unique-constraint violation reaching here means a race was lost at the DB level after
    // application-level checks passed (e.g. a booking_reference collision, or a discount cap
    // race that slipped past the conditional UPDATE). Map generically to 409 — specific call
    // sites should catch this and retry/translate to a more precise ErrorCode where meaningful.
    log.warn("Data integrity violation on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
    ProblemDetail pd =
        baseProblem(HttpStatus.CONFLICT, ErrorCode.VALIDATION_FAILED,
            "The request conflicts with existing data.", request);
    return ResponseEntity.status(HttpStatus.CONFLICT).body(pd);
  }

  /**
   * {@code ObjectOptimisticLockingFailureException} is what fires when the {@code @Version}
   * backstop on {@code ShowSeat} catches a write that skipped the pessimistic lock (AGENTS.md
   * §4.5). Without this handler it falls through to {@link #handleUnexpected} as a 500 — which
   * would mean the one moment the backstop actually does its job, it produces a server error
   * instead of the same clean 409 a normal lock-contention loss gets. Same ErrorCode as the
   * pessimistic path deliberately: from the client's perspective both are "you lost the seat."
   */
  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  public ResponseEntity<ProblemDetail> handleOptimisticLockFailure(
      ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
    log.warn(
        "Optimistic lock backstop caught a concurrent write on {} {} (persistentClass={})",
        request.getMethod(), request.getRequestURI(), ex.getPersistentClassName());
    ProblemDetail pd =
        baseProblem(
            ErrorCode.SEAT_UNAVAILABLE.getDefaultStatus(),
            ErrorCode.SEAT_UNAVAILABLE,
            "The seat was claimed by another request while this one was in flight",
            request);
    return ResponseEntity.status(ErrorCode.SEAT_UNAVAILABLE.getDefaultStatus()).body(pd);
  }

  /**
   * A distinct handler and log line for deadlock losses specifically — {@code
   * DeadlockLoserDataAccessException} extends {@link PessimisticLockingFailureException}, so
   * without this it would be caught by {@link #handleLockTimeout} and logged identically to
   * ordinary lock-wait contention. Since lock ordering (AGENTS.md §4.4) is a claim this project
   * makes explicitly and demonstrates on camera by deliberately breaking it, a deadlock loss
   * needs to be distinguishable in the logs from a normal, expected contention loss.
   */
  @ExceptionHandler(DeadlockLoserDataAccessException.class)
  public ResponseEntity<ProblemDetail> handleDeadlock(
      DeadlockLoserDataAccessException ex, HttpServletRequest request) {
    log.warn(
        "DEADLOCK detected and this transaction was the loser on {} {} — check lock ordering "
            + "(AGENTS.md §4.4) if this is not from the deliberate lock-ordering demo",
        request.getMethod(), request.getRequestURI());
    ProblemDetail pd =
        baseProblem(
            ErrorCode.SEAT_CONTENTION_TIMEOUT.getDefaultStatus(),
            ErrorCode.SEAT_CONTENTION_TIMEOUT,
            "Could not acquire seats due to a lock conflict, please retry",
            request);
    return ResponseEntity.status(ErrorCode.SEAT_CONTENTION_TIMEOUT.getDefaultStatus()).body(pd);
  }

  @ExceptionHandler(PessimisticLockingFailureException.class)
  public ResponseEntity<ProblemDetail> handleLockTimeout(
      PessimisticLockingFailureException ex, HttpServletRequest request) {
    log.warn("Lock acquisition timed out on {} {}", request.getMethod(), request.getRequestURI());
    ProblemDetail pd =
        baseProblem(
            ErrorCode.SEAT_CONTENTION_TIMEOUT.getDefaultStatus(),
            ErrorCode.SEAT_CONTENTION_TIMEOUT,
            ErrorCode.SEAT_CONTENTION_TIMEOUT.getTitle(),
            request);
    return ResponseEntity.status(ErrorCode.SEAT_CONTENTION_TIMEOUT.getDefaultStatus()).body(pd);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
    log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
    ProblemDetail pd =
        baseProblem(
            HttpStatus.INTERNAL_SERVER_ERROR,
            ErrorCode.INTERNAL_ERROR,
            "An unexpected error occurred.",
            request);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(pd);
  }

  /**
   * Routes every framework-level exception (bean validation, malformed body, 404, 405, ...)
   * through the same ProblemDetail enrichment as our own exceptions, instead of Spring's default
   * body.
   */
  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
    ProblemDetail pd =
        (body instanceof ProblemDetail existing)
            ? existing
            : ProblemDetail.forStatus(statusCode);
    pd.setTitle(pd.getTitle() != null ? pd.getTitle() : "Request could not be processed");
    enrich(pd, statusCode.value() == 400 ? ErrorCode.VALIDATION_FAILED : ErrorCode.INTERNAL_ERROR);
    log.warn("Framework exception mapped to {}: {}", statusCode, ex.getMessage());
    return ResponseEntity.status(statusCode).headers(headers).body(pd);
  }

  private ProblemDetail baseProblem(
      HttpStatus status, ErrorCode code, String detail, HttpServletRequest request) {
    ProblemDetail pd = ProblemDetail.forStatus(status);
    pd.setTitle(code.getTitle());
    pd.setDetail(detail);
    pd.setType(PROBLEM_BASE.resolve(code.name().toLowerCase().replace('_', '-')));
    pd.setInstance(URI.create(request.getRequestURI()));
    enrich(pd, code);
    return pd;
  }

  private void enrich(ProblemDetail pd, ErrorCode code) {
    pd.setProperty("code", code.name());
    pd.setProperty("traceId", MDC.get(RequestTraceFilter.TRACE_ID_MDC_KEY));
    pd.setProperty("timestamp", Instant.now(clock));
  }
}
