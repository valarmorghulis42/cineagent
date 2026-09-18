---
name: concurrency-testing
description: Use before writing, reviewing, or debugging any test that exercises concurrent seat holds, bookings, discount redemption, or the outbox. Covers the five rules that separate a concurrency test that actually proves the no-double-booking guarantee from one that only looks like it does. Read before writing a test with threads, latches, or an ExecutorService.
---

# Concurrency testing — CineAgent

The no-double-booking guarantee (`AGENTS.md` §4.3–4.6) is the single most-scrutinized claim in
this submission. A concurrency test that *looks* right but silently proves nothing is the single
most common failure mode in take-homes like this one. This skill is the checklist that prevents
that — read it before writing or reviewing `ConcurrentSeatHoldIT` or any sibling test.

## The five rules

**1. Two latches, not one.** A `ready` `CountDownLatch` that every worker thread counts down as
soon as it's running, awaited by the test thread, *before* releasing a second `startGun` latch
that the workers are all blocked on. With only a `startGun`, thread 1 can finish its entire
request before thread 32 has even been scheduled by the OS — the test "passes" without any real
contention ever happening.

```java
CountDownLatch ready = new CountDownLatch(THREADS);
CountDownLatch startGun = new CountDownLatch(1);
CountDownLatch done = new CountDownLatch(THREADS);
// each worker: ready.countDown(); startGun.await(); <do the request>; done.countDown();
assertThat(ready.await(10, SECONDS)).isTrue();   // all threads warm and waiting
startGun.countDown();                             // release them together
assertThat(done.await(30, SECONDS)).isTrue();
```

**2. HikariCP `maximum-pool-size` must be ≥ the thread count.** With the Spring Boot default pool
of 10, a 32-thread test has 22 threads blocking on *connection acquisition*, not on the row lock
— you are load-testing HikariCP, not the locking design, and the test can pass or fail for
reasons unrelated to the code under test. Set `spring.datasource.hikari.maximum-pool-size=40` (or
higher than your max thread count) in the `it` test profile specifically.

**3. The test method itself must never be `@Transactional`.** If the JUnit test method carries
`@Transactional`, its own setup transaction is never committed (Spring rolls test transactions
back by default), so the worker threads — each on their own connection, each seeing only
committed data — cannot see the show/seats the test just "created." Commit setup explicitly via
`TransactionTemplate` (or a non-transactional setup service call) before spawning workers.

**4. Assert against the database, not just in-process counters.** An `AtomicInteger wins` that
reads `1` can still coexist with a corrupted database if the counter and the actual persisted
state have drifted for any reason (a bug in the test harness itself, a swallowed exception, etc).
The assertion that actually matters queries the database directly:

```java
assertThat(jdbc.queryForObject(
    "select count(*) from seat_hold h join show_seat ss on ss.hold_id = h.id " +
    "where ss.id = ? and h.status = 'ACTIVE'", Integer.class, showSeatId)).isEqualTo(1);
```

**5. Assert the unexpected-exception queue is empty.** Collect anything that isn't the expected
`SeatUnavailableException` loss path into a `ConcurrentLinkedQueue<Throwable>` and assert it's
empty. A test that only checks `wins == 1` will happily pass when 31 threads die of a deadlock or
a lock-timeout exception instead of cleanly losing with a 409 — that is a real bug the test would
otherwise hide.

```java
Queue<Throwable> unexpected = new ConcurrentLinkedQueue<>();
// in each worker's catch block: catch (SeatUnavailableException e) { conflicts++ }
//                                catch (Throwable t) { unexpected.add(t); }
assertThat(unexpected).as("no deadlocks or lock timeouts").isEmpty();
```

## Run on both engines — don't let one subsume the other

Per `AGENTS.md` §4.5 and §7: H2 does not document `SELECT ... FOR UPDATE` as taking a true
exclusive row lock, so the `@Version` optimistic check is what actually guarantees correctness on
H2 (the default runtime profile). The concurrency suite must therefore run on **both** H2 and real
Postgres (via `zonky` embedded-postgres, no Docker) — structure it as an abstract base test class
with the shared test methods, and two thin subclasses that each wire a different `DataSource`.
Passing on Postgres alone leaves the shipped default unproven; passing on H2 alone leaves the
"real production engine" claim unproven. Both must be green.

## Variants worth writing, in increasing order of what they prove

1. **Single-seat contention** — `THREADS` workers race for one seat; exactly 1 wins.
2. **Overlapping multi-seat** — T1 wants `{A,B,C}`, T2 wants `{C,D,E}`; exactly one wins, and the
   loser leaves **zero** holds behind (proves all-or-nothing, no partial allocation).
3. **Reversed-order deadlock test** — T1 wants `{A,B}`, T2 wants `{B,A}`, repeated ~200 times;
   asserts zero `DeadlockLoserDataAccessException` / `CannotAcquireLockException`. This is the one
   that actually exercises the ascending-id lock-ordering rule in `AGENTS.md` §4.4 — reversing the
   caller-side sort should make this test fail, which is worth doing once, on purpose, to confirm
   the test isn't vacuously green.
4. **Stress across many seats** — e.g. 400 threads across 50 seats, 8 threads contending per
   seat; asserts exactly 50 winners total and no seat has two active holds.
5. **Expired-hold reclaim race** — a hold is logically expired (via the injected `Clock`, not a
   real sleep) and multiple challengers race to reclaim the seat; exactly one wins, and the
   original hold transitions to `EXPIRED` exactly once.
6. **Double-submit confirm** — two threads attempt to confirm the same hold concurrently; exactly
   one `Booking` and one `Payment` row exist afterward.

## Timing must be `Clock`-driven, never `Thread.sleep()`

Any test involving hold or booking expiry uses an injectable/advanceable `Clock` (see
`AGENTS.md` §4.1) to move time forward deterministically. A test that sleeps for real seconds to
wait out a TTL is slow, flaky under CI/load, and is a signal the production code isn't actually
using `Clock` correctly if the test can't fake time.
