package com.cineagent.show.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.cineagent.show.domain.Show;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface ShowRepository extends JpaRepository<Show, Long> {

  /**
   * Locks the parent Screen row (by locking the Show query path is NOT how overlap is enforced —
   * see ScreenRepository.lockById). This finder supports the overlap scan itself: all shows on a
   * screen whose [startsAt, endsAt) could overlap a candidate window, run AFTER the screen row is
   * locked in the same transaction (single-table-by-PK locking rule applies to the lock itself;
   * this plain read is fine to join/filter freely since it takes no lock of its own).
   */
  @Query(
      "select s from Show s where s.screen.id = :screenId and s.status = 'SCHEDULED' "
          + "and s.startsAt < :candidateEnd and s.endsAt > :candidateStart")
  List<Show> findOverlapping(
      @Param("screenId") Long screenId,
      @Param("candidateStart") Instant candidateStart,
      @Param("candidateEnd") Instant candidateEnd);

  /**
   * Browse filter — every parameter optional via the (:x is null or ...) pattern. cityId is the
   * hot path (uses idx_show_city_starts); movieId and the date range are additional filters.
   *
   * <p>JOIN FETCH screen/theater/movie/city — ShowResponse.from() dereferences all four after
   * this method's transaction has closed (open-in-view=false). All four are *-to-one, so the
   * fetch join is pagination-safe (no row multiplication the way a *-to-many fetch join would
   * cause).
   */
  @Query(
      "select s from Show s join fetch s.screen sc join fetch sc.theater t "
          + "join fetch s.movie m join fetch s.city c "
          + "where s.status = 'SCHEDULED' "
          + "and (:cityId is null or s.city.id = :cityId) "
          + "and (:movieId is null or s.movie.id = :movieId) "
          + "and (:from is null or s.startsAt >= :from) "
          + "and (:to is null or s.startsAt < :to) "
          + "order by s.startsAt asc, s.id asc")
  Page<Show> browse(
      @Param("cityId") Long cityId,
      @Param("movieId") Long movieId,
      @Param("from") Instant from,
      @Param("to") Instant to,
      Pageable pageable);

  /**
   * JOIN FETCH everything ShowResponse.from() touches — see {@link #browse} javadoc. Used for
   * any single-Show read whose result gets DTO-mapped, including right after {@code create()},
   * where the newly-built Show's own screen/theater/city references can still be uninitialized
   * Hibernate proxies (screen.getTheater().getCity() returns the City *reference* without
   * forcing its initialization) — re-fetching this way is simpler and more robust than relying
   * on session identity-map side effects to have initialized them in place.
   */
  @Query(
      "select s from Show s join fetch s.screen sc join fetch sc.theater t "
          + "join fetch s.movie m join fetch s.city c where s.id = :id")
  Optional<Show> findByIdFetchAll(@Param("id") Long id);

  /** Backs the reminder job — shows starting within the lead-time window, still SCHEDULED. */
  @Query("select s from Show s where s.status = 'SCHEDULED' and s.startsAt >= :from and s.startsAt < :to")
  List<Show> findStartingBetween(@Param("from") Instant from, @Param("to") Instant to);
}
