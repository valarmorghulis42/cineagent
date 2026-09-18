package com.cineagent.show.service;

import com.cineagent.common.config.HoldProperties;
import com.cineagent.show.domain.SeatHold;
import com.cineagent.show.domain.ShowSeat;
import com.cineagent.show.repository.SeatHoldRepository;
import com.cineagent.show.repository.ShowSeatRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Convergence only — NOT the correctness mechanism. Lazy expiry (see {@link SeatHoldService} /
 * AGENTS.md §4) already guarantees a logically-expired hold can never be honoured, with or
 * without this sweeper running. This class exists purely so stored state converges to the truth:
 * without it, an abandoned hold on a seat nobody ever contends for again would sit as HELD
 * forever, which looks like a leak to anyone reading the data.
 *
 * <p>Runs in bounded batches, each its own transaction — a single UPDATE across every expired row
 * in the system would hold locks long enough to stall live bookings.
 *
 * <p>Can be switched off entirely via {@code app.scheduling.enabled=false} (most integration
 * tests do this and call {@link #sweepOnce()} directly with an advanceable Clock instead) — the
 * system must remain correct with this class doing nothing.
 */
@Component
public class HoldExpirySweeper {

  private static final Logger log = LoggerFactory.getLogger(HoldExpirySweeper.class);

  private final SeatHoldRepository seatHoldRepository;
  private final ShowSeatRepository showSeatRepository;
  private final HoldProperties holdProperties;
  private final Clock clock;

  public HoldExpirySweeper(
      SeatHoldRepository seatHoldRepository,
      ShowSeatRepository showSeatRepository,
      HoldProperties holdProperties,
      Clock clock) {
    this.seatHoldRepository = seatHoldRepository;
    this.showSeatRepository = showSeatRepository;
    this.holdProperties = holdProperties;
    this.clock = clock;
  }

  @Scheduled(fixedDelayString = "${app.hold.sweep-interval}")
  public void sweep() {
    int totalExpired = 0;
    while (true) {
      int expired = sweepOnce();
      totalExpired += expired;
      if (expired < holdProperties.sweepBatchSize()) {
        break;
      }
    }
    if (totalExpired > 0) {
      log.info("Hold expiry sweeper converged {} expired hold(s)", totalExpired);
    }
  }

  /**
   * One bounded batch, its own transaction (REQUIRES_NEW so it never rides along inside a
   * caller's larger transaction in tests). Returns the number of holds expired in this batch.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public int sweepOnce() {
    Instant now = Instant.now(clock);
    List<Long> expiredHoldIds =
        seatHoldRepository.findExpiredActiveIds(now, PageRequest.of(0, holdProperties.sweepBatchSize()));
    if (expiredHoldIds.isEmpty()) {
      return 0;
    }

    // Lock the affected show_seat rows ascending — same global order as every other locker
    // (AGENTS.md §4.4). A batch UPDATE that locked in storage order could deadlock against a
    // booking thread locking ascending.
    //
    // ID-only projection (findIdsByHoldId), NOT an entity-hydrating read — reading these rows as
    // entities and then re-reading the same primary keys through lockAllByIdInOrder in the same
    // persistence context throws ObjectOptimisticLockingFailureException the moment the locking
    // read wakes up to a version a concurrent transaction bumped in between. See
    // ShowSeatRepository.findIdsByShowIdAndSeatIdIn's javadoc — found by
    // ConcurrentSeatHoldH2IT.reversedOrderRequests_noDeadlock.
    List<Long> showSeatIds =
        expiredHoldIds.stream()
            .flatMap(holdId -> showSeatRepository.findIdsByHoldId(holdId).stream())
            .sorted()
            .toList();

    if (!showSeatIds.isEmpty()) {
      List<ShowSeat> locked = showSeatRepository.lockAllByIdInOrder(showSeatIds);
      for (ShowSeat seat : locked) {
        // Re-check under the lock: only reclaim if still logically expired and still pointing
        // at one of the holds we're expiring — a booking thread may have already reclaimed it
        // between our read above and taking this lock.
        if (seat.getHoldId() != null
            && expiredHoldIds.contains(seat.getHoldId())
            && seat.effectiveStatus(now).name().equals("AVAILABLE")) {
          seat.releaseHold();
        }
      }
    }

    List<SeatHold> holds = seatHoldRepository.findAllById(expiredHoldIds);
    holds.forEach(SeatHold::markExpired);
    seatHoldRepository.saveAll(holds);
    return expiredHoldIds.size();
  }
}
