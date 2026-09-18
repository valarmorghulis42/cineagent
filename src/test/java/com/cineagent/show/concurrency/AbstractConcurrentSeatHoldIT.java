package com.cineagent.show.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import com.cineagent.catalog.api.dto.SeatLayoutDtos.BulkSeatLayoutRequest;
import com.cineagent.catalog.api.dto.SeatLayoutDtos.RowSpec;
import com.cineagent.catalog.domain.City;
import com.cineagent.catalog.domain.Movie;
import com.cineagent.catalog.domain.Screen;
import com.cineagent.catalog.domain.Seat;
import com.cineagent.catalog.domain.SeatCategory;
import com.cineagent.catalog.domain.Theater;
import com.cineagent.catalog.repository.CityRepository;
import com.cineagent.catalog.repository.MovieRepository;
import com.cineagent.catalog.repository.ScreenRepository;
import com.cineagent.catalog.repository.SeatRepository;
import com.cineagent.catalog.repository.TheaterRepository;
import com.cineagent.catalog.service.SeatLayoutService;
import com.cineagent.common.error.ConflictException;
import com.cineagent.common.error.ErrorCode;
import com.cineagent.identity.domain.Role;
import com.cineagent.identity.domain.User;
import com.cineagent.identity.repository.UserRepository;
import com.cineagent.show.api.dto.ShowDtos.CreateShowRequest;
import com.cineagent.show.api.dto.ShowDtos.PriceSpec;
import com.cineagent.show.domain.Show;
import com.cineagent.show.service.SeatHoldService;
import com.cineagent.show.service.ShowService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The abstract base for the single most-scrutinized test in this submission — see the
 * {@code concurrency-testing} skill and AGENTS.md §4/§7. Every rule below exists because omitting
 * it lets a test pass while proving nothing:
 *
 * <ul>
 *   <li>Two latches ({@code ready} + {@code startGun}), not one — otherwise thread 1 can finish
 *       before thread 32 is even scheduled.
 *   <li>Setup is committed via {@link TransactionTemplate}, and this class's test methods are
 *       deliberately NOT {@code @Transactional} — worker threads run on their own connections and
 *       must see committed data, not a test-rollback sandbox.
 *   <li>Assertions run against the database via {@link JdbcTemplate}, not just in-process
 *       counters.
 *   <li>Every unexpected exception (anything that isn't the expected {@link ConflictException}
 *       loss path) is collected and asserted empty — a test that only checks the winner count
 *       would pass even if the losers died of deadlocks instead of clean 409s.
 * </ul>
 *
 * <p>Subclasses wire a different {@code DataSource} (H2 default profile vs. real embedded
 * Postgres) but share every test method here — see {@code ConcurrentSeatHoldH2IT} and
 * {@code ConcurrentSeatHoldPostgresIT}. Never let one engine's green run stand in for the other's
 * (AGENTS.md §7): H2 does not document {@code FOR UPDATE} as a true exclusive row lock, so only
 * the Postgres run proves the pessimistic path; only the H2 run proves the profile a reviewer
 * actually launches.
 */
public abstract class AbstractConcurrentSeatHoldIT {

  @Autowired private CityRepository cityRepository;
  @Autowired private TheaterRepository theaterRepository;
  @Autowired private ScreenRepository screenRepository;
  @Autowired private MovieRepository movieRepository;
  @Autowired private SeatRepository seatRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private ShowService showService;
  @Autowired private SeatHoldService seatHoldService;
  @Autowired private SeatLayoutService seatLayoutService;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private javax.sql.DataSource dataSource;

  private JdbcTemplate jdbc;

  private JdbcTemplate jdbc() {
    if (jdbc == null) {
      jdbc = new JdbcTemplate(dataSource);
    }
    return jdbc;
  }

  private TransactionTemplate tx() {
    return new TransactionTemplate(transactionManager);
  }

  /** Creates a fresh show with {@code seatCount} seats (single row "A") and returns it, committed. */
  private Show createShowWithSeats(int seatCount) {
    return tx()
        .execute(
            status -> {
              // Unique per call — this method runs dozens of times per test class run (each
              // reversed-order/overlapping iteration creates its own show), and city has a
              // UNIQUE(name, state) constraint.
              String suffix = java.util.UUID.randomUUID().toString();
              City city =
                  cityRepository.save(new City("TestCity-" + suffix, "TestState-" + suffix, "IN", "Asia/Kolkata"));
              Theater theater = theaterRepository.save(new Theater(city, "Test Theater", "1 Main St"));
              Screen screen = screenRepository.save(new Screen(theater, "Screen 1"));
              Movie movie =
                  movieRepository.save(
                      new Movie("Test Movie", "en", 120, "U", "synopsis", LocalDate.now()));
              seatLayoutService.createLayout(
                  screen.getId(),
                  new BulkSeatLayoutRequest(List.of(new RowSpec("A", seatCount, SeatCategory.REGULAR))));

              Instant startsAt = Instant.now().plus(1, ChronoUnit.DAYS);
              Instant endsAt = startsAt.plus(2, ChronoUnit.HOURS);
              return showService.create(
                  new CreateShowRequest(
                      screen.getId(),
                      movie.getId(),
                      startsAt,
                      endsAt,
                      null,
                      null,
                      List.of(new PriceSpec(SeatCategory.REGULAR, new BigDecimal("250.00")))));
            });
  }

  private List<Seat> seatsForShow(Show show) {
    return seatRepository.findByScreenIdAndActiveTrueOrderByRowLabelAscSeatNumberAsc(
        show.getScreen().getId());
  }

  private List<Long> createUsers(int n) {
    return tx()
        .execute(
            status -> {
              List<Long> ids = new java.util.ArrayList<>();
              for (int i = 0; i < n; i++) {
                User u =
                    userRepository.save(
                        new User(
                            "concurrency-test-" + java.util.UUID.randomUUID() + "@test.local",
                            "x",
                            "Test User " + i,
                            null,
                            EnumSet.of(Role.CUSTOMER)));
                ids.add(u.getId());
              }
              return ids;
            });
  }

  // --- Variant 1: single-seat contention -----------------------------------------------------

  @Test
  void singleSeatContention_exactlyOneWinner() throws InterruptedException {
    int threads = 32;
    Show show = createShowWithSeats(1);
    Long seatId = seatsForShow(show).get(0).getId();
    List<Long> userIds = createUsers(threads);

    CountDownLatch ready = new CountDownLatch(threads);
    CountDownLatch startGun = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(threads);
    AtomicInteger wins = new AtomicInteger();
    AtomicInteger losses = new AtomicInteger();
    Queue<Throwable> unexpected = new ConcurrentLinkedQueue<>();

    ExecutorService pool = Executors.newFixedThreadPool(threads);
    for (int i = 0; i < threads; i++) {
      Long userId = userIds.get(i);
      pool.submit(
          () -> {
            try {
              ready.countDown();
              startGun.await();
              seatHoldService.acquire(show.getId(), List.of(seatId), userId);
              wins.incrementAndGet();
            } catch (ConflictException e) {
              if (e.getErrorCode() == ErrorCode.SEAT_UNAVAILABLE) {
                losses.incrementAndGet();
              } else {
                unexpected.add(e);
              }
            } catch (Throwable t) {
              unexpected.add(t);
            } finally {
              done.countDown();
            }
          });
    }

    assertThat(ready.await(10, TimeUnit.SECONDS)).as("all workers scheduled and waiting").isTrue();
    startGun.countDown();
    assertThat(done.await(30, TimeUnit.SECONDS)).as("all workers finished").isTrue();
    pool.shutdown();

    assertThat(unexpected).as("no deadlocks or unexpected exceptions").isEmpty();
    assertThat(wins.get()).isEqualTo(1);
    assertThat(losses.get()).isEqualTo(threads - 1);
    assertThat(wins.get() + losses.get()).isEqualTo(threads);

    Integer activeHoldsOnSeat =
        jdbc()
            .queryForObject(
                "select count(*) from seat_hold h join show_seat ss on ss.hold_id = h.id "
                    + "where ss.seat_id = ? and h.status = 'ACTIVE'",
                Integer.class,
                seatId);
    assertThat(activeHoldsOnSeat).as("exactly one ACTIVE hold on the contended seat").isEqualTo(1);
  }

  // --- Variant 2: overlapping multi-seat, loser leaves zero holds ----------------------------

  @Test
  void overlappingMultiSeat_loserLeavesZeroHolds() throws InterruptedException {
    int iterations = 20;
    for (int iter = 0; iter < iterations; iter++) {
      Show show = createShowWithSeats(5); // A1..A5
      List<Long> seatIds = seatsForShow(show).stream().map(Seat::getId).toList();
      List<Long> userIds = createUsers(2);

      List<Long> t1Seats = List.of(seatIds.get(0), seatIds.get(1), seatIds.get(2)); // A,B,C
      List<Long> t2Seats = List.of(seatIds.get(2), seatIds.get(3), seatIds.get(4)); // C,D,E

      CountDownLatch ready = new CountDownLatch(2);
      CountDownLatch startGun = new CountDownLatch(1);
      CountDownLatch done = new CountDownLatch(2);
      AtomicInteger wins = new AtomicInteger();
      AtomicInteger losses = new AtomicInteger();
      Queue<Throwable> unexpected = new ConcurrentLinkedQueue<>();

      ExecutorService pool = Executors.newFixedThreadPool(2);
      pool.submit(worker(show, t1Seats, userIds.get(0), ready, startGun, done, wins, losses, unexpected));
      pool.submit(worker(show, t2Seats, userIds.get(1), ready, startGun, done, wins, losses, unexpected));

      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      startGun.countDown();
      assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
      pool.shutdown();

      assertThat(unexpected).as("iteration " + iter + ": no deadlocks/unexpected").isEmpty();
      assertThat(wins.get()).as("iteration " + iter).isEqualTo(1);
      assertThat(losses.get()).as("iteration " + iter).isEqualTo(1);

      // All-or-nothing: the loser must have left ZERO holds behind, not a partial 2-of-3.
      Integer totalActiveHoldsForShow =
          jdbc()
              .queryForObject(
                  "select count(*) from seat_hold where show_id = ? and status = 'ACTIVE'",
                  Integer.class,
                  show.getId());
      assertThat(totalActiveHoldsForShow).as("iteration " + iter + ": exactly one hold row exists").isEqualTo(1);

      Integer activeShowSeats =
          jdbc()
              .queryForObject(
                  "select count(*) from show_seat where show_id = ? and status = 'HELD'",
                  Integer.class,
                  show.getId());
      // The winner holds exactly 3 seats; the loser's request never touched storage at all.
      assertThat(activeShowSeats).as("iteration " + iter + ": exactly 3 seats HELD (the winner's)").isEqualTo(3);
    }
  }

  // --- Variant 3: reversed-order requests never deadlock --------------------------------------

  @Test
  void reversedOrderRequests_noDeadlock() throws InterruptedException {
    int iterations = 50;
    for (int iter = 0; iter < iterations; iter++) {
      Show show = createShowWithSeats(2); // A1, A2
      List<Long> seatIds = seatsForShow(show).stream().map(Seat::getId).toList();
      Long seatA = seatIds.get(0);
      Long seatB = seatIds.get(1);
      List<Long> userIds = createUsers(2);

      CountDownLatch ready = new CountDownLatch(2);
      CountDownLatch startGun = new CountDownLatch(1);
      CountDownLatch done = new CountDownLatch(2);
      AtomicInteger wins = new AtomicInteger();
      AtomicInteger losses = new AtomicInteger();
      Queue<Throwable> unexpected = new ConcurrentLinkedQueue<>();

      ExecutorService pool = Executors.newFixedThreadPool(2);
      // T1 requests [A, B] in that order; T2 requests [B, A] — REVERSED. Both get sorted
      // ascending by SeatHoldService before locking (AGENTS.md §4.4), so this must never deadlock
      // regardless of caller-supplied order.
      pool.submit(
          worker(show, List.of(seatA, seatB), userIds.get(0), ready, startGun, done, wins, losses, unexpected));
      pool.submit(
          worker(show, List.of(seatB, seatA), userIds.get(1), ready, startGun, done, wins, losses, unexpected));

      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      startGun.countDown();
      assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
      pool.shutdown();

      assertThat(unexpected)
          .as("iteration " + iter + ": zero deadlocks — ascending-id lock order must prevent this")
          .isEmpty();
      assertThat(wins.get()).as("iteration " + iter).isEqualTo(1);
      assertThat(losses.get()).as("iteration " + iter).isEqualTo(1);
    }
  }

  private Runnable worker(
      Show show,
      List<Long> seatIds,
      Long userId,
      CountDownLatch ready,
      CountDownLatch startGun,
      CountDownLatch done,
      AtomicInteger wins,
      AtomicInteger losses,
      Queue<Throwable> unexpected) {
    return () -> {
      try {
        ready.countDown();
        startGun.await();
        seatHoldService.acquire(show.getId(), seatIds, userId);
        wins.incrementAndGet();
      } catch (ConflictException e) {
        if (e.getErrorCode() == ErrorCode.SEAT_UNAVAILABLE) {
          losses.incrementAndGet();
        } else {
          unexpected.add(e);
        }
      } catch (Throwable t) {
        unexpected.add(t);
      } finally {
        done.countDown();
      }
    };
  }
}
