---
name: spring-boot-conventions
description: Use when writing or reviewing any Spring Boot code in this repo — entities, repositories, services, controllers, DTOs, or configuration. Covers package placement, transaction boundaries, DTO/entity separation, dependency injection style, and the module dependency rule. Read before creating a new class in any com.cineagent.* package.
---

# Spring Boot conventions — CineAgent

This expands `AGENTS.md` §3–4 into concrete per-class rules. Read `AGENTS.md` first for *why*;
this skill is the *how* checklist when actually writing a class.

## Where a class goes

Every feature package (`booking`, `show`, `pricing`, `payment`, `refund`, `notification`,
`catalog`, `identity`) has this internal shape — not every feature needs every subpackage:

```
com.cineagent.<feature>/
├── api/              controllers + request/response DTOs (records)
│   └── dto/
├── domain/           JPA entities + enums
├── repository/        Spring Data repositories, including locking queries
├── service/           business logic, transaction boundaries
└── event/             domain events published via ApplicationEventPublisher (if any)
```

`common/` has no `api/` — it is pure shared kernel (config, error types, security, money helpers,
the `BaseEntity` audit superclass).

## The cross-feature dependency rule

A feature may import another feature's `service`, `domain`, or `dto` types. **Never** import
another feature's `repository` package from outside that feature — that repository is private to
its owning feature's services. If `booking` needs to look up show seats, it calls
`ShowSeatQueryService` (in `show.service`), not `ShowSeatRepository` directly.

The allowed call graph, do not add new edges without updating `AGENTS.md`:
```
booking → show, pricing, payment, refund, identity
pricing → catalog
notification → nothing (it only listens to events)
```

## Entities

- Extend `common.persistence.BaseEntity` (`id`, `createdAt`, `updatedAt`, `createdBy`,
  `updatedBy` via `@EntityListeners(AuditingEntityListener.class)`).
- Every `@ManyToOne` / `@OneToOne` is `FetchType.LAZY`. There is no exception to this in this
  codebase — if a read path needs the associated data, use a projection or an explicit fetch
  join in the repository query, not eager loading.
- Enums are `@Enumerated(EnumType.STRING)` always, with a matching Flyway `CHECK` constraint.
  `ORDINAL` is banned — a reordered enum constant would silently corrupt stored data.
- Money fields are `BigDecimal` with `columnDefinition = "NUMERIC(12,2)"`. Never `double`/`float`.
- Timestamp fields are `Instant`, column type `TIMESTAMP WITH TIME ZONE`.

## DTOs

- Request and response DTOs are `record`s in `api/dto/`. They never leak into `service/` or
  `repository/` — services take and return domain objects or purpose-built value records, and
  the controller maps to/from the DTO.
- Bean Validation annotations live on the DTO (`@NotNull`, `@Size`, `@DecimalMin`, `@Future`,
  etc.) for syntactic checks only. A DTO is never where a business rule ("is this show
  bookable?") is checked — that belongs in the service layer as a `BusinessException`.
- Give DTOs a static factory (`static BookingResponse from(Booking b, List<BookingSeat> seats)`)
  rather than a mapping library — at this project's size a mapper dependency buys nothing.

## Services and transaction boundaries

- `@Service` classes hold `@Transactional` boundaries. A controller method is never
  `@Transactional` itself — it delegates to exactly one service method that owns the transaction
  (or explicitly composes two, as booking→payment does across two transactions on purpose — see
  `AGENTS.md` §4.6).
- Default transaction isolation is `READ_COMMITTED` (Postgres/H2 default) — do not raise it
  per-method. If a stronger guarantee is needed, add an explicit `SELECT ... FOR UPDATE` lock
  instead of raising isolation (see `AGENTS.md` §4.3–4.5).
- Inject `Clock` and repositories via constructor injection (Lombok `@RequiredArgsConstructor`
  on a `final`-field class, or an explicit constructor). No field injection (`@Autowired` on a
  field) anywhere in the codebase.
- A service method that calls out to a port (`PaymentGateway`, `NotificationSender`) must not do
  so from inside a transaction that holds a row lock — see `AGENTS.md` §4.6. If in doubt, split
  the method into two transactions with the I/O call in between, as the booking→payment flow does.

## Repositories

- Spring Data JPA interfaces in `repository/`. Locking queries use
  `@Lock(LockModeType.PESSIMISTIC_WRITE)` with an explicit `@QueryHint` for
  `jakarta.persistence.lock.timeout`, and are single-table, by primary key — see `AGENTS.md` §4.3.
- Prefer a derived query or `@Query` with JPQL for simple lookups; drop to a native `@Query` only
  for the conditional-`UPDATE` patterns (discount usage caps, outbox claiming) that need a single
  atomic round trip — see `AGENTS.md` and the `api-contract` skill.
- No `@Query` returning a full entity graph where a projection interface or DTO projection would
  do — the seat map and booking-list endpoints are read-hot and must not hydrate full entity
  graphs. Use a Spring Data projection interface or a constructor-expression JPQL query.

## Configuration

- All cross-cutting beans live in `common/config/`: `ClockConfig` (`Clock` bean —
  `Clock.systemUTC()` in real profiles, an overridable fixed/mutable clock in tests),
  `AsyncConfig`, `SchedulingConfig`, `JpaAuditingConfig`, `JacksonConfig`
  (`WRITE_BIGDECIMAL_AS_PLAIN`, ISO-8601 `Instant`), `OpenApiConfig`.
- `spring.jpa.open-in-view=false` is set in every `application-*.properties`/`.yml` profile.
  Never remove it, never rely on lazy-loading working outside a transaction — if a controller or
  DTO mapper touches a lazy association, that association must already have been fetched inside
  the owning service's transaction.
