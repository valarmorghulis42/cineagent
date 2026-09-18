package com.cineagent.show.service;

import com.cineagent.common.config.HoldProperties;
import com.cineagent.common.error.ConflictException;
import com.cineagent.common.error.ErrorCode;
import com.cineagent.common.error.ResourceNotFoundException;
import com.cineagent.common.error.UnprocessableException;
import com.cineagent.show.domain.HoldStatus;
import com.cineagent.show.domain.SeatHold;
import com.cineagent.show.domain.Show;
import com.cineagent.show.domain.ShowSeat;
import com.cineagent.show.domain.ShowSeatStatus;
import com.cineagent.show.repository.SeatHoldRepository;
import com.cineagent.show.repository.ShowSeatRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * THE core of the concurrency guarantee. Every rule here is explained in detail in AGENTS.md §4
 * and the {@code concurrency-testing} skill — this class is where those rules are actually
 * implemented, so read both before changing anything here.
 *
 * <p>Summary: lock the requested {@code show_seat} rows in ascending-id order (never request
 * order), re-derive each row's effective status from the injected {@link Clock} rather than
 * trusting stored state (lazy expiry is the correctness mechanism — AGENTS.md), and fail the
 * whole request if any seat is unavailable (all-or-nothing, never a partial allocation).
 */
@Service
public class SeatHoldService {

  private final ShowSeatRepository showSeatRepository;
  private final SeatHoldRepository seatHoldRepository;
  private final ShowService showService;
  private final HoldProperties holdProperties;
  private final Clock clock;

  public SeatHoldService(
      ShowSeatRepository showSeatRepository,
      SeatHoldRepository seatHoldRepository,
      ShowService showService,
      HoldProperties holdProperties,
      Clock clock) {
    this.showSeatRepository = showSeatRepository;
    this.seatHoldRepository = seatHoldRepository;
    this.showService = showService;
    this.holdProperties = holdProperties;
    this.clock = clock;
  }

  /**
   * Acquires a hold on the given seats for the given show, on behalf of userId. All-or-nothing:
   * if ANY requested seat is unavailable, no hold is created for any of them, and the response
   * lists exactly which seats were the problem so the client can re-render without a second
   * round trip.
   */
  @Transactional
  public SeatHold acquire(Long showId, List<Long> seatIds, Long userId) {
    if (seatIds.isEmpty()) {
      throw new UnprocessableException(ErrorCode.HOLD_LIMIT_EXCEEDED, "At least one seat must be requested");
    }
    if (seatIds.size() > holdProperties.maxSeatsPerHold()) {
      throw new UnprocessableException(
          ErrorCode.HOLD_LIMIT_EXCEEDED,
          "Cannot hold more than " + holdProperties.maxSeatsPerHold() + " seats at once");
    }

    Show show = showService.getShow(showId);
    Instant now = Instant.now(clock);
    if (!show.isBookableAt(now)) {
      throw new UnprocessableException(ErrorCode.SHOW_NOT_BOOKABLE, "Show " + showId + " is not open for booking");
    }

    long activeHolds = seatHoldRepository.countByUserIdAndStatus(userId, HoldStatus.ACTIVE);
    if (activeHolds >= holdProperties.maxActiveHoldsPerUser()) {
      throw new UnprocessableException(
          ErrorCode.HOLD_LIMIT_EXCEEDED,
          "You already have " + activeHolds + " active holds (limit "
              + holdProperties.maxActiveHoldsPerUser() + ")");
    }

    // Resolve the requested seatIds to their show_seat row ids for THIS show via an ID-ONLY
    // projection — NOT an entity-hydrating read. Reading these rows as entities here and then
    // re-reading the same primary keys through lockAllByIdInOrder in the same persistence
    // context makes Hibernate throw ObjectOptimisticLockingFailureException the moment the
    // locking query wakes up to a version another transaction bumped in between (found by
    // ConcurrentSeatHoldH2IT.reversedOrderRequests_noDeadlock — see
    // ShowSeatRepository.findIdsByShowIdAndSeatIdIn's javadoc). Then sort ascending in Java
    // before locking — AGENTS.md §4.4, the global lock order — independent of either engine's
    // query planner.
    List<Long> lockIds = showSeatRepository.findIdsByShowIdAndSeatIdIn(showId, seatIds).stream().sorted().toList();
    if (lockIds.size() != seatIds.size()) {
      throw new ResourceNotFoundException(
          ErrorCode.RESOURCE_NOT_FOUND, "One or more requested seats do not belong to show " + showId);
    }

    // THE serialization point. Single-table, by primary key, no status predicate — see
    // ShowSeatRepository.lockAllByIdInOrder's javadoc for why.
    List<ShowSeat> locked = showSeatRepository.lockAllByIdInOrder(lockIds);

    List<ShowSeat> unavailable = new ArrayList<>();
    Set<Long> reclaimableHoldIds = new LinkedHashSet<>();
    for (ShowSeat seat : locked) {
      ShowSeatStatus effective = seat.effectiveStatus(now);
      if (effective != ShowSeatStatus.AVAILABLE) {
        unavailable.add(seat);
      } else if (seat.getStatus() == ShowSeatStatus.HELD) {
        // Logically expired but not yet swept — reclaiming it now, under the lock.
        reclaimableHoldIds.add(seat.getHoldId());
      }
    }

    if (!unavailable.isEmpty()) {
      List<Map<String, Object>> conflicts =
          unavailable.stream()
              .map(
                  s -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("showSeatId", s.getId());
                    row.put("seatLabel", s.getSeat().getDisplayLabel());
                    row.put("status", s.effectiveStatus(now).name());
                    return row;
                  })
              .toList();
      ConflictException ex =
          new ConflictException(
              ErrorCode.SEAT_UNAVAILABLE,
              unavailable.size() + " of " + seatIds.size() + " requested seats are unavailable");
      ex.withProperty("showId", showId);
      ex.withProperty("conflictingSeats", conflicts);
      throw ex;
    }

    // Expire the stale holds we're reclaiming from, under the same lock.
    if (!reclaimableHoldIds.isEmpty()) {
      List<SeatHold> stale = seatHoldRepository.findAllById(reclaimableHoldIds);
      stale.forEach(SeatHold::markExpired);
      seatHoldRepository.saveAll(stale);
    }

    Instant expiresAt = now.plus(holdProperties.ttlMinutes(), ChronoUnit.MINUTES);
    SeatHold hold = seatHoldRepository.save(new SeatHold(show, userId, locked.size(), expiresAt));
    locked.forEach(seat -> seat.assignHold(hold.getId(), expiresAt));
    return hold;
  }

  @Transactional
  public void release(Long holdId, Long userId) {
    SeatHold hold = getOwnedActiveHold(holdId, userId);
    List<ShowSeat> seats = lockSeatsForHold(holdId);
    seats.forEach(ShowSeat::releaseHold);
    hold.markReleased();
  }

  @Transactional
  public SeatHold extend(Long holdId, Long userId) {
    SeatHold hold = getOwnedActiveHold(holdId, userId);
    Instant now = Instant.now(clock);
    if (hold.isLogicallyExpired(now)) {
      throw new ConflictException(ErrorCode.HOLD_EXPIRED, "Hold " + holdId + " has already expired");
    }
    Instant newExpiry = now.plus(holdProperties.ttlMinutes(), ChronoUnit.MINUTES);
    List<ShowSeat> seats = lockSeatsForHold(holdId);
    seats.forEach(seat -> seat.assignHold(holdId, newExpiry));
    hold.extend(newExpiry);
    return hold;
  }

  /**
   * Locks and returns a hold's seats for booking checkout — the hold is validated (owned,
   * ACTIVE, not logically expired) but NOT mutated; seats stay HELD. Used by
   * {@code BookingService.create} to snapshot seat/category data for pricing without consuming
   * the hold — payment happens later, over the network, strictly between transactions.
   */
  @Transactional
  public HeldSeatsSnapshot lockOwnedActiveHold(Long holdId, Long userId) {
    SeatHold hold = getOwnedActiveHold(holdId, userId);
    if (hold.isLogicallyExpired(Instant.now(clock))) {
      throw new ConflictException(ErrorCode.HOLD_EXPIRED, "Hold " + holdId + " has already expired");
    }
    List<ShowSeat> seats = lockSeatsForHold(holdId);
    return new HeldSeatsSnapshot(hold, seats);
  }

  /**
   * Re-locks a hold's seats and checks they are STILL genuinely held by it — called after a
   * payment gateway round-trip to discover whether the hold expired (and was reclaimed by
   * someone else) while the network call was in flight. No exception on failure: the caller
   * branches on the boolean to run the seat-lost-after-payment compensation (AGENTS.md §4.6),
   * which itself must call the gateway OUTSIDE this method's transaction.
   */
  @Transactional
  public boolean seatsStillHeldByHold(Long holdId) {
    List<ShowSeat> seats = lockSeatsForHold(holdId);
    if (seats.isEmpty()) {
      return false;
    }
    Instant now = Instant.now(clock);
    SeatHold hold = seatHoldRepository.findById(holdId).orElse(null);
    if (hold == null || hold.getStatus() != HoldStatus.ACTIVE || hold.isLogicallyExpired(now)) {
      return false;
    }
    return seats.stream().allMatch(s -> holdId.equals(s.getHoldId()) && s.effectiveStatus(now) == ShowSeatStatus.HELD);
  }

  /** Commits a successful booking: seats -> BOOKED, hold -> CONSUMED. Caller must have just
   * confirmed {@link #seatsStillHeldByHold} true in the SAME transaction. */
  @Transactional
  public void confirmHoldAsBooked(Long holdId, Long bookingId) {
    List<ShowSeat> seats = lockSeatsForHold(holdId);
    seats.forEach(seat -> seat.confirmBooking(bookingId));
    seatHoldRepository.findById(holdId).ifPresent(SeatHold::markConsumed);
  }

  /** Releases specific BOOKED show_seat rows back to AVAILABLE (booking cancellation, full or
   * partial). Locked ascending-id, same global order as everything else. */
  @Transactional
  public void releaseBookedSeats(List<Long> showSeatIds) {
    List<ShowSeat> locked = showSeatRepository.lockAllByIdInOrder(showSeatIds.stream().sorted().toList());
    locked.forEach(ShowSeat::cancelBooking);
  }

  /** Admin seat-blocking (a broken seat, a press hold) — only ever a single row, but still
   * routed through the same locking query as everything else for consistency. */
  @Transactional
  public void blockSeat(Long showSeatId) {
    ShowSeat seat = lockSingle(showSeatId);
    if (seat.getStatus() != ShowSeatStatus.AVAILABLE) {
      throw new ConflictException(ErrorCode.SEAT_UNAVAILABLE, "Seat " + showSeatId + " is not AVAILABLE (currently " + seat.getStatus() + ")");
    }
    seat.block();
  }

  @Transactional
  public void unblockSeat(Long showSeatId) {
    ShowSeat seat = lockSingle(showSeatId);
    if (seat.getStatus() != ShowSeatStatus.BLOCKED) {
      throw new ConflictException(ErrorCode.SEAT_UNAVAILABLE, "Seat " + showSeatId + " is not BLOCKED (currently " + seat.getStatus() + ")");
    }
    seat.unblock();
  }

  private ShowSeat lockSingle(Long showSeatId) {
    List<ShowSeat> locked = showSeatRepository.lockAllByIdInOrder(List.of(showSeatId));
    if (locked.isEmpty()) {
      throw ResourceNotFoundException.of(ErrorCode.RESOURCE_NOT_FOUND, showSeatId);
    }
    return locked.get(0);
  }

  public record HeldSeatsSnapshot(SeatHold hold, List<ShowSeat> seats) {}

  private SeatHold getOwnedActiveHold(Long holdId, Long userId) {
    SeatHold hold =
        seatHoldRepository
            .findById(holdId)
            .orElseThrow(() -> ResourceNotFoundException.of(ErrorCode.HOLD_NOT_FOUND, holdId));
    if (!hold.getUserId().equals(userId)) {
      // 404, not 403 — do not leak that a hold with this id exists to a non-owner. See the
      // api-contract skill's status taxonomy.
      throw ResourceNotFoundException.of(ErrorCode.HOLD_NOT_FOUND, holdId);
    }
    if (hold.getStatus() != HoldStatus.ACTIVE) {
      throw new ConflictException(ErrorCode.HOLD_EXPIRED, "Hold " + holdId + " is not active");
    }
    return hold;
  }

  private List<ShowSeat> lockSeatsForHold(Long holdId) {
    // ID-only projection, same reason as in acquire() — never hydrate these rows before the one
    // locking read.
    return showSeatRepository.lockAllByIdInOrder(showSeatRepository.findIdsByHoldId(holdId));
  }
}
