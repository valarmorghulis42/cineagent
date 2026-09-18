package com.cineagent.show.domain;

import com.cineagent.catalog.domain.City;
import com.cineagent.catalog.domain.Movie;
import com.cineagent.catalog.domain.Screen;
import com.cineagent.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Deliberately carries NO {@code @Version}. Concurrency correctness for this system lives at the
 * per-seat granularity ({@code ShowSeat}), not per-show — a version column here would create a
 * single serialization point that every write to any of a show's 250 seats contends on, directly
 * undermining the point of per-seat locking. The one place a {@code Show}-level write race
 * matters (creating a show vs. checking for a screen-time overlap) is handled instead by locking
 * the parent {@code Screen} row — see {@code ShowService.create}.
 */
@Entity
@Table(name = "show_event")
public class Show extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "screen_id", nullable = false)
  private Screen screen;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "movie_id", nullable = false)
  private Movie movie;

  /**
   * Denormalised from screen -> theater -> city, purely for the hot browse query ("shows in city
   * X for movie Y on date D"). Always derived server-side from {@code screen.getTheater().getCity()}
   * in {@code ShowService} — never accepted from a request DTO, so it cannot disagree with the
   * screen's real city.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "city_id", nullable = false)
  private City city;

  @Column(name = "starts_at", nullable = false)
  private Instant startsAt;

  @Column(name = "ends_at", nullable = false)
  private Instant endsAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ShowStatus status = ShowStatus.SCHEDULED;

  @Column(name = "sales_open_at")
  private Instant salesOpenAt;

  /** Sales close N minutes before start; null means sales stay open until {@code startsAt}. */
  @Column(name = "sales_close_at")
  private Instant salesCloseAt;

  @Column(nullable = false, length = 3)
  private String currency = "INR";

  protected Show() {}

  public Show(
      Screen screen,
      Movie movie,
      City city,
      Instant startsAt,
      Instant endsAt,
      Instant salesOpenAt,
      Instant salesCloseAt) {
    this.screen = screen;
    this.movie = movie;
    this.city = city;
    this.startsAt = startsAt;
    this.endsAt = endsAt;
    this.salesOpenAt = salesOpenAt;
    this.salesCloseAt = salesCloseAt;
  }

  public Screen getScreen() {
    return screen;
  }

  public Movie getMovie() {
    return movie;
  }

  public City getCity() {
    return city;
  }

  public Instant getStartsAt() {
    return startsAt;
  }

  public Instant getEndsAt() {
    return endsAt;
  }

  public ShowStatus getStatus() {
    return status;
  }

  public Instant getSalesOpenAt() {
    return salesOpenAt;
  }

  public Instant getSalesCloseAt() {
    return salesCloseAt;
  }

  public String getCurrency() {
    return currency;
  }

  public void cancel() {
    this.status = ShowStatus.CANCELLED;
  }

  /**
   * True if the show is open for new holds/bookings at the given instant. {@code COMPLETED} is
   * never set by a mutator (see {@link ShowStatus}) — completion is time-derived, so this check
   * is time-based (startsAt) rather than status-based for the "already started" case, and
   * status-based only for the explicit admin action (CANCELLED).
   */
  public boolean isBookableAt(Instant now) {
    return status == ShowStatus.SCHEDULED
        && startsAt.isAfter(now)
        && (salesOpenAt == null || !salesOpenAt.isAfter(now))
        && (salesCloseAt == null || salesCloseAt.isAfter(now));
  }

  /** True once the show's end time has passed, regardless of the stored {@code status}. */
  public boolean hasEnded(Instant now) {
    return !endsAt.isAfter(now);
  }
}
