# AGENTS.md — CineAgent Engineering Constitution

This file is the single source of truth for how this codebase is built. It is read by any AI
coding agent (Claude Code, or otherwise) before it writes a line of code in this repo, and it is
the first thing a human reviewer should read to understand *why* the code looks the way it does.

It is written to be tool-agnostic on purpose: the rules below are good engineering practice
independent of which agent is applying them. `CLAUDE.md` is a thin pointer to this file.

## 1. What this project is

**CineAgent** — a movie ticket booking system for the DMG Companies Java Developer take-home
assignment. One of four possible problem statements (see `docs/raw/assignment-pdfs/`); this one
was chosen because seat-level holds with automatic expiry is the hardest and most distinctive
concurrency problem of the four, and it comes with pricing tiers, discount codes, and
configurable refund policies for free.

The full architecture reasoning, every rejected alternative, and the phase-by-phase build plan
live in `docs/raw/planning/architecture-plan.md` (once committed) — read that first for *why*,
this file for *how to build within those decisions*.

**Explicitly out of scope** (per the assignment; do not build these, and do not let an agent
"helpfully" add them): UI/frontend, deployment/containerization/CI-CD, distributed systems or
microservices, advanced auth (OAuth/SSO/MFA), production-grade observability/monitoring/alerting.

## 2. Stack (do not deviate without updating this file)

| | |
|---|---|
| Language | Java 25 (LTS) |
| Framework | Spring Boot 4.1.x |
| Build | Maven, via the committed wrapper (`./mvnw`) — never assume a global `mvn` |
| Persistence | Spring Data JPA + Hibernate, Flyway migrations, `ddl-auto=validate` (never `update`) |
| Runtime DB | H2, PostgreSQL compatibility mode (default profile — zero external dependencies) |
| Test DB | H2 **and** real Postgres via `zonky` embedded-postgres (no Docker available/required) |
| Auth | Spring Security, stateless JWT (HS256), BCrypt |
| API docs | springdoc-openapi 3.1.1 |
| Package naming | `com.cineagent.<feature>.<layer>` |

`JAVA_HOME` on the dev machine is pinned to JDK 25 via `~/.zshrc`. Every `./mvnw` invocation in
this repo must run under that JDK — verify with `java -version` if a build behaves unexpectedly.

## 3. Package layout — feature-sliced, not layered

```
com.cineagent/
├── common/          shared kernel: Clock bean, error handling, security, money, persistence base
├── identity/         users, roles, JWT auth
├── catalog/          cities, theaters, screens, seats, movies (admin reference data)
├── show/              shows, per-category pricing, show-seat provisioning, seat map
├── booking/           seat holds, bookings, cancellation, status history — THE CORE MODULE
├── pricing/           pricing rule engine, discount codes
├── payment/           payment gateway port + mock, confirmation
├── refund/            refund policies, refund calculation
└── notification/     transactional outbox, dispatcher, reminders — a sink, nothing calls into it
```

**Why feature-sliced, not `controller/service/repository`:** a reviewer skimming the repo for ten
minutes should learn the domain decomposition from the package list alone. Strict layering
produces a 20-file junk drawer per layer at this project's size and tells a skimming reviewer
nothing. See the architecture plan for the full reasoning.

**Dependency rule (a feature depends on `common` freely; cross-feature calls only through another
feature's `service`/`domain`/`dto`, never its `repository`):**

```
booking → show, pricing, payment, refund, identity
pricing → catalog
notification → nothing (event-driven only; it is a sink)
```

This will be enforced by ArchUnit (Phase 5) — until then, hold the line by convention.

**Ports, used sparingly and deliberately:** only `PaymentGateway` and `NotificationSender` are
real ports with a real interface + mock implementation. Everything else is plain layering. Do not
add a port/adapter split "for consistency" — a port earns its place only when a second real
implementation genuinely exists or is imminent.

## 4. Non-negotiable disciplines

These are enforced by ArchUnit once Phase 5 lands; until then, every agent and every human
reviewing a diff must check for these by hand.

1. **`Clock` injection everywhere; `Instant.now()` / `LocalDate.now()` calls are banned in
   `main`.** Inject `java.time.Clock` and call `clock.instant()`. This is what makes hold-expiry
   and refund-window tests deterministic with zero `Thread.sleep()`.
2. **`BigDecimal` only for money; `double`/`float` are banned in the `pricing`, `refund`,
   `payment`, and `booking` packages.** Columns are `NUMERIC(12,2)`. Round with `HALF_UP` at
   `setScale(2)`, once per line item as it is produced — never round only at the end (breakdowns
   must sum exactly to the total; this is asserted in tests).
3. **Locking queries are single-table, by primary key, never with a status predicate in the
   `WHERE` clause of a `FOR UPDATE` query.** Two independent reasons: (a) under `READ_COMMITTED`
   a blocked `FOR UPDATE` re-evaluates its `WHERE` on wake-up, so a status predicate can silently
   return fewer rows than requested; (b) H2 rejects `FOR UPDATE` combined with `JOIN` outright.
   Check status in Java, after the lock is held.
4. **Every multi-row lock acquisition sorts ids ascending in Java before querying**, in addition
   to any SQL `ORDER BY`. Global lock order across the whole system:
   `booking → show_seat (asc id) → discount_code → inserts`. This is what makes concurrent
   multi-seat bookings deadlock-free — do not acquire locks in request order.
5. **`show_seat` carries both a pessimistic lock path (`SELECT … FOR UPDATE`) and an optimistic
   `@Version` column.** The version check is not a nicety — H2 (the default runtime profile) does
   not document `FOR UPDATE` as taking a true exclusive row lock, so `@Version` is the mechanism
   that makes the no-double-booking guarantee hold on the profile a reviewer actually runs.
6. **Never hold a database row lock across a network call.** Payment gateway calls, and any other
   I/O, happen strictly between transactions, never inside one that holds seat locks.
7. **`spring.jpa.open-in-view=false`** is set explicitly in every profile. Do not remove it.
8. **Portable DDL only** in Flyway migrations: `GENERATED BY DEFAULT AS IDENTITY` (not
   `BIGSERIAL`), `VARCHAR` + `CHECK` (not native enum types), `NUMERIC`, `TIMESTAMP WITH TIME
   ZONE`, no partial/conditional unique indexes, no `JSONB`. One migration set must run
   unmodified on both H2 (PostgreSQL mode) and real Postgres.
9. **Every `@ManyToOne` is `FetchType.LAZY`.** Enums are always `@Enumerated(EnumType.STRING)`,
   mirrored by a DB `CHECK` constraint — never `ORDINAL`.
10. **Virtual threads are deliberately not enabled.** Do not add `spring.threads.virtual.enabled`
    without discussing it first — it risks pinning on the synchronized/locking sections that are
    this project's core guarantee, and it would muddy concurrency test measurements.

## 5. Error handling contract

- RFC 7807 `ProblemDetail`, via one `@RestControllerAdvice` extending
  `ResponseEntityExceptionHandler` (`common/error/GlobalExceptionHandler`), so Spring's own
  exceptions (bean validation failures, malformed JSON, 404s, 405s) come back in the *same* shape
  as our own business exceptions. Never introduce a second error envelope.
- All error codes are declared once in `common/error/ErrorCode` (an enum of `code` + default
  `HttpStatus` + `title`). Adding a new error is one enum constant plus a throw site — never a
  `switch` over exception types in the handler.
- Status code taxonomy (stick to this, it's a stated policy in the README):
  - `400` — syntactically malformed / bean-validation failure
  - `401` / `403` — not authenticated / not permitted
  - `404` — not found **or** not owned by the caller (never leak existence via 403 — anti-enumeration)
  - `402` — payment declined by the gateway
  - `409` — well-formed but conflicts with current state (seat taken, hold expired, illegal
    transition, concurrency loss)
  - `422` — well-formed and consistent, but rejected by a business rule (expired coupon, refund
    window passed)
- Every `ProblemDetail` carries a `traceId` (from an MDC request filter) echoed in the matching
  log line, so a reviewer can go from a 500 response straight to the stack trace.

## 6. API conventions

- Prefix: `/api/v1`.
- Request/response DTOs are Java `record`s. Entities never leave the service layer.
- Bean Validation (`@NotNull`, `@Size`, `@DecimalMin`, …) on DTOs for syntactic checks; business
  rules live in services and throw a typed `BusinessException` subtype — controllers never
  encode business rules, services never re-check `@NotNull`.
- Paginated responses use a `PageResponse<T>` wrapper — never serialize Spring's `Page` directly.
  Every paginated endpoint has a max page-size cap.
- `Idempotency-Key` header is required on `POST /bookings` and `POST /bookings/{ref}/payment`.
  `UNIQUE(payment.booking_id)` / `UNIQUE(refund.booking_id)` make double-charge and double-refund
  structurally impossible as a backstop even before the idempotency layer is built.
- All timestamps are `Instant`, serialized ISO-8601 UTC. `BigDecimal` serializes as plain (never
  scientific notation) — see Jackson config in `common/config`.

## 7. Testing requirements

- Unit tests: plain JUnit 5 + AssertJ, no Spring context, for pricing/refund rule logic — always
  against a fixed/advanceable `Clock`, never `Thread.sleep()`.
- Slice tests: `@WebMvcTest` (error contract, auth), `@DataJpaTest` (constraints).
- Integration tests: `@SpringBootTest`, named `*IT`, run by Failsafe under `mvn verify`.
- **The concurrency suite (`ConcurrentSeatHoldIT` and siblings) runs on both H2 and real
  Postgres** (abstract base class, two engine-specific subclasses). The H2 run is what proves the
  guarantee holds on the profile a reviewer actually launches; the Postgres run is what proves it
  on the intended production engine. Never let one subsume the other.
- Every concurrency test must follow the five rules in the `concurrency-testing` skill
  (two latches, Hikari pool ≥ thread count, no `@Transactional` on the test method, assert
  against DB truth not in-process counters, assert the unexpected-exception queue is empty).
  Read that skill before writing or reviewing a concurrency test.
- `mvn test` = fast suite, H2 only, no network. `mvn verify` = adds the embedded-Postgres suite,
  and must skip gracefully (not fail) if the Postgres binary can't be fetched.

## 8. Commit discipline

- Conventional Commits (`feat(scope): ...`, `test(scope): ...`, `chore: ...`, `docs: ...`).
- Every commit compiles and is test-green on its own — no "wip" commits, no commit that only
  half-implements a slice.

## 9. Definition of done, per feature commit

Before considering any slice finished:
1. `./mvnw test` is green.
2. New locking/transactional code has been checked against §4 by hand (or, once available, by
   the `concurrency-auditor` subagent) — status predicate in a locking query, lock-ordering
   violation, network call inside a transaction, and event published before commit are the four
   specific traps to hunt for.
3. New business logic has a unit test with a fixed `Clock`, not a live one.
4. Money-handling code uses `BigDecimal` exclusively and has a "breakdown sums to total" test.
5. New error paths return a `ProblemDetail` via the existing `ErrorCode` registry, not an ad hoc
   response.
6. The commit message states what changed and, where it's a scoping decision, why.

## 10. Skills and subagents

- `.claude/skills/spring-boot-conventions` — package/DTO/transaction conventions in more detail
- `.claude/skills/flyway-migrations` — the portable DDL subset, migration numbering, "never edit
  an applied migration"
- `.claude/skills/api-contract` — the full error/status/pagination/idempotency contract
- `.claude/skills/concurrency-testing` — the five-rule recipe for a concurrency test that actually
  proves something
- `.claude/agents/spring-reviewer` — reviews a diff against this file's conventions
- `.claude/agents/concurrency-auditor` — hunts the four specific concurrency traps in §9.2

Run the relevant subagent against any commit touching `booking`, `show_seat` locking, or the
outbox before considering that commit final.
