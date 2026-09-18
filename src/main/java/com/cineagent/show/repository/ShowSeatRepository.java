package com.cineagent.show.repository;

import com.cineagent.catalog.domain.SeatCategory;
import com.cineagent.show.domain.ShowSeat;
import com.cineagent.show.domain.ShowSeatStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface ShowSeatRepository extends JpaRepository<ShowSeat, Long> {

  /**
   * THE serialization point for the entire system. Locks the given show_seat rows for update.
   *
   * <p>Callers MUST pass {@code ids} sorted ascending — see AGENTS.md §4.4 (global lock order).
   * Sorting is done in Java by the caller, in addition to the SQL ORDER BY here, so the ordering
   * guarantee does not depend on any query planner's behaviour on either H2 or Postgres.
   *
   * <p>Deliberately single-table, by primary key only, with NO status predicate — see AGENTS.md
   * §4.3. A status predicate in a FOR UPDATE query can silently return fewer rows than requested
   * once a blocked lock is granted, because READ_COMMITTED re-evaluates the WHERE clause on
   * wakeup (verified in {@code ForUpdateStatusPredicateTrapTest}). Status is checked in Java,
   * after the lock is held, in the calling service.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
  @Query("select ss from ShowSeat ss where ss.id in :ids order by ss.id")
  List<ShowSeat> lockAllByIdInOrder(@Param("ids") List<Long> ids);

  List<ShowSeat> findByShowIdAndSeatIdIn(Long showId, List<Long> seatIds);

  boolean existsByShowId(Long showId);

  long countByShowId(Long showId);

  List<ShowSeat> findByHoldId(Long holdId);

  /**
   * ID-only projections, deliberately NOT entity-hydrating — found necessary the hard way by
   * {@code ConcurrentSeatHoldH2IT.reversedOrderRequests_noDeadlock}: resolving candidate rows via
   * an entity-returning read (e.g. {@link #findByShowIdAndSeatIdIn}) and then re-reading those
   * SAME rows through {@link #lockAllByIdInOrder} in the same persistence context makes Hibernate
   * throw {@code ObjectOptimisticLockingFailureException} the moment the locking query wakes up
   * to a row whose version another transaction bumped in between — "conflicting version of
   * entity already held in persistence context." That is not a real seat conflict; it is an
   * artifact of reading the same primary keys twice. Callers resolving ids-to-lock MUST go
   * through one of these instead, so nothing is cached before the one, single, locking read.
   */
  @Query("select ss.id from ShowSeat ss where ss.show.id = :showId and ss.seat.id in :seatIds")
  List<Long> findIdsByShowIdAndSeatIdIn(@Param("showId") Long showId, @Param("seatIds") List<Long> seatIds);

  @Query("select ss.id from ShowSeat ss where ss.holdId = :holdId order by ss.id")
  List<Long> findIdsByHoldId(@Param("holdId") Long holdId);

  /**
   * The seat-map projection — ONE query, no entity hydration, no lazy proxies. This is what
   * keeps the hottest read in the system (a show's seat map, ~100-300 rows, hit on every page
   * view) from becoming an N+1. See SeatMapQueryCountTest, which asserts this executes exactly
   * once regardless of seat count.
   *
   * <p>LEFT JOINs show_price so a category with no price row comes back with a null baseAmount —
   * SeatMapQueryService turns that into a loud 422, never a silent zero price.
   */
  @Query(
      "select ss.id as showSeatId, s.rowLabel as rowLabel, s.seatNumber as seatNumber, "
          + "s.displayLabel as displayLabel, s.category as category, ss.status as status, "
          + "ss.holdExpiresAt as holdExpiresAt, sp.baseAmount as baseAmount "
          + "from ShowSeat ss join ss.seat s "
          + "left join ShowPrice sp on sp.show = ss.show and sp.seatCategory = s.category "
          + "where ss.show.id = :showId order by s.rowLabel, s.seatNumber")
  List<SeatMapRow> findSeatMapByShowId(@Param("showId") Long showId);

  interface SeatMapRow {
    Long getShowSeatId();

    String getRowLabel();

    Integer getSeatNumber();

    String getDisplayLabel();

    SeatCategory getCategory();

    ShowSeatStatus getStatus();

    Instant getHoldExpiresAt();

    BigDecimal getBaseAmount();
  }
}
