package com.cineagent.show.service;

import com.cineagent.catalog.domain.Movie;
import com.cineagent.catalog.domain.Screen;
import com.cineagent.catalog.repository.MovieRepository;
import com.cineagent.catalog.repository.ScreenRepository;
import com.cineagent.common.error.ConflictException;
import com.cineagent.common.error.ErrorCode;
import com.cineagent.common.error.ResourceNotFoundException;
import com.cineagent.show.api.dto.ShowDtos.CreateShowRequest;
import com.cineagent.show.api.dto.ShowDtos.PriceSpec;
import com.cineagent.show.domain.Show;
import com.cineagent.show.domain.ShowPrice;
import com.cineagent.show.repository.ShowPriceRepository;
import com.cineagent.show.repository.ShowRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShowService {

  private final ShowRepository showRepository;
  private final ShowPriceRepository showPriceRepository;
  private final ScreenRepository screenRepository;
  private final MovieRepository movieRepository;
  private final ShowSeatProvisioningService provisioningService;

  public ShowService(
      ShowRepository showRepository,
      ShowPriceRepository showPriceRepository,
      ScreenRepository screenRepository,
      MovieRepository movieRepository,
      ShowSeatProvisioningService provisioningService) {
    this.showRepository = showRepository;
    this.showPriceRepository = showPriceRepository;
    this.screenRepository = screenRepository;
    this.movieRepository = movieRepository;
    this.provisioningService = provisioningService;
  }

  /**
   * Locks the Screen row FIRST (third concurrency mechanism, see ScreenRepository.lockById),
   * then scans for an overlap, then creates the show, its prices, and provisions every seat —
   * all in this one transaction, so a show is never left partially set up.
   */
  @Transactional
  public Show create(CreateShowRequest req) {
    Screen screen =
        screenRepository
            .lockById(req.screenId())
            .orElseThrow(() -> ResourceNotFoundException.of(ErrorCode.RESOURCE_NOT_FOUND, req.screenId()));
    Movie movie =
        movieRepository
            .findById(req.movieId())
            .orElseThrow(() -> ResourceNotFoundException.of(ErrorCode.RESOURCE_NOT_FOUND, req.movieId()));

    List<Show> overlapping =
        showRepository.findOverlapping(screen.getId(), req.startsAt(), req.endsAt());
    if (!overlapping.isEmpty()) {
      throw new ConflictException(
          ErrorCode.SCREEN_OVERLAP,
          "Screen " + screen.getId() + " already has an overlapping show at this time");
    }

    // city is ALWAYS derived server-side from the screen's own theater/city — never accepted
    // from the request DTO. See AGENTS.md domain note on show.city_id consistency.
    Show newShow =
        new Show(
            screen,
            movie,
            screen.getTheater().getCity(),
            req.startsAt(),
            req.endsAt(),
            req.salesOpenAt(),
            req.salesCloseAt());
    Show show = showRepository.save(newShow);

    List<ShowPrice> prices =
        req.prices().stream()
            .map(p -> new ShowPrice(show, p.category(), p.baseAmount()))
            .toList();
    showPriceRepository.saveAll(prices);

    provisioningService.provision(show);
    return show;
  }

  @Transactional
  public void cancel(Long showId) {
    getShow(showId).cancel();
    // Releasing live holds / refunding bookings on cancellation is booking-module work — wired
    // once that module exists (architecture plan enhancement B5).
  }

  @Transactional(readOnly = true)
  public Show getShow(Long id) {
    return showRepository
        .findById(id)
        .orElseThrow(() -> ResourceNotFoundException.of(ErrorCode.SHOW_NOT_FOUND, id));
  }

  @Transactional(readOnly = true)
  public Page<Show> browse(Long cityId, Long movieId, Instant from, Instant to, Pageable pageable) {
    return showRepository.browse(cityId, movieId, from, to, pageable);
  }
}
