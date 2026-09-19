# CineAgent

A robust, highly concurrent movie ticket booking system. Built to handle seat-level holds, configurable refund policies, pricing tiers, and discount codes, all with strong concurrency guarantees.

## Quick start

Clone, build, and run in under 30 seconds. Requires JDK 25.

```bash
./mvnw clean package -DskipTests
java -jar target/cineagent-0.0.1-SNAPSHOT.jar
```

The app boots on `localhost:8080`.
An initial dataset (cities, theaters, users, and admin roles) is seeded automatically via Flyway.

Full architectural reasoning, sequence diagrams, the requirements traceability matrix, and a
list of known gaps live in **[`ARCHITECTURE.md`](ARCHITECTURE.md)** — this README stays a quick
start and feature list on purpose.

## Configuration

Every setting below has a safe default and none are required to run locally. See
**[`.env.example`](.env.example)** for the full list with explanations; copy it to `.env`
(gitignored) and `export $(grep -v '^#' .env | xargs)` before starting the app, or export
individual variables directly.

| Variable | Used for | Default |
|---|---|---|
| `JWT_SECRET` | HS256 signing key | a committed dev-only value |
| `GEMINI_API_KEY` | real NL-search parsing (optional, see below) | unset — deterministic parser used instead |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | `postgres` profile only | local Postgres defaults |

## Features

- **Seat-level booking**: Materialized `show_seat` rows, one per `(show, seat)`
- **Time-bound holds**: Auto-release on expiry via `SeatHoldService` lazy expiry + `HoldExpirySweeper`
- **Pricing tiers**: Configurable base prices with regular, premium, and weekend rates
- **Discount codes**: Usage capped, flat or percentage-based
- **Configurable refund policies**: Tiered pro-rata per-seat refunds
- **Concurrency & Seat Locking Guarantee**: Strong serialization for concurrent booking to prevent double-allocation
- **Notifications**: Transactional outbox for async notifications, plus a reminder job and an
  ETA-based "leave now" nudge (Swiggy/Zomato-style meme copy)
- **Natural-language show search**: `GET /shows/search?q=...` — deterministic keyword parsing by
  default, or real Gemini-backed parsing when `GEMINI_API_KEY` + `app.ai.gemini.enabled=true` are
  set, with automatic fallback to the deterministic parser on any API failure

## Tech stack

- **Java 25 (LTS) + Spring Boot 4.1**
- **Maven**
- **Spring Data JPA + Flyway**
- **H2 in-memory (PostgreSQL compatibility mode)** and **embedded Postgres**
- **Feature-sliced package layout**
- **Spring Security + stateless JWT (HS256), BCrypt**
- **RFC 7807 `ProblemDetail`** for error handling
- **`BigDecimal`** strict usage for all financial math

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
so nothing in the codebase ever imports *from* `notification`.

Cross-module calls only ever go through another module's `service`/`domain`/`dto` layer, never
its `repository`.

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
```

## The booking and payment lifecycle

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

Full mechanics of the payment split (why the gateway call never runs inside a lock-holding
transaction, and the `SEAT_LOST_AFTER_PAYMENT` compensation path) are in `ARCHITECTURE.md`.

## API reference

Full OpenAPI spec is live at `/v3/api-docs` and browsable at `/swagger-ui.html`. Grouped summary:

| Module | Endpoints |
|---|---|
| **Auth** (public) | `POST /auth/register`, `POST /auth/login`, `GET /auth/me` |
| **Catalog** (public browse) | `GET /cities`, `GET /cities/{id}/theaters`, `GET /movies`, `GET /screens/{id}/seats` |
| **Catalog** (admin) | `POST`/`PUT /admin/cities`, `POST`/`PUT /admin/theaters`, `POST /admin/screens`, `POST /admin/screens/{id}/seats/bulk`, `POST`/`PUT /admin/movies` |
| **Shows** (public browse) | `GET /shows`, `GET /shows/{id}`, `GET /shows/{id}/seats`, `GET /shows/search?q=...` (NL search) |
| **Shows** (admin) | `POST /admin/shows`, `POST /admin/shows/{id}/cancel` |
| **Seat holds** | `POST /shows/{id}/holds`, `POST /holds/{id}/extend`, `DELETE /holds/{id}` |
| **Seat blocking** (admin) | `POST /admin/show-seats/{id}/block`, `POST /admin/show-seats/{id}/unblock` |
| **Discounts** (admin) | `POST /admin/discounts` |
| **Refund policies** (admin) | `POST /admin/refund-policies` |
| **Bookings** | `POST /bookings`, `POST /bookings/{id}/pay`, `POST /bookings/{id}/cancel-seats`, `POST /bookings/{id}/cancel`, `GET /bookings`, `GET /bookings/{id}`, `GET /bookings/{id}/seats`, `GET /bookings/{id}/charges`, `GET /bookings/{id}/history` |
| **Notifications** (admin) | `GET /admin/notifications`, `POST /admin/notifications/dispatch-now` |
| **Ops** (admin) | `POST /admin/ops/sweep-now`, `POST /admin/ops/remind-now`, `POST /admin/ops/nudge-now` |

All `/api/v1/admin/**` routes require `ROLE_ADMIN`; everything else requires authentication
except the explicitly public browse endpoints above.

## Testing

```bash
./mvnw test                          # H2 suite
./mvnw verify                        # adds the embedded-Postgres concurrency suite
./mvnw verify -Dpostgres.it.skip=true # skip Postgres explicitly (offline reviewer)
```

The test suite covers:
- **Concurrency & Seat Locking Guarantee**: 4 integration test classes (`ConcurrentSeatHoldH2IT`,
  `ConcurrentSeatHoldPostgresIT`, `ConcurrentSeatHoldHttpIT`) covering single-seat contention,
  overlapping multi-seat all-or-nothing, reversed-order-request deadlock freedom, and HTTP-level
  tests proving the loser gets a clean `409` conflict.
- **Money/pricing math**: `DiscountCodeTest`, `BookingCreateServiceTest` — percentage/flat
  discounts, capping, and pro-rata per-seat allocation summing exactly to the total.
- **Refund resolution**: `RefundPolicyResolverTest`, `BookingCancellationServiceTest` —
  most-specific-scope-wins ordering, exact tier boundaries, pro-rata refund on only the
  refundable charge lines.
- **Booking state machine**: `BookingStatusTest`, `BookingTest` — exhaustive transition matrix.
- **Discount usage-cap logic**: `DiscountServiceTest` — validity window boundaries, redemption
  cap, minimum order amount.
- **Seat/hold expiry boundaries**: `ShowSeatTest`, `SeatHoldTest` — exact instant-of-expiry
  behavior with a fixed `Clock`, no `Thread.sleep`.
- **Request validation**: `BookingControllerValidationTest`, `AdminDiscountControllerValidationTest`.

Full requirements-to-test traceability matrix is in `ARCHITECTURE.md`.

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
