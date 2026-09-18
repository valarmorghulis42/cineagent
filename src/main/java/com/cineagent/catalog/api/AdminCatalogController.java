package com.cineagent.catalog.api;

import com.cineagent.catalog.api.dto.CityDtos.CityRequest;
import com.cineagent.catalog.api.dto.CityDtos.CityResponse;
import com.cineagent.catalog.api.dto.MovieDtos.MovieRequest;
import com.cineagent.catalog.api.dto.MovieDtos.MovieResponse;
import com.cineagent.catalog.api.dto.ScreenDtos.ScreenRequest;
import com.cineagent.catalog.api.dto.ScreenDtos.ScreenResponse;
import com.cineagent.catalog.api.dto.SeatLayoutDtos.BulkSeatLayoutRequest;
import com.cineagent.catalog.api.dto.SeatLayoutDtos.SeatResponse;
import com.cineagent.catalog.api.dto.TheaterDtos.TheaterRequest;
import com.cineagent.catalog.api.dto.TheaterDtos.TheaterResponse;
import com.cineagent.catalog.service.CatalogService;
import com.cineagent.catalog.service.SeatLayoutService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only management of reference data. URL-level {@code hasRole("ADMIN")} gating is applied
 * globally to {@code /api/v1/admin/**} in {@code SecurityConfig} — see AGENTS.md §5/§7.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminCatalogController {

  private final CatalogService catalogService;
  private final SeatLayoutService seatLayoutService;

  public AdminCatalogController(CatalogService catalogService, SeatLayoutService seatLayoutService) {
    this.catalogService = catalogService;
    this.seatLayoutService = seatLayoutService;
  }

  @PostMapping("/cities")
  public ResponseEntity<CityResponse> createCity(@Valid @RequestBody CityRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(CityResponse.from(catalogService.createCity(req)));
  }

  @PutMapping("/cities/{id}")
  public CityResponse updateCity(@PathVariable Long id, @Valid @RequestBody CityRequest req) {
    return CityResponse.from(catalogService.updateCity(id, req));
  }

  @PostMapping("/theaters")
  public ResponseEntity<TheaterResponse> createTheater(@Valid @RequestBody TheaterRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(TheaterResponse.from(catalogService.createTheater(req)));
  }

  @PutMapping("/theaters/{id}")
  public TheaterResponse updateTheater(@PathVariable Long id, @Valid @RequestBody TheaterRequest req) {
    return TheaterResponse.from(catalogService.updateTheater(id, req));
  }

  @PostMapping("/screens")
  public ResponseEntity<ScreenResponse> createScreen(@Valid @RequestBody ScreenRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ScreenResponse.from(catalogService.createScreen(req)));
  }

  @PostMapping("/screens/{screenId}/seats/bulk")
  public ResponseEntity<List<SeatResponse>> createSeatLayout(
      @PathVariable Long screenId, @Valid @RequestBody BulkSeatLayoutRequest req) {
    List<SeatResponse> response =
        seatLayoutService.createLayout(screenId, req).stream().map(SeatResponse::from).toList();
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @PostMapping("/movies")
  public ResponseEntity<MovieResponse> createMovie(@Valid @RequestBody MovieRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(MovieResponse.from(catalogService.createMovie(req)));
  }

  @PutMapping("/movies/{id}")
  public MovieResponse updateMovie(@PathVariable Long id, @Valid @RequestBody MovieRequest req) {
    return MovieResponse.from(catalogService.updateMovie(id, req));
  }
}
