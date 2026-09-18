package com.cineagent.catalog.repository;

import com.cineagent.catalog.domain.Screen;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScreenRepository extends JpaRepository<Screen, Long> {

  /** JOIN FETCH theater — ScreenResponse.from() touches s.getTheater(); see TheaterRepository. */
  @Query("select s from Screen s join fetch s.theater where s.theater.id = :theaterId and s.active = true")
  List<Screen> findByTheaterIdAndActiveTrue(@Param("theaterId") Long theaterId);

  @Query("select s from Screen s join fetch s.theater join fetch s.theater.city where s.id = :id")
  Optional<Screen> findByIdFetchTheater(@Param("id") Long id);

  /**
   * A third, deliberately different concurrency mechanism from seat-level locking: coarse
   * parent-row locking. Show creation is low-concurrency (an admin action), so serializing the
   * whole screen for the duration of the overlap check is an acceptable trade — it turns a
   * portable-DDL-friendly overlap check (Postgres's EXCLUDE USING gist needs btree_gist and
   * doesn't exist on H2) into a lock-then-scan, single-table-by-PK, exactly like the seat lock.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from Screen s where s.id = :id")
  Optional<Screen> lockById(@Param("id") Long id);
}
