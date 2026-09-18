package com.cineagent.catalog.service;

import com.cineagent.catalog.api.dto.SeatLayoutDtos.BulkSeatLayoutRequest;
import com.cineagent.catalog.api.dto.SeatLayoutDtos.RowSpec;
import com.cineagent.catalog.domain.Screen;
import com.cineagent.catalog.domain.Seat;
import com.cineagent.catalog.repository.ScreenRepository;
import com.cineagent.catalog.repository.SeatRepository;
import com.cineagent.common.error.ConflictException;
import com.cineagent.common.error.ErrorCode;
import com.cineagent.common.error.ResourceNotFoundException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bulk seat-layout creation for a screen — a screen's physical layout is rows x seat-counts with
 * a category per row, created in one call rather than one POST per seat. See AGENTS.md /
 * architecture plan enhancement B3.
 */
@Service
@Transactional
public class SeatLayoutService {

  private final ScreenRepository screenRepository;
  private final SeatRepository seatRepository;

  public SeatLayoutService(ScreenRepository screenRepository, SeatRepository seatRepository) {
    this.screenRepository = screenRepository;
    this.seatRepository = seatRepository;
  }

  public List<Seat> createLayout(Long screenId, BulkSeatLayoutRequest request) {
    Screen screen =
        screenRepository
            .findById(screenId)
            .orElseThrow(() -> ResourceNotFoundException.of(ErrorCode.RESOURCE_NOT_FOUND, screenId));

    if (seatRepository.existsByScreenId(screenId)) {
      // A screen's layout is set once at creation time. Re-laying-out a screen that already has
      // shows/bookings against its seats would orphan show_seat rows — out of scope for this
      // assignment; an admin who needs to change a layout creates a new screen.
      throw new ConflictException(
          ErrorCode.SEAT_LAYOUT_ALREADY_EXISTS, "Screen " + screenId + " already has a seat layout");
    }

    List<Seat> seats = new ArrayList<>();
    for (RowSpec row : request.rows()) {
      for (int seatNumber = 1; seatNumber <= row.seatCount(); seatNumber++) {
        seats.add(new Seat(screen, row.rowLabel(), seatNumber, row.category()));
      }
    }
    seats = seatRepository.saveAll(seats);
    screen.setTotalSeats(seats.size());
    return seats;
  }

  @Transactional(readOnly = true)
  public List<Seat> getLayout(Long screenId) {
    return seatRepository.findByScreenIdAndActiveTrueOrderByRowLabelAscSeatNumberAsc(screenId);
  }
}
