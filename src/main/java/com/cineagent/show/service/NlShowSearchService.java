package com.cineagent.show.service;

import com.cineagent.catalog.domain.City;
import com.cineagent.catalog.service.CatalogService;
import com.cineagent.show.domain.Show;
import com.cineagent.show.port.NlQueryInterpreter;
import com.cineagent.show.repository.ShowRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Natural-language show search. Understanding the query text is delegated to {@link
 * NlQueryInterpreter} — either the always-available deterministic keyword parser, or, when
 * {@code app.ai.gemini.enabled=true}, a real Gemini call that falls back to the deterministic
 * parser on any failure. This service owns everything domain-specific: resolving a city NAME to
 * a city ID, turning a date-range LABEL into actual Instant boundaries, and running the query —
 * neither interpreter implementation touches the database. The interpretation is always returned
 * alongside the results, so what the system understood from the query is never a black box.
 */
@Service
public class NlShowSearchService {

  private final CatalogService catalogService;
  private final ShowRepository showRepository;
  private final NlQueryInterpreter queryInterpreter;
  private final Clock clock;

  public NlShowSearchService(
      CatalogService catalogService, ShowRepository showRepository, NlQueryInterpreter queryInterpreter, Clock clock) {
    this.catalogService = catalogService;
    this.showRepository = showRepository;
    this.queryInterpreter = queryInterpreter;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public NlSearchResult search(String query, Pageable pageable) {
    // Demo/assignment scale only (a handful of cities) -- a full-text or trigram index would
    // replace this linear list at real scale. See README's Known Gaps.
    List<City> cities = catalogService.listActiveCities(PageRequest.of(0, 200)).getContent();
    List<String> cityNames = cities.stream().map(City::getName).toList();

    NlQueryInterpreter.ParsedQuery parsed = queryInterpreter.interpret(query, cityNames);

    Long cityId =
        parsed.cityName() == null
            ? null
            : cities.stream()
                .filter(c -> c.getName().equalsIgnoreCase(parsed.cityName()))
                .findFirst()
                .map(City::getId)
                .orElse(null);

    DateRange range = resolveDateRange(parsed.dateRangeLabel());
    String keyword = parsed.movieKeyword();

    Page<Show> results =
        showRepository.searchByTitleCityAndDateRange(cityId, keyword, range.from(), range.to(), pageable);

    return new NlSearchResult(parsed.cityName(), range.label(), keyword, results);
  }

  private DateRange resolveDateRange(String label) {
    if (label == null) {
      return new DateRange(null, null, null);
    }
    Instant now = Instant.now(clock);
    ZonedDateTime today = now.atZone(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS);
    return switch (label.toLowerCase(Locale.ROOT)) {
      case "today" -> new DateRange(today.toInstant(), today.plusDays(1).toInstant(), "today");
      case "tomorrow" -> new DateRange(today.plusDays(1).toInstant(), today.plusDays(2).toInstant(), "tomorrow");
      case "weekend" -> {
        int daysToSaturday = (DayOfWeek.SATURDAY.getValue() - today.getDayOfWeek().getValue() + 7) % 7;
        ZonedDateTime saturday = today.plusDays(daysToSaturday);
        yield new DateRange(saturday.toInstant(), saturday.plusDays(2).toInstant(), "this weekend");
      }
      case "week" -> new DateRange(now, today.plusDays(7).toInstant(), "this week");
      default -> new DateRange(null, null, null);
    };
  }

  private record DateRange(Instant from, Instant to, String label) {}

  public record NlSearchResult(String interpretedCity, String interpretedDateRange, String interpretedKeyword, Page<Show> results) {}
}
