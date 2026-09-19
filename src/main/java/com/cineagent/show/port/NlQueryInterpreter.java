package com.cineagent.show.port;

import java.util.List;

/**
 * The third real port in this codebase (with {@code PaymentGateway} and {@code
 * NotificationSender}) — earned its place the moment a second real implementation
 * ({@code GeminiQueryInterpreter}) existed alongside the deterministic default, per AGENTS.md's
 * port discipline. Extracts three raw signals from free text; date-phrase-to-Instant resolution
 * and city-name-to-id resolution both stay in {@code NlShowSearchService}, so neither
 * implementation needs database access.
 */
public interface NlQueryInterpreter {

  ParsedQuery interpret(String query, List<String> knownCityNames);

  /**
   * @param cityName exactly one of {@code knownCityNames}, or null if none matched
   * @param dateRangeLabel one of "today", "tomorrow", "weekend", "week", or null
   * @param movieKeyword free-text title substring to match, or null
   */
  record ParsedQuery(String cityName, String dateRangeLabel, String movieKeyword) {}
}
