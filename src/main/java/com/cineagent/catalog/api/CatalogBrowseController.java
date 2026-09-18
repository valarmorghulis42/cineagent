package com.cineagent.catalog.api;

import com.cineagent.catalog.api.dto.CityDtos.CityResponse;
import com.cineagent.catalog.api.dto.MovieDtos.MovieResponse;
import com.cineagent.catalog.api.dto.SeatLayoutDtos.SeatResponse;
import com.cineagent.catalog.api.dto.TheaterDtos.TheaterResponse;
import com.cineagent.catalog.service.CatalogService;
import com.cineagent.catalog.service.SeatLayoutService;
import com.cineagent.common.web.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public, unauthenticated browse endpoints — permitted in {@code SecurityConfig}. */
@RestController
@RequestMapping("/api/v1")
public class CatalogBrowseController {

  private static final int MAX_PAGE_SIZE = 100;

  private final CatalogService catalogService;
  private final SeatLayoutService seatLayoutService;

  public CatalogBrowseController(CatalogService catalogService, SeatLayoutService seatLayoutService) {
    this.catalogService = catalogService;
    this.seatLayoutService = seatLayoutService;
  }

  @GetMapping("/cities")
  public PageResponse<CityResponse> listCities(
      @PageableDefault(size = 20, sort = "id") Pageable pageable) {
    var page = catalogService.listActiveCities(capped(pageable));
    return PageResponse.from(page, page.getContent().stream().map(CityResponse::from).toList());
  }

  @GetMapping("/cities/{cityId}/theaters")
  public PageResponse<TheaterResponse> listTheaters(
      @PathVariable Long cityId, @PageableDefault(size = 20, sort = "id") Pageable pageable) {
    var page = catalogService.listTheatersByCity(cityId, capped(pageable));
    return PageResponse.from(page, page.getContent().stream().map(TheaterResponse::from).toList());
  }

  @GetMapping("/movies")
  public PageResponse<MovieResponse> listMovies(@PageableDefault(size = 20, sort = "id") Pageable pageable) {
    var page = catalogService.listActiveMovies(capped(pageable));
    return PageResponse.from(page, page.getContent().stream().map(MovieResponse::from).toList());
  }

  @GetMapping("/screens/{screenId}/seats")
  public java.util.List<SeatResponse> getSeatLayout(@PathVariable Long screenId) {
    return seatLayoutService.getLayout(screenId).stream().map(SeatResponse::from).toList();
  }

  /** Every paginated endpoint caps page size server-side — see the api-contract skill. */
  private Pageable capped(Pageable pageable) {
    if (pageable.getPageSize() <= MAX_PAGE_SIZE) {
      return pageable;
    }
    return PageRequest.of(pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort());
  }
}
