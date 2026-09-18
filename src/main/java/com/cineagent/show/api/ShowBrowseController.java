package com.cineagent.show.api;

import com.cineagent.common.web.PageResponse;
import com.cineagent.show.api.dto.SeatMapDtos.SeatMapEntry;
import com.cineagent.show.api.dto.ShowDtos.ShowResponse;
import com.cineagent.show.service.SeatMapQueryService;
import com.cineagent.show.service.ShowService;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public, unauthenticated browse endpoints — see SecurityConfig's permitAll matchers. */
@RestController
@RequestMapping("/api/v1")
public class ShowBrowseController {

  private static final int MAX_PAGE_SIZE = 100;

  private final ShowService showService;
  private final SeatMapQueryService seatMapQueryService;

  public ShowBrowseController(ShowService showService, SeatMapQueryService seatMapQueryService) {
    this.showService = showService;
    this.seatMapQueryService = seatMapQueryService;
  }

  @GetMapping("/shows")
  public PageResponse<ShowResponse> browse(
      @RequestParam(required = false) Long cityId,
      @RequestParam(required = false) Long movieId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
      @PageableDefault(size = 20) Pageable pageable) {
    var page = showService.browse(cityId, movieId, from, to, capped(pageable));
    return PageResponse.from(page, page.getContent().stream().map(ShowResponse::from).toList());
  }

  @GetMapping("/shows/{id}")
  public ShowResponse getShow(@PathVariable Long id) {
    return ShowResponse.from(showService.getShowWithDetails(id));
  }

  @GetMapping("/shows/{id}/seats")
  public List<SeatMapEntry> getSeatMap(@PathVariable Long id) {
    return seatMapQueryService.getSeatMap(id);
  }

  private Pageable capped(Pageable pageable) {
    if (pageable.getPageSize() <= MAX_PAGE_SIZE) {
      return pageable;
    }
    return PageRequest.of(pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort());
  }
}
