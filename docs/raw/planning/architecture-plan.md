# DMG Take-Home — Movie Ticket Booking System (Spring Boot)

## Context

DMG Companies sent a take-home for a Java Developer role: pick **one of four** problem statements, build it in Spring Boot, submit a GitHub repo plus a ≤10-minute Loom video. All four PDFs are **identical** in framing, scope, and grading bar — only the domain differs.

**Chosen: Movie Ticket Booking System.** Hardest and most distinctive concurrency problem (seat-level holds with automatic expiry, not decrementing a stock counter), and pricing tiers, discount codes, and configurable refund policies come free in the brief — so breadth is given, not invented.

What the PDF actually rewards, in its own words: *"Your interpretation of the requirement and the features you choose to build are themselves part of what is being evaluated... both the breadth and the depth of the features you build contribute."* Scoping judgment is graded as heavily as code.

**Explicitly out of scope per the PDF** (don't build, but *do* state we deliberately skipped them): UI/frontend, deployment/containerization/CI-CD, distributed systems or microservices, advanced auth (OAuth/SSO/MFA), production-grade observability.

### Environment reality (verified)

| | Status | Action |
|---|---|---|
| JDK / Maven / Docker / `gh` | **all absent** | `brew install openjdk@21 maven`; commit `mvnw`; Docker ruled out; push over HTTPS |
| git identity | **unset** | **Open item — need your name + email** (set repo-local) |
| Homebrew | present (arm64, macOS 26.6) | — |

---

## Requirements traceability (mined clause-by-clause from the PDF)

Every clause below becomes a row in the README's traceability matrix: **clause → implementing class/endpoint → test that proves it.** This is the highest-value single artifact in the submission — it directly answers "did you actually read the brief".

| PDF clause | Coverage |
|---|---|
| multiple cities / theaters per city / shows per theater | `catalog` + `show` modules |
| seat-level booking | `show_seat` materialized rows |
| time-bound holds, auto-release on expiry | lazy expiry + sweeper |
| pricing tiers (regular, premium, weekend) | `pricing_rule` chain — see axis-split note |
| discount codes | `discount_code` + conditional-UPDATE caps |
| payment | `PaymentGateway` port + mock |
| booking confirmation | outbox-backed |
| refunds under **configurable** refund policies | `refund_policy` tiered rows, snapshotted |
| serialize concurrent booking, no double-allocation | **two-mechanism defence — §Concurrency** |
| confirmation **and reminder** notifications, non-blocking | transactional outbox + reminder job |
| admin manages cities, theaters, shows, **seat layouts**, pricing tiers, refund policies | admin CRUD + **bulk layout API** |
| customer: browse, **book and cancel seats**, **view booking history** | **both ambiguities resolved below** |

**Two requirement ambiguities I am resolving deliberately, and documenting:**

1. **"book and cancel *seats*"** (plural, seat-level) — read literally, this asks for **partial, per-seat cancellation**, not just cancelling a whole booking. Supporting it requires pro-rata attribution of an order-level discount back to individual seats. We will support it (see §Enhancements B1).
2. **"view booking history"** — reads either as *the list of my past bookings* or *the lifecycle history of a booking*. We satisfy **both**: a paginated booking list, plus a `booking_status_history` audit trail with a per-booking timeline endpoint.

**Submission clause often missed:** *"Must include all raw files used during development."* This is a graded deliverable and needs a home in the repo — see §Enhancements C1.

---

## Decisions (locked)

| Decision | Choice | Rejected, and why |
|---|---|---|
| Stack | **Java 25 (LTS) + Spring Boot 4.1.x** (current stable, Aug 2026) | Java 21 — that LTS is one generation stale, superseded by 25 in Sept 2025; Java 27 — a non-LTS that just GA'd, too fresh/unsupported for a graded submission. Boot 3.x — a year behind; using current stable signals currency. Spike first: Boot 4 ships **Jackson 3** and **Spring Security 7**, both of which change config APIs |
| API docs | springdoc-openapi **3.1.1** (verified Boot 4.x compatible; Java 21+) | — |
| Build | Maven + committed wrapper | Gradle — Maven is more universally runnable by a cold reviewer |
| Persistence | Spring Data JPA + **Flyway**, `ddl-auto=validate` | `ddl-auto=update` hides the schema, and half the concurrency design *lives* in the schema |
| Runtime DB | **H2 in-memory, PostgreSQL mode** | Clone-and-run with zero daemons — mandatory given no DB server available |
| Concurrency proof | **Both H2 and real Postgres** (zonky embedded-postgres, no Docker) | H2-only leaves the claim unproven; Postgres-only leaves the *shipped default* unproven — **we need both, see §Concurrency** |
| Package layout | Feature-sliced modular monolith | Strict `controller/service/repository` tells a 10-min reviewer nothing; full hexagonal doubles file count for adapters never swapped |
| Ports | Only `PaymentGateway` + `NotificationSender` | "Ports where two implementations genuinely exist, plain layering elsewhere" *is* the seniority signal |
| Auth | Spring Security + stateless JWT (HS256), BCrypt, no refresh token | HTTP Basic reads as "didn't get to auth"; OAuth/SSO explicitly out of scope |
| Errors | RFC 7807 `ProblemDetail` + one `@RestControllerAdvice` extending `ResponseEntityExceptionHandler` | A bespoke envelope leaves Spring's own exceptions in a *second* format — exactly what a reviewer finds by POSTing garbage |
| Virtual threads | **Deliberately not enabled**, and documented | Pinning risk on synchronized sections, no benefit for DB-bound work, and it would muddy the concurrency measurements. A stated non-use beats an unexamined flag |
| Commits | ~24 vertical slices, Conventional Commits, each compiling and green | Fine-grained commits don't compile individually; milestone commits look dumped |

**Cross-cutting discipline, enforced by ArchUnit rather than merely documented:** a `Clock` bean everywhere (`Instant.now()` banned) so expiry and refund-window tests are deterministic with zero `Thread.sleep()`; `BigDecimal` only in pricing (`double`/`float` banned); `open-in-view=false` set explicitly; portable DDL subset only (`GENERATED BY DEFAULT AS IDENTITY`, `VARCHAR`+`CHECK`, no `JSONB`, **no partial indexes**) so one migration set serves both engines.

---

## Concurrency — the core, now with a two-mechanism defence

**Three different problems get three deliberately different mechanisms.** That variety is the video's story; "use a lock" alone is not an answer.

### The correctness risk I found while verifying, and the fix

H2's own documentation states only that *"insert and update operations issue a **shared** lock on the table"* and never documents `SELECT … FOR UPDATE` as taking an exclusive **row** lock; H2 also rejects `FOR UPDATE` combined with `JOIN` outright. **Since H2 is the default shipped profile, a pessimistic-lock-only design would leave our headline guarantee unproven exactly where the reviewer runs it.**

So the design uses **two mechanisms layered**, and says so:

1. **Pessimistic `SELECT … FOR UPDATE`** on `show_seat` rows — clean, deterministic serialization with precise conflict reporting. This is what runs on Postgres.
2. **Optimistic `@Version` on `show_seat` — promoted to a load-bearing correctness backstop, not a nicety.** Even if an engine fails to honour `FOR UPDATE` exclusivity, the version check makes the second writer fail and map to 409. This is what makes the guarantee hold *on any engine*, including the default H2 profile.

H2 does confirm that *"if multiple connections concurrently try to lock or update the same row, the database waits until it can apply the change, but at most until the lock timeout expires"* — and since our transaction **writes** `show_seat` regardless, the write-write conflict serializes there too. Belt, braces, and a documented reason for each.

Consequences folded into the design:
- All locking queries are **single-table, by primary key** — already the design for EPQ reasons, now *also required* by H2's `FOR UPDATE + JOIN` limitation. Documented as a constraint, not a coincidence.
- `LOCK_TIMEOUT` set explicitly per profile.
- **The concurrency suite runs on both engines** (abstract base, two subclasses). The H2 run is what proves the shipped default is safe.

### Seat allocation

Every `(show, seat)` pair gets one pre-created row, `UNIQUE(show_id, seat_id)`. `show_seat.status` is the authority on occupancy; `booking_seat` is merely the record of what was sold.

Per-show seat rows are **correct, not wasteful**: you cannot take a row lock on a row that does not exist. Deriving availability by anti-join makes "C4 is free" an *absence* of rows, requiring gap locks or `SERIALIZABLE` — neither portable across both engines. Materializing turns the hardest problem in the assignment into a lock on a primary key.

Two details that separate working from correct, both worth a README paragraph:

- **Lock by primary key, never with a status predicate.** Under `READ COMMITTED` a blocked `FOR UPDATE` re-evaluates its `WHERE` on wake-up, so `WHERE status='AVAILABLE' … FOR UPDATE` silently returns *fewer* rows than asked for and destroys our ability to report which seat was lost. Check status in Java, after the lock is held.
- **Sort seat ids ascending in Java**, not just in SQL. Global lock order: `booking → show_seat (asc id) → discount_code → inserts`. Caller-side sorting makes deadlock-freedom independent of the query planner — which matters precisely because H2's executor is not Postgres's.

Isolation stays `READ_COMMITTED`: choose the weakest level and add explicit locks exactly where the invariant lives. Blocking `FOR UPDATE` (not `NOWAIT`) so the loser wakes after the winner commits, re-reads `BOOKED`, and returns a precise 409 listing `conflictingSeats` — the client re-renders with no second round trip. All-or-nothing: nobody wants 3 of 5 seats scattered across the room.

**Never hold a row lock across a network call.** The gateway call sits *between* two transactions. The failure that falls out — gateway succeeds, hold expired, seats gone — is handled explicitly: auto-refund, `PAYMENT_REVERSED`, 409 `SEAT_LOST_AFTER_PAYMENT` carrying the refund reference. ~20 lines; almost nobody handles it.

### Hold expiry — lazy is the correctness mechanism, the sweeper only converges state

> The system must remain correct with the sweeper switched off. That sentence goes in the README verbatim.

Every path holding a `show_seat` lock re-derives expiry from the `Clock` before acting; stored status is never trusted alone. This closes both traps — a seat spuriously unavailable for 15s (UX), and far worse, **an expired hold being honoured** against a customer who should have won the seat (correctness). The `@Scheduled` sweeper then runs bounded 200-row batches in separate transactions purely so stored state converges and expiry events exist.

### Discount usage caps — a single conditional UPDATE

A counter race, not a resource race. A conditional `UPDATE … WHERE used_count < limit` is the right tool: no lock ordering, no retry, one round trip. Its row-exclusive lock is held to end-of-transaction, which serializes concurrent redemptions of the same code and makes the subsequent per-user count race-free. **Order is load-bearing** — it runs *after* the seat locks, per the global order — and a comment says so.

### Async notifications — transactional outbox

`ApplicationEventPublisher` → `@TransactionalEventListener(BEFORE_COMMIT)` writes an outbox row *inside* the transaction → `@Scheduled` dispatcher drains with exponential backoff to `DEAD_LETTER`.

Publishing before commit via plain `@Async` causes two bugs worth naming: phantom confirmations for rolled-back bookings, and read-your-writes failures where the async thread queries a row its publisher hasn't committed — nondeterministic, passes locally, fails under load.

`UNIQUE(dedupe_key)` gives **exactly-once production**; delivery is at-least-once with the key passed downstream. Reminders come nearly free: a job enqueuing `SHOW_REMINDER:{bookingId}` is idempotent by construction.

An outbox is normally over-engineering for a take-home. It earns its place because retry/idempotency is explicitly graded, the `@Scheduled` infrastructure already exists for the sweeper, and — decisively — it is **observable**: `GET /admin/notifications?bookingRef=` turns "trust me, it's async" into "here it is" on camera.

---

## Data & pricing model (highlights)

- **Requirement ambiguity to call out:** the brief's "regular, premium, weekend" conflates two independent axes — seat *category* (property of the physical seat) and temporal *surcharge* (property of the show). Modelling weekend as a third category makes "a premium seat on Saturday" unrepresentable.
- **Pricing is data-driven, never `if (isWeekend)`.** Staged chain — base → per-seat rule adjustments → order discounts → fees → tax — over `pricing_rule` rows with a **closed enum** of condition types. An expression language (SpEL/Drools) is rejected: unsafe from an admin endpoint, untestable, unbounded.
- **Discounts before fees; the convenience fee is neither discounted nor refunded.** Matches real ticketing and makes the refund rule coherent.
- **`booking_charge` persists the signed, itemized breakdown** with a `refundable` flag. Recomputing a historical price from today's rules is a classic billing bug; persisting makes refunds trivially correct and auditable.
- **Refund policy = tiered rows** keyed on `min_minutes_before_show` — **minutes, not hours**; truncating to hours produces off-by-one refunds at every boundary. Resolution is **most-specific-wins** (SHOW > THEATER > CITY > GLOBAL), deliberately unlike pricing rules which compose: surcharges are additive facts, a refund policy is a single promise.
- **`booking.refund_policy_id` snapshotted at confirmation**; policy rules immutable (an "edit" deactivates and re-creates). An admin cannot retroactively change what a customer was promised.
- `UNIQUE(payment.booking_id)` / `UNIQUE(refund.booking_id)` make double-charge and double-refund *structurally impossible* rather than defended against.
- `UNIQUE(booking_id, show_seat_id)` on `booking_seat` — **not** `UNIQUE(show_seat_id)`, since book→cancel→rebook legitimately produces two rows.

---

## Enhancements over the first draft

Everything below was added after re-reading the PDF clause-by-clause and verifying load-bearing assumptions.

### A. Correctness risks found by verification
- **A1. `@Version` promoted to a load-bearing backstop** — H2 does not document `FOR UPDATE` row-exclusivity, and H2 is the default profile. Without this, the headline guarantee is unproven where the reviewer runs it.
- **A2. Concurrency suite runs on *both* H2 and Postgres** — abstract base, two subclasses. Previously Postgres-only.
- **A3. Single-table locking queries are now a stated hard constraint**, since H2 rejects `FOR UPDATE` + `JOIN`.
- **A4. `LOCK_TIMEOUT` set explicitly per profile.**
- **A5. zonky arm64 risk spiked in Phase 0** — `darwin-arm64v8` binaries were historically x86_64 Mach-O, native only from PG 17. Pin PG 17, verify before relying on it, and make the Postgres ITs **skip gracefully** (not fail) when the binary can't be fetched, so an offline reviewer still gets a green build.

### B. Requirement-coverage gaps
- **B1. Per-seat partial cancellation** — reverses the earlier "whole-booking only" decision, which contradicted the literal *"book and cancel seats"*. Order-level discount is attributed pro-rata by each seat's `adjusted_amount`, using the **largest-remainder method** so allocations sum exactly to the discount with no rounding leak. New `PARTIALLY_CANCELLED` state; cancelling the final seat collapses to `CANCELLED`.
- **B2. `booking_status_history` audit table + timeline endpoint** — resolves the "view booking history" ambiguity in both directions and yields an audit trail for free.
- **B3. Bulk seat-layout API** (rows × columns + category map) — admin "manage seat layouts" is unusable as 250 individual POSTs.
- **B4. Seat map returns per-seat price** — without it the browse→hold flow is broken, since the customer can't see tier pricing before choosing.
- **B5. Admin show-cancellation → bulk refund + notification fan-out** — realistic, and the single best demo of refund and outbox working together.
- **B6. Admin seat-blocking flow** — `BLOCKED` existed in the enum with no way to set it.
- **B7. Hold-extension endpoint** — the service had `extend` with nothing exposing it.
- **B8. Explicit guarded state machine** (`BookingStatus.canTransitionTo`) instead of scattered `if`s.
- **B9. `booking_reference` collision retry** on unique violation.

### C. Submission-deliverable gaps
- **C1. `docs/raw/`** — the PDF mandates *"all raw files used during development"*; the plan had no home for them. Holds the assignment PDFs, every prompt used, the plan file, and subagent decision outputs.
- **C2. `docs/VIDEO_SCRIPT.md`** — a timed storyboard. Four mandated topics in ≤10 minutes is genuinely tight and needs rehearsing, not improvising.
- **C3. Requirements Traceability Matrix** in the README — clause → implementation → proving test.
- **C4. Committed `docs/openapi.yaml`** — API reviewable without running anything.
- **C5. Mermaid ER + booking sequence diagrams** — render natively on GitHub, cost nothing.
- **C6. Enumerated ADR index.**

### D. "At scale" — currently asserted nowhere
- **D1. Seat map served by a projection query**, not by hydrating 250 entities.
- **D2. N+1 detection tests** asserting query counts on booking-detail and seat-map.
- **D3. Pagination defaults and a max page-size cap** (reject `size=100000`).
- **D4. Documented index rationale** plus a measured throughput number from the demo script.

### E. Test quality, not just test count
- **E1. JaCoCo** coverage report with a printed summary.
- **E2. PIT mutation testing scoped narrowly to `pricing` + `refund`** — proves the money tests actually assert something. Strong senior signal, bounded cost.
- **E3. Test data builders** so fixtures don't sprawl.

### F. Stack currency
- **F1. Spring Boot 4.1.x, not 3.4** — with a Phase-0 spike, because Boot 4 ships **Jackson 3** (affects the BigDecimal-plain config) and **Spring Security 7** (changed config DSL).
- **F2. Virtual threads deliberately *not* enabled**, with the reasoning documented.

### G. Operability and demo affordances
- **G1. Admin endpoints to force the sweeper and reminder jobs** — the video cannot wait 15 seconds, let alone 2 hours.
- **G2. Configurable reminder lead time.**
- **G3. JWT secret via env var** with a documented dev default.
- **G4. Spotless + google-java-format + `.editorconfig`.**

---

## Phases & commit plan (~24 commits, Conventional Commits, all on `main`)

Each commit compiles and is test-green. The first four are the AI-tooling commits, so `git log` itself shows *guardrails first, then build*.

### Phase 0 — Toolchain, spikes, AI guardrails (~3h, no feature code)
`brew install openjdk@25 maven`; repo-local git identity; `git init`.
**Two spikes before committing to the stack:** (a) Spring Boot 4.1 + Jackson 3 + Security 7 + springdoc 3.1.1 wire together; (b) zonky PG 17 arm64 actually boots on this machine. Both have a documented fallback (Boot 3.5.x; H2-only with `@Version` carrying the guarantee).

| # | Commit |
|---|---|
| 1 | `chore: scaffold Spring Boot 4.1 project with Maven wrapper` |
| 2 | `docs: add AGENTS.md and CLAUDE.md engineering constitution` |
| 3 | `chore(ai): add skills for conventions, migrations, and API contract` |
| 4 | `chore(ai): add concurrency-testing skill and reviewer/auditor subagents` |

**`AGENTS.md`** is the vendor-neutral primary file (layout, locking rules, Clock/BigDecimal discipline, error contract, definition-of-done); **`CLAUDE.md`** is thin and points at it. Tool-agnostic framing reads as engineering rigour rather than Claude-specific ceremony.

**Skills** (`.claude/skills/`): `spring-boot-conventions`, `flyway-migrations`, `api-contract`, `concurrency-testing`.
**Subagents** (`.claude/agents/`): `spring-reviewer` and `concurrency-auditor` (hunts four specific traps: status predicate inside a locking query, lock-ordering violations, network calls inside transactions, events published pre-commit). Both run against every concurrency commit — that's the multi-agent workflow to demo.

### Phase 1 — Foundation (~4h)
| # | Commit |
|---|---|
| 5 | `feat(common): Clock bean, auditing, ErrorCode registry, ProblemDetail handler` |
| 6 | `feat(identity): JWT auth, users, roles, BCrypt` |
| 7 | `feat(catalog): cities, theaters, screens, movies + bulk seat-layout API` |

### Phase 2 — Shows (~3h)
| # | Commit |
|---|---|
| 8 | `feat(show): shows, per-category pricing, show-seat provisioning` |
| 9 | `feat(show): seat map with per-seat pricing and lazy hold expiry` |

### Phase 3 — The core (~6h; do not compress)
| # | Commit |
|---|---|
| 10 | `feat(booking): seat holds with pessimistic locking and optimistic backstop` |
| 11 | `test(booking): prove no double-allocation under contention on H2 and Postgres` |
| 12 | `test(booking): prove lock ordering prevents deadlock on reversed seat sets` |
| 13 | `feat(booking): hold expiry sweeper with bounded batches` |

### Phase 4 — Money (~6h)
| # | Commit |
|---|---|
| 14 | `feat(pricing): data-driven rule engine and dry-run quote endpoint` |
| 15 | `feat(pricing): discount codes with usage caps and stacking rules` |
| 16 | `feat(booking): booking creation, charge breakdown, idempotency keys` |
| 17 | `feat(payment): gateway port, confirmation, seat-lost compensation` |
| 18 | `feat(refund): configurable policies, partial per-seat cancellation` |

### Phase 5 — Async, lifecycle & proof (~5h)
| # | Commit |
|---|---|
| 19 | `feat(notification): transactional outbox, dispatcher with backoff, reminders` |
| 20 | `feat(show): admin show cancellation with bulk refund and fan-out` |
| 21 | `feat(booking): status history timeline and guarded state machine` |
| 22 | `test(arch): ArchUnit boundaries, JaCoCo, mutation testing on pricing` |

### Phase 6 — The deliverable layer (~5h, never cut)
| # | Commit |
|---|---|
| 23 | `feat(demo): seed data, demo profile, and contention demo script` |
| 24 | `docs: README, traceability matrix, ADRs, diagrams, and raw development files` |

---

## The test that proves the central claim

`ConcurrentSeatHoldIT`, run on **both** engines. Five details that separate a test that proves something from one that only looks like it does — encoded in the `concurrency-testing` skill and quoted in the README:

1. **Two latches, not one.** A `ready` latch ensures all 32 threads are scheduled, JIT-warm, and holding a connection *before* the start gun. With only a start gun, thread 1 finishes before thread 32 is scheduled and the test passes trivially.
2. **Hikari `maximum-pool-size` ≥ thread count.** With the default pool of 10, 22 threads block on *connection acquisition* rather than the row lock — you would be testing HikariCP. The most common way this test silently proves nothing.
3. **The test method must not be `@Transactional`.** Setup commits via `TransactionTemplate`, or the workers cannot see the show at all.
4. **Assert against the database, not in-process counters.** Counters can agree while the DB is corrupt.
5. **Assert the unexpected-exception queue is empty.** A test checking only `wins == 1` passes when the other 31 threads die of deadlock.

Variants: single-seat contention; overlapping multi-seat (loser leaves *zero* holds); **reversed-order deadlock test over 200 iterations**; 400-thread stress across 50 seats; expired-hold reclaim race; double-submit confirm.

Beyond that: unit tests for pricing/refund boundaries with a fixed `Clock`, `@WebMvcTest` slices for the error contract, `@DataJpaTest` for constraints, N+1 query-count tests, and `OutboxRollbackIT` — which *proves* the `BEFORE_COMMIT` atomicity claim rather than asserting it in prose.

`mvn test` = fast H2 suite, no network. `mvn verify` = adds the embedded-Postgres suite, skipping gracefully if the binary is unavailable.

---

## Highest-leverage extras (ranked by impact ÷ cost)

1. **Requirements traceability matrix** — highest impact per minute in the entire submission.
2. **springdoc Swagger UI** — converts a reviewer who *reads* code into one who *uses* the system. One README line pre-empts the objection: generated API docs are not a product frontend.
3. **Seed data + `scripts/demo.sh`** — ends with 20 parallel curls at one seat printing `1 × 201, 19 × 409`. The most persuasive artifact in the repo, and it makes the video possible without live-coding.
4. **ArchUnit + mutation testing** — turns discipline from aspirational into enforced, and proves the tests assert something.
5. **README + ADRs** — the decisions table, "explicitly out of scope and why", "what changes at 10x/100x".

**Deliberately rejected, and saying so earns credit:** Redis locks (without fencing tokens, *worse* than a DB row lock, which commits atomically with the data it protects); Kafka; caching the seat map (actively wrong — serves stale availability straight into guaranteed 409s); rate limiting; WebSockets; GraphQL/CQRS; microservice split.

---

## AI features — parked backlog (your question, answered)

**Is it advisable?** Yes, but only as a clearly-labelled extension *after* the core is green — which is what you decided. Honest read on your three original ideas:

- **ETA-based start-time nudge — the strongest.** It plugs into the outbox/reminder machinery already being built, so it extends an *in-scope* requirement rather than bolting on a new one. Keep travel-time injectable so no live maps API is needed to demo.
- **Snack suggestions and parking/theatre wayfinding — weakest.** Both need concession inventory and theatre-layout data we'd have to invent. Without that modelling they demo as plausible-sounding filler, which reads as padding next to a rigorous core.

**Candidates I'd rank above two of the originals**, for discussion when we get there:

| Idea | Why it fits |
|---|---|
| **NL → pricing rule for admins** ("20% off weekends on premium seats in Bengaluru") | Best fit by far: `pricing_rule` is already data-driven with a *closed* enum of conditions, so the model's output space is small and **fully validatable** before persisting. Shown for confirmation, never auto-applied. |
| **Refund explainer** | Plain-English explanation of exactly what refund applies and why, grounded entirely in our own computed numbers — near-zero hallucination surface. |
| **Natural-language show search** | Most demo-friendly; uses only catalog data. |
| **Seat recommendation rationale** | Allocation stays deterministic; the model only narrates *why* those seats are good — correctness never leaves our code. |

Design rule for all: a **port interface with a deterministic stub used in every test**, so the suite stays fast and offline and the core is never hostage to an API key.

---

## Verification

1. `mvn test` — H2 suite green, no network, no Docker. **Includes the concurrency proof on the default profile.**
2. `mvn verify` — adds embedded-Postgres ITs; the reversed-order deadlock test must be clean.
3. `mvn spring-boot:run -Dspring-boot.run.profiles=demo` then `scripts/demo.sh` — login → seat map with prices → hold → quote with discount → book → pay → breakdown → cancel one seat → partial refund → cancel show → bulk refund, ending with the 20-way contention burst printing exactly one 201.
4. Swagger UI at `/swagger-ui.html` with the seeded admin token.
5. `GET /admin/notifications?bookingRef=` — the async confirmation that was actually produced.
6. **Deliberately break lock ordering, watch the deadlock test fail, restore it, watch it pass.** Thirty seconds of video worth more than any feature.

## Open items

- **git `user.name` and `user.email`** — needed to commit (set repo-local).
- **GitHub repo** — no `gh` CLI; either create the empty repo in the browser and give me the URL, or approve installing `gh`.
- Repo/package name: **`cineagent`** (cinema + agent — signals the domain and the AI-agent-driven build), package `com.cineagent`. Decided.
