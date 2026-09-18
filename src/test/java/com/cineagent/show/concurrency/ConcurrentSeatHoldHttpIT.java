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
import com.cineagent.identity.api.dto.AuthResponse;
import com.cineagent.identity.api.dto.RegisterRequest;
import com.cineagent.show.api.dto.HoldDtos.CreateHoldRequest;
import com.cineagent.show.api.dto.ShowDtos.CreateShowRequest;
import com.cineagent.show.api.dto.ShowDtos.PriceSpec;
import com.cineagent.show.domain.Show;
import com.cineagent.show.service.ShowService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A service-layer test proves the lock serializes; it does NOT prove the loser actually gets a
 * clean {@code 409}, as opposed to (say) an unhandled exception surfacing as a {@code 500}
 * through {@link com.cineagent.common.error.GlobalExceptionHandler} — that mapping is exactly
 * what {@code scripts/demo.sh} shows on camera, so it must be pinned by a test rather than left
 * to the demo to discover (AGENTS.md §7). This is that test: real HTTP, real JWTs, real
 * concurrent requests hitting the running Spring MVC dispatcher.
 */
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.datasource.hikari.maximum-pool-size=40",
      "app.scheduling.enabled=false"
    })
@AutoConfigureTestRestTemplate
class ConcurrentSeatHoldHttpIT {

  @LocalServerPort private int port;
  @Autowired private TestRestTemplate rest;

  @Autowired private CityRepository cityRepository;
  @Autowired private TheaterRepository theaterRepository;
  @Autowired private ScreenRepository screenRepository;
  @Autowired private MovieRepository movieRepository;
  @Autowired private SeatRepository seatRepository;
  @Autowired private SeatLayoutService seatLayoutService;
  @Autowired private ShowService showService;
  @Autowired private PlatformTransactionManager transactionManager;

  @Test
  void singleSeatContentionOverHttp_exactlyOne201_restAre409NeverA500() throws InterruptedException {
    int threads = 20;

    Show show =
        new TransactionTemplate(transactionManager)
            .execute(
                status -> {
                  City city = cityRepository.save(new City("HttpTestCity", "S", "IN", "Asia/Kolkata"));
                  Theater theater = theaterRepository.save(new Theater(city, "HTTP Test Theater", "1 St"));
                  Screen screen = screenRepository.save(new Screen(theater, "Screen 1"));
                  Movie movie =
                      movieRepository.save(new Movie("HTTP Test Movie", "en", 120, "U", "syn", LocalDate.now()));
                  seatLayoutService.createLayout(
                      screen.getId(),
                      new BulkSeatLayoutRequest(List.of(new RowSpec("A", 1, SeatCategory.REGULAR))));
                  Instant startsAt = Instant.now().plus(1, ChronoUnit.DAYS);
                  return showService.create(
                      new CreateShowRequest(
                          screen.getId(),
                          movie.getId(),
                          startsAt,
                          startsAt.plus(2, ChronoUnit.HOURS),
                          null,
                          null,
                          List.of(new PriceSpec(SeatCategory.REGULAR, new BigDecimal("250.00")))));
                });
    Long showId = show.getId();
    Long seatId =
        seatRepository
            .findByScreenIdAndActiveTrueOrderByRowLabelAscSeatNumberAsc(show.getScreen().getId())
            .get(0)
            .getId();

    List<String> tokens = registerUsers(threads);

    CountDownLatch ready = new CountDownLatch(threads);
    CountDownLatch startGun = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(threads);
    AtomicInteger created201 = new AtomicInteger();
    AtomicInteger conflict409 = new AtomicInteger();
    Queue<HttpStatus> unexpectedStatuses = new ConcurrentLinkedQueue<>();
    Queue<Throwable> unexpected = new ConcurrentLinkedQueue<>();

    ExecutorService pool = Executors.newFixedThreadPool(threads);
    for (String token : tokens) {
      pool.submit(
          () -> {
            try {
              ready.countDown();
              startGun.await();
              HttpHeaders headers = new HttpHeaders();
              headers.setBearerAuth(token);
              HttpEntity<CreateHoldRequest> entity =
                  new HttpEntity<>(new CreateHoldRequest(List.of(seatId)), headers);
              ResponseEntity<String> response =
                  rest.postForEntity(
                      "http://localhost:" + port + "/api/v1/shows/" + showId + "/holds",
                      entity,
                      String.class);
              HttpStatus status = HttpStatus.valueOf(response.getStatusCode().value());
              if (status == HttpStatus.CREATED) {
                created201.incrementAndGet();
              } else if (status == HttpStatus.CONFLICT) {
                conflict409.incrementAndGet();
              } else {
                unexpectedStatuses.add(status);
              }
            } catch (Throwable t) {
              unexpected.add(t);
            } finally {
              done.countDown();
            }
          });
    }

    assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
    startGun.countDown();
    assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
    pool.shutdown();

    assertThat(unexpected).as("no client-side exceptions").isEmpty();
    assertThat(unexpectedStatuses)
        .as("every loser must be a clean 409, never a 500 or anything else")
        .isEmpty();
    assertThat(created201.get()).isEqualTo(1);
    assertThat(conflict409.get()).isEqualTo(threads - 1);
  }

  private List<String> registerUsers(int n) {
    List<String> tokens = new java.util.ArrayList<>();
    for (int i = 0; i < n; i++) {
      RegisterRequest req =
          new RegisterRequest(
              "http-concurrency-" + UUID.randomUUID() + "@test.local",
              "password123",
              "HTTP Test User " + i,
              null);
      ResponseEntity<AuthResponse> response =
          rest.postForEntity(
              "http://localhost:" + port + "/api/v1/auth/register", req, AuthResponse.class);
      assertThat(response.getStatusCode().value()).isEqualTo(201);
      tokens.add(response.getBody().token());
    }
    return tokens;
  }
}
