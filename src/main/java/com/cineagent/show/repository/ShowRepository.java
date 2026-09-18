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
   */
  @Query(
      "select s from Show s where s.status = 'SCHEDULED' "
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
}
