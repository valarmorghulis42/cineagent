package com.cineagent.show.service;

import com.cineagent.catalog.domain.Seat;
import com.cineagent.catalog.repository.SeatRepository;
import com.cineagent.common.error.ErrorCode;
import com.cineagent.common.error.UnprocessableException;
import com.cineagent.show.domain.Show;
import com.cineagent.show.domain.ShowSeat;
import com.cineagent.show.repository.ShowSeatRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the {@code show_seat} row for every seat in the show's screen, in the SAME transaction
 * as show creation — a show is never left with a partial seat map (AGENTS.md domain note: a
 * partial map from a failed/retried provisioning would silently sell fewer seats and be nearly
 * invisible).
 */
@Service
public class ShowSeatProvisioningService {

  private final SeatRepository seatRepository;
  private final ShowSeatRepository showSeatRepository;

  public ShowSeatProvisioningService(SeatRepository seatRepository, ShowSeatRepository showSeatRepository) {
    this.seatRepository = seatRepository;
    this.showSeatRepository = showSeatRepository;
  }

  @Transactional
  public void provision(Show show) {
    List<Seat> seats =
        seatRepository.findByScreenIdAndActiveTrueOrderByRowLabelAscSeatNumberAsc(
            show.getScreen().getId());
    if (seats.isEmpty()) {
      throw new UnprocessableException(
          ErrorCode.SHOW_NOT_BOOKABLE,
          "Screen " + show.getScreen().getId() + " has no seat layout; cannot create a show on it");
    }
    List<ShowSeat> showSeats = seats.stream().map(seat -> new ShowSeat(show, seat)).toList();
    showSeatRepository.saveAll(showSeats);

    long provisioned = showSeatRepository.countByShowId(show.getId());
    if (provisioned != seats.size()) {
      // Defensive: should be unreachable given saveAll ran in this same transaction, but a
      // count mismatch here would be exactly the "silently sold fewer seats" bug — fail loud.
      throw new IllegalStateException(
          "Seat provisioning mismatch for show "
              + show.getId()
              + ": expected "
              + seats.size()
              + ", provisioned "
              + provisioned);
    }
  }
}
