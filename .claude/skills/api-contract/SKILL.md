---
name: api-contract
description: Use before adding a new REST endpoint, a new exception/error type, or any pagination or idempotency handling. Covers the ProblemDetail error shape, the ErrorCode registry pattern, the HTTP status taxonomy, pagination conventions, and idempotency key handling. Read before writing a controller method or a new BusinessException subtype.
---

# API contract — CineAgent

This is the detailed reference behind `AGENTS.md` §5–6. Every endpoint in this codebase must
conform to it — consistency here is what lets a reviewer probe the API (malformed JSON, a
double-submitted POST, another user's booking reference) and get a coherent answer every time
instead of three different error shapes.

## Error shape: RFC 7807 ProblemDetail, always

One `@RestControllerAdvice` (`common.error.GlobalExceptionHandler`) extends Spring's
`ResponseEntityExceptionHandler`, so framework-level failures (bean validation,
`HttpMessageNotReadableException`, 404 on an unmapped route, 405 on a wrong verb) come back in the
*same* shape as our own business exceptions. Never catch-and-wrap into a bespoke `{success, data,
error}` envelope anywhere.

```json
{
  "type": "https://cineagent.example/problems/seat-unavailable",
  "title": "One or more selected seats are no longer available",
  "status": 409,
  "detail": "2 of 4 requested seats were taken by another booking.",
  "instance": "/api/v1/shows/1042/holds",
  "code": "SEAT_UNAVAILABLE",
  "traceId": "8f2c1a...",
  "timestamp": "2026-09-18T11:04:22Z"
}
```

Extra fields beyond the RFC-7807 baseline (`code`, `traceId`, `timestamp`, and any
situation-specific payload like `conflictingSeats`) are `ProblemDetail` properties, added via
`setProperty(...)`, not new top-level response shapes.

## The ErrorCode registry pattern

`common.error.ErrorCode` is a single enum: `code` (machine-readable), default `HttpStatus`,
`title`. Every `BusinessException` subtype carries one `ErrorCode`. Adding a new error condition
is:

1. One new `ErrorCode` constant.
2. A throw site using it (`throw new ConflictException(ErrorCode.SEAT_UNAVAILABLE, detail, props)`).

Never add a `switch` over exception class in the handler — the handler maps generically from
`BusinessException.getErrorCode()`. If you find yourself writing a new `if (ex instanceof ...)`
branch in `GlobalExceptionHandler`, stop — that's a sign the exception should carry its own
`ErrorCode` instead.

`BusinessException` subtypes, by HTTP status family (add new ones to the matching subtype, don't
invent a fifth):
- `ResourceNotFoundException` → 404
- `ConflictException` → 409
- `UnprocessableException` → 422
- `PaymentFailedException` → 402

## Status code taxonomy — the policy, verbatim from `AGENTS.md`

| Status | When |
|---|---|
| 400 | Syntactically malformed, or a bean-validation failure — the request could never have been valid |
| 401 / 403 | Not authenticated / authenticated but not permitted |
| 404 | Not found, **or** not owned by the requesting user — never 403 an ownership mismatch, that confirms the resource exists and turns the id into an enumeration oracle |
| 402 | Payment declined by the gateway |
| 409 | Well-formed, but conflicts with current state: seat taken, hold expired, illegal state transition, a concurrency loss |
| 422 | Well-formed and internally consistent, but rejected by a business rule: expired discount code, refund window has passed |

`HOLD_EXPIRED` is 409, not 410 — keeping the whole "conflicts with current state" family on one
status code with distinct `code` values is more useful to a client than splitting hairs between
409 and 410.

## Pagination

- Every paginated endpoint returns `PageResponse<T>` (`common.web.PageResponse`) — never
  serialize Spring Data's `Page<T>` directly; its JSON shape isn't a stable contract.
- Every paginated endpoint has a **maximum page size** enforced server-side (reject or clamp
  `size` above a documented cap — do not let a client request `size=100000`).
- Default sort is deterministic (e.g. `createdAt DESC, id DESC`) — never rely on unspecified
  natural order for a paginated list.

## Idempotency

- `Idempotency-Key` header is **required** on `POST /bookings` and
  `POST /bookings/{ref}/payment`.
- Tier 1, always present regardless of whether the generic aspect (Tier 2) has landed yet:
  `UNIQUE(payment.booking_id)` and `UNIQUE(refund.booking_id)` make a double-charge or
  double-refund a constraint violation, caught and translated to replaying the existing result —
  never a second side effect.
- Tier 2, the generic `@Idempotent` aspect (once built): hash the canonical request body, insert
  into `idempotency_record` keyed on `(idem_key, user_id)`. On a unique-constraint hit: same hash
  + `COMPLETED` → replay the stored response with `Idempotency-Replayed: true`; same hash +
  `IN_PROGRESS` → 409 `REQUEST_IN_PROGRESS`; different hash → 422 `IDEMPOTENCY_KEY_REUSE`.

## Validation — two layers, never mixed

1. **Syntactic**, on the DTO: Jakarta Bean Validation annotations. → 400 with a populated
   `errors[]` on `ProblemDetail`.
2. **Semantic/business**, in the service: throws a typed `BusinessException`. "Is this show
   bookable right now?" is never a bean-validation annotation, and a controller never contains an
   `if` that encodes a business rule.

## Misc conventions

- `/api/v1` prefix on every endpoint.
- `Instant` fields serialize as ISO-8601 UTC; `BigDecimal` serializes as plain decimal
  (`WRITE_BIGDECIMAL_AS_PLAIN` — see `JacksonConfig`), never scientific notation.
- Public identifiers in URLs are opaque references (`booking.bookingReference`, a `BK` + 8-char
  Crockford-base32 string), not raw sequential database ids, to avoid enumeration.
