package com.cineagent.show.port;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * The default {@link NlQueryInterpreter} — a keyword/pattern parser, no network call, no API
 * key. Always registered; used directly unless {@code app.ai.gemini.enabled=true}, and used as
 * {@link GeminiQueryInterpreter}'s fallback on any API failure. See {@code
 * NlShowSearchService}'s javadoc for the full reasoning.
 */
@Component
public class DeterministicQueryInterpreter implements NlQueryInterpreter {

  private static final Set<String> STOPWORDS =
      Set.of(
          "show", "shows", "movie", "movies", "film", "films", "in", "on", "at", "near", "me",
          "this", "the", "a", "an", "for", "please", "want", "to", "watch", "book", "tickets",
          "ticket", "i", "any", "today", "tomorrow", "weekend", "week");

  @Override
  public ParsedQuery interpret(String query, List<String> knownCityNames) {
    String normalized = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();

    String matchedCity =
        knownCityNames.stream()
            .filter(c -> normalized.contains(c.toLowerCase(Locale.ROOT)))
            .max((a, b) -> Integer.compare(a.length(), b.length()))
            .orElse(null);

    String dateRangeLabel = detectDateRangeLabel(normalized);
    String keyword = extractKeyword(normalized, matchedCity);

    return new ParsedQuery(matchedCity, dateRangeLabel, keyword.isBlank() ? null : keyword);
  }

  private String detectDateRangeLabel(String normalized) {
    if (normalized.contains("today")) {
      return "today";
    }
    if (normalized.contains("tomorrow")) {
      return "tomorrow";
    }
    if (normalized.contains("weekend")) {
      return "weekend";
    }
    if (normalized.contains("week")) {
      return "week";
    }
    return null;
  }

  private String extractKeyword(String normalized, String matchedCity) {
    String remaining = normalized;
    if (matchedCity != null) {
      remaining = remaining.replace(matchedCity.toLowerCase(Locale.ROOT), " ");
    }
    remaining = remaining.replace("this weekend", " ");
    StringBuilder keyword = new StringBuilder();
    for (String word : remaining.split("\\s+")) {
      String cleaned = word.replaceAll("[^a-z0-9]", "");
      if (cleaned.isEmpty() || STOPWORDS.contains(cleaned)) {
        continue;
      }
      keyword.append(cleaned).append(' ');
    }
    return keyword.toString().trim();
  }
}
