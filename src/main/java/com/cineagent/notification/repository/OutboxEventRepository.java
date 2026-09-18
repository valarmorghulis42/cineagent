package com.cineagent.notification.repository;

import com.cineagent.notification.domain.OutboxEvent;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

  @Query(
      "select e.id from OutboxEvent e where e.status = 'PENDING' and e.nextAttemptAt <= :now order by e.id")
  List<Long> findDuePendingIds(@Param("now") Instant now, Pageable pageable);

  /** Same ascending-id lock order as everything else (AGENTS.md §4.4) — a batch that locked in
   * storage order could deadlock against another dispatcher instance. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from OutboxEvent e where e.id in :ids order by e.id")
  List<OutboxEvent> lockAllByIdInOrder(@Param("ids") List<Long> ids);

  Optional<OutboxEvent> findByDedupeKey(String dedupeKey);

  /** Backs GET /admin/notifications?bookingRef= — the observable proof that an async
   * confirmation was actually produced, not just claimed. */
  @Query("select e from OutboxEvent e where e.payload like concat('%', :bookingRef, '%') order by e.id desc")
  List<OutboxEvent> findByPayloadContaining(@Param("bookingRef") String bookingRef);

  List<OutboxEvent> findTop50ByOrderByIdDesc();
}
