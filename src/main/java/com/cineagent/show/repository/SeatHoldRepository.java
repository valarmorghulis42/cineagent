package com.cineagent.show.repository;

import com.cineagent.show.domain.SeatHold;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SeatHoldRepository extends JpaRepository<SeatHold, Long> {

  long countByUserIdAndStatus(Long userId, com.cineagent.show.domain.HoldStatus status);

  /**
   * Batch finder for the sweeper (AGENTS.md §4.4: bounded batches, separate transactions). Only
   * finds ids here — the sweeper locks the corresponding show_seat rows ascending, in Java, in
   * its own service method, per the global lock order.
   */
  @Query(
      "select h.id from SeatHold h where h.status = 'ACTIVE' and h.expiresAt <= :now order by h.id")
  List<Long> findExpiredActiveIds(@Param("now") Instant now, Pageable pageable);
}
