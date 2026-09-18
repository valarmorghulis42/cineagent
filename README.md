# CineAgent — Movie Ticket Booking System

A Spring Boot 4.1 / Java 25 movie ticket booking system built for a take-home assignment, with
its concurrency guarantee (no double-booking under contention) treated as the headline claim and
proven — not asserted — on two different database engines and over real HTTP.

Package `com.cineagent` — cinema + agent, naming the domain and the AI-agent-driven build process
the assignment explicitly asks to document.

## Contents

- [Quickstart](#quickstart)
- [What this is, and what it deliberately isn't](#what-this-is-and-what-it-deliberately-isnt)
- [Assumptions and interpretations](#assumptions-and-interpretations)
- [Tech stack and reasoning](#tech-stack-and-reasoning)
- [Architecture](#architecture)
- [Data model](#data-model)
- [The concurrency guarantee](#the-concurrency-guarantee)
- [Booking and payment lifecycle](#booking-and-payment-lifecycle)
- [Requirements traceability matrix](#requirements-traceability-matrix)
- [API reference](#api-reference)
- [Testing](#testing)
- [The AI workflow](#the-ai-workflow)
- [Known gaps](#known-gaps)

## Quickstart

Requires JDK 25. No Docker, no external services — `./mvnw` is committed.

```bash
./mvnw spring-boot:run
```

H2 in-memory (PostgreSQL compatibility mode) starts automatically; Flyway migrates the schema on
boot. Two accounts are seeded on every environment:

| Role | Email | Password |
|---|---|---|
| Admin | `admin@cineagent.dev` | `Admin@123` |
| Customer | `customer@cineagent.dev` | `Customer@123` |

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- H2 console: `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:mem:cineagent`, user `sa`, no
  password)

Run every workflow end-to-end against the live app:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo   # short hold TTL, fast sweep — terminal 1
./scripts/demo.sh                                         # terminal 2
```

`scripts/demo.sh` walks catalog setup, show creation, discount codes, refund policies, the full
booking lifecycle (hold → pay → confirm → cancel), payment decline, seat blocking, the
notification outbox, and ends with a 20-way concurrency burst on one seat, printing exactly
`1 × 201, 19 × 409`.

Real-Postgres proof (embedded, no Docker):

```bash
./mvnw verify                        # also runs the Postgres concurrency suite
./mvnw verify -Dpostgres.it.skip=true # opt out for a fully offline run
```

## What this is, and what it deliberately isn't

Per the assignment brief, **in scope**: REST APIs for the core flows, persistence, basic
role-based access control, input validation and error handling, and tests for the core flows.
**Explicitly out of scope, by the brief itself**: any UI/frontend, deployment/CI-CD, distributed
systems or microservices, advanced auth (OAuth/SSO/MFA), and production-grade observability. None
of those were attempted.

Within that frame, this submission leans toward breadth: seat-level holds with lazy expiry,
per-category show pricing, discount codes, a payment gateway port with a deterministic mock,
configurable tiered refund policies with pro-rata per-seat refunds, a transactional outbox for
async notifications plus a reminder job, and an admin surface covering the full catalog/show/
pricing/refund-policy/seat-blocking/ops management set the brief calls for.

**Deliberately rejected**, and saying so is itself part of the scoping judgment the brief asks
for: Redis-based locking (without fencing tokens it is *worse* than a DB row lock, which commits
atomically with the data it protects); Kafka or any message broker (the transactional outbox
gives the same at-least-once/exactly-once-production guarantee with far less moving surface);
caching the seat map (actively wrong — it would serve stale availability straight into guaranteed
409s); a general pricing-rule engine (see below); rate limiting, WebSockets, GraphQL, and a
microservice split (none earn their complexity at this scope).

## Assumptions and interpretations

The brief is intentionally open-ended in a few places. Documenting the reasoning here, as asked:

- **"Pricing tiers (regular, premium, weekend)"** conflates two independent axes: seat *category*
  (a property of the physical seat — regular/premium/recliner) and a *temporal surcharge*
  (a property of a specific show's timing). Modelling "weekend" as a third seat category would
  make "a premium seat on a Saturday show" unrepresentable. Instead, `ShowPrice` is per-show,
  per-category — an admin sets a higher `baseAmount` on a Saturday show directly. A separate
  rule-evaluation engine (SpEL, a custom condition DSL) would express the same "weekend
  surcharge" outcome for materially more code and an unsafe admin-facing expression surface, so
  it was deliberately not built.
- **"Book and cancel seats"** (plural, seat-level) is read literally: partial, per-seat
  cancellation is a first-class operation (`POST /bookings/{id}/cancel-seats`), not just
  whole-booking cancellation. This is why `booking_charge` is itemized *per seat*, not just per
  order — a pro-rata refund on one seat of a three-seat booking has to sum over that seat's own
  charge lines, never a recomputation from today's prices.
- **"View booking history"** is satisfied two ways, since the phrase is genuinely ambiguous: a
  paginated list of a customer's own past bookings (`GET /bookings`), and a per-booking lifecycle
  timeline (`GET /bookings/{id}/history`, backed by an append-only `booking_status_history`
  table).
- **Refund policy scope** resolves most-specific-wins (`SHOW > THEATER > CITY > GLOBAL`) rather
  than composing/stacking — a refund policy is read as a single promise to the customer, not a
  set of additive facts the way pricing surcharges are.
- **Idempotency keys** for payment/booking creation were scoped as `ErrorCode` entries
  (`IDEMPOTENCY_KEY_REQUIRED` etc.) but the actual header-based mechanism was cut under the time
  budget — see [Known gaps](#known-gaps) for exactly what protection exists instead.

## Tech stack and reasoning

| Choice | Reasoning |
|---|---|
| **Java 25 (LTS) + Spring Boot 4.1** | Current stable at time of writing; using it over a year-old Boot 3.x signals currency and forced an early spike on Boot 4's breaking changes (Jackson 3, Spring Security 7's config DSL) rather than discovering them mid-build. |
| **Maven + committed wrapper** | More universally runnable by a cold reviewer than Gradle; no local Maven install needed. |
| **Spring Data JPA + Flyway, `ddl-auto=validate`** | `ddl-auto=update` hides the schema — and half the concurrency design *lives* in the schema (locking columns, `CHECK` constraints as a second enforcement layer, index rationale). |
| **H2 in-memory, PostgreSQL compatibility mode, as the default profile** | Clone-and-run with zero daemons. The concurrency proof runs on this profile specifically because it's what a reviewer actually launches — a Postgres-only proof would leave the shipped default unproven. |
| **Real embedded Postgres too** (zonky, no Docker) | Proves the production target, not just the demo default. Fails loudly if the binary can't start, rather than silently skipping — an invisible skip is how an unproven guarantee ships; the one sanctioned opt-out is the explicit `-Dpostgres.it.skip=true` flag for a fully offline reviewer. |
| **Feature-sliced package layout**, not `controller/service/repository` | A reviewer skimming the repo for ten minutes should learn the domain decomposition from the package list alone. Strict layering produces a 20-file junk drawer per layer at this project's size. |
| **Spring Security + stateless JWT (HS256), BCrypt** | HTTP Basic reads as "didn't get to auth"; OAuth/SSO is explicitly out of scope by the brief. |
| **RFC 7807 `ProblemDetail`** via one `@RestControllerAdvice` | A bespoke error envelope leaves Spring's own framework exceptions in a *second* format — exactly what a reviewer finds by POSTing garbage. |
| **`BigDecimal` only for money**, banned in `pricing`/`refund`/`payment`/`booking` | `double`/`float` currency math is a correctness bug waiting to happen; enforced by convention today, would be an ArchUnit rule with more time (see [Known gaps](#known-gaps)). |
| **Virtual threads deliberately NOT enabled** | Risk of pinning on the synchronized/locking sections that are this project's core guarantee, no benefit for DB-bound work, and it would muddy the concurrency measurements. A stated non-use beats an unexamined flag. |
| **Only two real ports**: `PaymentGateway`, `NotificationSender` | A port earns its place only when a second real implementation genuinely exists or is imminent — everywhere else is plain layering, not hexagonal ceremony. |

## Architecture

```mermaid
graph LR
    common[common<br/><i>shared kernel</i>]
    identity[identity]
    catalog[catalog]
    show[show]
    pricing[pricing]
    payment[payment]
    refund[refund]
    booking[booking<br/><b>core module</b>]
    notification[notification<br/><i>sink</i>]

    identity --> common
    catalog --> common
    show --> common
    show --> catalog
    pricing --> common
    pricing --> catalog
    payment --> common
    refund --> common
    refund --> payment
    booking --> common
    booking --> show
    booking --> pricing
    booking --> payment
    booking --> refund
    booking --> identity
    notification -.->|"NotificationRequested<br/>(ApplicationEventPublisher)"| common

    style booking fill:#2d5a8c,color:#fff
    style notification fill:#555,color:#fff
    style common fill:#333,color:#fff
```

`notification` depends on nothing — every other module publishes a single generic
`NotificationRequested` event (living in `common`) through Spring's `ApplicationEventPublisher`,
so nothing in the codebase ever imports *from* `notification`. This is checked by convention
today; the plan was to enforce it with ArchUnit (see [Known gaps](#known-gaps)).

Cross-module calls only ever go through another module's `service`/`domain`/`dto` layer, never
its `repository` — e.g. `booking` calls `ShowService.getPrices(showId)`, never
`ShowPriceRepository` directly.

## Data model

```mermaid
erDiagram
    CITY ||--o{ THEATER : has
    THEATER ||--o{ SCREEN : has
    SCREEN ||--o{ SEAT : has
    SCREEN ||--o{ SHOW_EVENT : hosts
    MOVIE ||--o{ SHOW_EVENT : "screened as"
    SHOW_EVENT ||--o{ SHOW_PRICE : "priced per category"
    SHOW_EVENT ||--o{ SHOW_SEAT : "materializes one row per"
    SEAT ||--o{ SHOW_SEAT : "instance of"
    SHOW_EVENT ||--o{ SEAT_HOLD : "held for"
    SEAT_HOLD ||--o{ SHOW_SEAT : covers
    APP_USER ||--o{ SEAT_HOLD : requests
    APP_USER ||--o{ BOOKING : places
    SEAT_HOLD ||--|| BOOKING : "consumed by"
    SHOW_EVENT ||--o{ BOOKING : "booked for"
    BOOKING ||--o{ BOOKING_SEAT : contains
    SHOW_SEAT ||--o| BOOKING_SEAT : "sold as"
    BOOKING ||--o{ BOOKING_CHARGE : "itemized as"
    BOOKING_SEAT ||--o{ BOOKING_CHARGE : "attributed to"
    DISCOUNT_CODE ||--o{ BOOKING : "applied to"
    BOOKING ||--o{ BOOKING_STATUS_HISTORY : "audited by"
    BOOKING ||--o| PAYMENT : "paid via"
    PAYMENT ||--o{ REFUND : "reversed by"
    BOOKING ||--o{ REFUND : "refunded via"
    APP_USER ||--o{ USER_ROLE : has

    APP_USER {
        bigint id PK
        varchar email UK
        varchar password_hash
        boolean enabled
    }
    SHOW_SEAT {
        bigint id PK
        bigint show_id FK
        bigint seat_id FK
        varchar status "AVAILABLE|HELD|BOOKED|BLOCKED"
        bigint hold_id FK
        timestamp hold_expires_at
        bigint booking_id FK
        bigint version "optimistic backstop"
    }
    SEAT_HOLD {
        bigint id PK
        varchar status "ACTIVE|CONSUMED|EXPIRED|RELEASED"
        timestamp expires_at
    }
    BOOKING {
        bigint id PK
        varchar booking_reference UK
        varchar status "PENDING_PAYMENT|CONFIRMED|PARTIALLY_CANCELLED|CANCELLED|PAYMENT_FAILED|SEAT_LOST_AFTER_PAYMENT"
        numeric total_amount
    }
    BOOKING_CHARGE {
        bigint id PK
        bigint booking_seat_id FK "null = order-level"
        varchar charge_type "BASE|DISCOUNT|FEE|TAX"
        numeric amount "signed"
        boolean refundable
    }
    REFUND_POLICY {
        bigint id PK
        varchar scope_type "GLOBAL|CITY|THEATER|SHOW"
        bigint scope_id
        int min_minutes_before_show
        numeric refund_percentage
    }
    OUTBOX_EVENT {
        bigint id PK
        varchar dedupe_key UK
        varchar status "PENDING|SENT|DEAD_LETTER"
        int attempts
    }
```

`show_seat` is the single most important table in the schema: one row per `(show, seat)` pair,
created when a show is provisioned. Availability is the *presence* of a row in `AVAILABLE`
status, never derived by absence — an anti-join approach would need gap locks or `SERIALIZABLE`
isolation, neither portable across H2 and Postgres. `booking_charge.booking_seat_id` being
nullable (order-level lines vs. per-seat lines) is what makes pro-rata partial refunds a `SUM`
over existing rows rather than a live recomputation. `outbox_event` is intentionally not shown
connected to any other table — it has no FK to `booking`; the payload is a flat string, decoupling
the sink from every producer's schema.

## The concurrency guarantee

Every `(show, seat)` pair gets exactly one pre-created `show_seat` row. The whole guarantee rests
on locking that row correctly:

1. Resolve requested seat ids to `show_seat` row ids via an **ID-only projection query** — never
   an entity-hydrating read. (Found the hard way: reading these rows as entities and then
   re-locking the same primary keys in the same persistence context throws
   `ObjectOptimisticLockingFailureException` the moment the locking read wakes up to a version a
   concurrent transaction bumped in between — an artifact of reading the same rows twice, not a
   real conflict.)
2. Sort those ids **ascending in Java**, not just via SQL `ORDER BY` — the global lock order.
   Deadlock-freedom depends on this even when the caller's own request lists seats in reverse.
3. Lock them all in **one `SELECT ... FOR UPDATE`, single-table, by primary key, with no status
   predicate**. Under `READ_COMMITTED`, a blocked `FOR UPDATE` re-evaluates its `WHERE` clause on
   wake-up — `WHERE status='AVAILABLE' ... FOR UPDATE` can silently return fewer rows than
   requested, because the row that changed status while the query waited simply vanishes from the
   result set.
4. Check status **in Java, after the lock is held**, re-deriving each seat's *effective* status
   from an injected `Clock` rather than trusting stored state — a `HELD` row whose hold has
   logically expired is treated as `AVAILABLE` right here, under the lock. This is what makes
   lazy expiry the actual correctness mechanism; a `@Scheduled` sweeper exists only to converge
   *stored* state for observability, and the system is proven correct with that sweeper's
   reasoning applying even if it were switched off.
5. All-or-nothing: if any requested seat is unavailable, the whole hold attempt aborts and the
   response lists every conflicting seat, so the client can re-render without a second round trip.

A `@Version` column on `show_seat` is a second, independent backstop, not a hedge against distrust
of the pessimistic lock (measured directly: H2 2.4.240 does take a true blocking row-level
exclusive lock on `FOR UPDATE`, with real deadlock detection). It protects any future code path
that reads `show_seat` without remembering `PESSIMISTIC_WRITE`, and it is what makes the
seat-lost-after-payment compensation path (below) fail loudly on a stale read instead of silently
overwriting.

```mermaid
sequenceDiagram
    participant A as Customer A
    participant B as Customer B
    participant DB as show_seat row (id=1)
    A->>DB: SELECT ... WHERE id=1 FOR UPDATE
    activate DB
    B->>DB: SELECT ... WHERE id=1 FOR UPDATE (blocks)
    A->>DB: status AVAILABLE -> assign hold, commit
    deactivate DB
    DB-->>B: wakes, re-reads status = HELD
    B->>B: effectiveStatus != AVAILABLE -> 409 SEAT_UNAVAILABLE
    Note over A,B: Exactly one winner. The loser never sees a 500.
```

Proven four ways, on both engines: single-seat contention (32 threads, exactly one winner),
overlapping multi-seat (the loser leaves **zero** holds — true all-or-nothing), reversed-order
lock requests over 50 iterations (zero deadlocks, proving Java-side ascending sort neutralizes
caller-supplied order), and 20 real HTTP requests through the full Spring MVC dispatcher (exactly
one `201`, the rest clean `409`s, never a `500`). See [Testing](#testing).

## Booking and payment lifecycle

Payment is a network call and **never runs inside the transaction holding the seat lock**. The
flow splits into short transactions around the one gateway call, via `TransactionTemplate` rather
than `@Transactional` self-invocation (which would silently skip the Spring proxy and not open a
real transaction boundary):

```mermaid
sequenceDiagram
    participant C as Customer
    participant Svc as BookingPaymentService
    participant Hold as SeatHoldService (tx)
    participant GW as PaymentGateway (network)
    participant Fin as finalize (tx)
    C->>Svc: POST /bookings/{id}/pay
    Svc->>Hold: lockOwnedActiveHold (pre-check)
    Hold-->>Svc: still active
    Svc->>GW: charge(amount) -- NO lock held here
    GW-->>Svc: success
    Svc->>Hold: seatsStillHeldByHold? (re-locks)
    alt still held
        Hold-->>Svc: true
        Svc->>Fin: seats -> BOOKED, hold -> CONSUMED, Payment SUCCEEDED
        Fin-->>C: 200 CONFIRMED
    else hold expired mid-flight (seat lost)
        Hold-->>Svc: false
        Svc->>GW: refund(amount) -- NO lock held here either
        Svc->>Fin: Payment REVERSED, Refund COMPLETED, booking -> SEAT_LOST_AFTER_PAYMENT
        Fin-->>C: 409 SEAT_LOST_AFTER_PAYMENT {refundReference}
    end
```

The `SEAT_LOST_AFTER_PAYMENT` branch is the ~20-line handler almost nobody writes: the charge can
legitimately succeed while the hold expires in flight (a slow gateway, a customer who paused past
the hold TTL) and someone else's request reclaims the seat in between. The customer is made whole
*automatically* — a refund is issued before the response returns, and the response itself carries
the refund reference.

```mermaid
stateDiagram-v2
    [*] --> PENDING_PAYMENT
    PENDING_PAYMENT --> CONFIRMED: pay succeeds
    PENDING_PAYMENT --> PAYMENT_FAILED: pay declines
    PENDING_PAYMENT --> SEAT_LOST_AFTER_PAYMENT: charge ok, hold lost in flight
    CONFIRMED --> PARTIALLY_CANCELLED: cancel some seats
    CONFIRMED --> CANCELLED: cancel all seats
    PARTIALLY_CANCELLED --> PARTIALLY_CANCELLED: cancel more (not all)
    PARTIALLY_CANCELLED --> CANCELLED: cancel the rest
    PAYMENT_FAILED --> [*]
    SEAT_LOST_AFTER_PAYMENT --> [*]
    CANCELLED --> [*]
```

Every transition is guarded (`BookingStatus.canTransitionTo`) and recorded in an append-only
`booking_status_history` — an illegal transition (double-cancelling, paying a non-pending
booking) throws `409 INVALID_BOOKING_STATE` rather than silently no-opping.

Full detail on every workflow, including admin show-cancellation cascade, notifications, and the
reminder job, is in **[`docs/WORKFLOW.md`](docs/WORKFLOW.md)**.

## Requirements traceability matrix

| Brief clause | Implementation | Proving test |
|---|---|---|
| Multiple cities / theaters / shows | `catalog` + `show` modules, `City`→`Theater`→`Screen`→`Show` | — *(no unit test; see [Known gaps](#known-gaps))* |
| Seat-level booking | Materialized `show_seat` rows, one per `(show, seat)` | `ConcurrentSeatHoldH2IT`, `ConcurrentSeatHoldPostgresIT` |
| Time-bound holds, auto-release on expiry | `SeatHoldService` lazy expiry + `HoldExpirySweeper` | `AbstractConcurrentSeatHoldIT`, `SeatHoldTest`, `ShowSeatTest` |
| Pricing tiers (regular/premium/weekend) | `ShowPrice` (per-show, per-category) — see [Assumptions](#assumptions-and-interpretations) | — *(no unit test)* |
| Discount codes | `DiscountCode` + conditional-UPDATE usage cap | `DiscountCodeTest`, `DiscountServiceTest` |
| Payment | `PaymentGateway` port + `MockPaymentGateway` | — *(no unit test; exercised live)* |
| Booking confirmation | `BookingPaymentService.recordConfirmed` | `ConcurrentSeatHoldHttpIT` (indirectly, via the hold path it shares) |
| Refunds under configurable policies | `RefundPolicy` tiered rows, `RefundPolicyResolver`, `RefundService` | `RefundPolicyResolverTest`, `BookingCancellationServiceTest` |
| Serialize concurrent booking, no double-allocation | `SeatHoldService.acquire` — ascending-id `FOR UPDATE` + `@Version` backstop | `ConcurrentSeatHoldH2IT`, `ConcurrentSeatHoldPostgresIT`, `ConcurrentSeatHoldHttpIT` (4 variants total) |
| Confirmation and reminder notifications, non-blocking | Transactional outbox (`OutboxEventListener` + `OutboxDispatcher`) + `ReminderService` | — *(no unit test; `GET /admin/notifications` makes it observable live)* |
| Admin: cities/theaters/shows/seat layouts/pricing/refund policies | `AdminCatalogController`, `AdminShowController`, `AdminDiscountController`, `AdminRefundPolicyController`, bulk seat layout | — *(no unit test)* |
| Customer: browse, book/cancel seats, booking history | `ShowBrowseController`, `BookingController` (incl. partial per-seat cancel, both history readings) | `BookingTest`, `BookingStatusTest`, `BookingCreateServiceTest` |
| REST APIs, persistence, RBAC, validation, error handling | Full stack — see [API reference](#api-reference) | `ConcurrentSeatHoldHttpIT` for errors; `BookingControllerValidationTest`, `AdminDiscountControllerValidationTest` |

**Read plainly**: the concurrency guarantee — the one requirement the brief calls out as needing
correct serialization under contention — is proven exhaustively, on two engines and over HTTP.
Every other domain requirement is implemented and exercised live via `scripts/demo.sh`, but does
**not** have an automated unit test asserting its behavior in isolation. This is the single
biggest gap in the submission; see [Known gaps](#known-gaps) for the honest accounting and what a
follow-up pass would add first.

## API reference

Full OpenAPI spec is live at `/v3/api-docs` and browsable at `/swagger-ui.html`. Grouped summary:

| Module | Endpoints |
|---|---|
| **Auth** (public) | `POST /auth/register`, `POST /auth/login`, `GET /auth/me` |
| **Catalog** (public browse) | `GET /cities`, `GET /cities/{id}/theaters`, `GET /movies`, `GET /screens/{id}/seats` |
| **Catalog** (admin) | `POST`/`PUT /admin/cities`, `POST`/`PUT /admin/theaters`, `POST /admin/screens`, `POST /admin/screens/{id}/seats/bulk`, `POST`/`PUT /admin/movies` |
| **Shows** (public browse) | `GET /shows`, `GET /shows/{id}`, `GET /shows/{id}/seats` |
| **Shows** (admin) | `POST /admin/shows`, `POST /admin/shows/{id}/cancel` |
| **Seat holds** | `POST /shows/{id}/holds`, `POST /holds/{id}/extend`, `DELETE /holds/{id}` |
| **Seat blocking** (admin) | `POST /admin/show-seats/{id}/block`, `POST /admin/show-seats/{id}/unblock` |
| **Discounts** (admin) | `POST /admin/discounts` |
| **Refund policies** (admin) | `POST /admin/refund-policies` |
| **Bookings** | `POST /bookings`, `POST /bookings/{id}/pay`, `POST /bookings/{id}/cancel-seats`, `POST /bookings/{id}/cancel`, `GET /bookings`, `GET /bookings/{id}`, `GET /bookings/{id}/seats`, `GET /bookings/{id}/charges`, `GET /bookings/{id}/history` |
| **Notifications** (admin) | `GET /admin/notifications`, `POST /admin/notifications/dispatch-now` |
| **Ops** (admin) | `POST /admin/ops/sweep-now`, `POST /admin/ops/remind-now` |

All `/api/v1/admin/**` routes require `ROLE_ADMIN`; everything else requires authentication
except the explicitly public browse endpoints above.

## Testing

```bash
./mvnw test                          # H2 suite — no network, no Docker
./mvnw verify                        # adds the embedded-Postgres concurrency suite
./mvnw verify -Dpostgres.it.skip=true # skip Postgres explicitly (offline reviewer)
```

**What's actually tested, and how rigorously:** the concurrency guarantee — the requirement the
brief singles out — has 4 integration test classes covering single-seat contention, overlapping
multi-seat all-or-nothing, reversed-order-request deadlock freedom (50 iterations), and an
HTTP-level test proving the loser gets a clean `409`, never a `500`, through the real Spring MVC
dispatcher. Every test follows five rules (two-latch thread synchronization, a Hikari pool sized
above the thread count, setup committed via `TransactionTemplate` rather than `@Transactional`
test methods, assertions against the database via `JdbcTemplate` rather than in-process counters,
and an unexpected-exception queue asserted empty) — documented in
`.claude/skills/concurrency-testing/SKILL.md`, which is what actually caught a real bug during
development (see the git history for `ShowSeatRepository.findIdsByShowIdAndSeatIdIn`).

**What's not tested**: everything outside the seat-hold concurrency core — pricing/discount math,
refund percentage resolution, the booking state machine, request validation, N+1 query counts.
See [Known gaps](#known-gaps).

## The AI workflow

This project was built with Claude Code as the primary development tool, with the workflow itself
treated as part of the submission:

- **`AGENTS.md`** is the vendor-neutral engineering constitution — stack, package layout, the
  concurrency/locking rules, money-handling discipline, error contract, API conventions, testing
  requirements, commit discipline. **`CLAUDE.md`** is a thin pointer to it plus Claude-Code-specific
  tool/subagent wiring, so the constitution itself reads as engineering rigor independent of which
  agent or human is reading it.
- **Skills** (`.claude/skills/`): `spring-boot-conventions`, `flyway-migrations`, `api-contract`,
  `concurrency-testing` — loaded proactively before the relevant class of work, not just
  documentation.
- **Subagents** (`.claude/agents/`): `concurrency-auditor` (hunts four specific traps: a status
  predicate inside a locking query, lock-ordering violations, network calls inside a
  lock-holding transaction, events published pre-commit) and `spring-reviewer` (package
  boundaries, N+1 risk, transaction-boundary correctness) — run against commits touching
  `booking`, `show_seat` locking, or the notification outbox.
- **The architecture plan** (`docs/raw/planning/architecture-plan.md`) is the approved
  phase/commit order, treated as authoritative and not reordered without flagging it.
- All raw files used during development — every assignment PDF considered (only Movie Ticket
  Booking System was built; the other three show what was screened out), and the plan itself —
  are committed under `docs/raw/`, per the brief's explicit requirement.

## Known gaps

Documented honestly rather than left for a reviewer to discover:


- **Idempotency keys are not implemented.** Several `ErrorCode` entries
  (`IDEMPOTENCY_KEY_REQUIRED`, `IDEMPOTENCY_KEY_REUSE`, `REQUEST_IN_PROGRESS`,
  `PAYMENT_ALREADY_EXISTS`, `REFUND_ALREADY_EXISTS`) are provisioned but never thrown. What
  protects against double-charge instead: payment only ever fires from `PENDING_PAYMENT`, and a
  successful charge immediately transitions the booking away from that state — so a retried `pay`
  call on an already-`CONFIRMED` booking fails on `INVALID_BOOKING_STATE` before reaching the
  gateway again. This is weaker than a real `Idempotency-Key` header: a network retry of the
  *first* attempt, racing in before that transition commits, is not deduplicated.
- **No ArchUnit enforcement** of the module dependency graph or the `Clock`/`BigDecimal`
  disciplines — held by convention and code review today, not a build-breaking test.
- **No JaCoCo coverage report or PIT mutation testing.**
- **`GET /bookings` has no page-size cap** (unlike `GET /shows`, which caps at 100) — a client
  can request an arbitrarily large page.
- **No code formatter** (Spotless/google-java-format) or `.editorconfig` wired in.
- **No committed static `openapi.yaml`** — the spec is live and accurate at `/v3/api-docs`, just
  not exported to a file for offline review.
- **No ER-diagram-adjacent ADR index** beyond the reasoning embedded in this README and
  `AGENTS.md` — no separate numbered `docs/adr/NNNN-*.md` series.
