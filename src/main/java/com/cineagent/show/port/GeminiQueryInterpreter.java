package com.cineagent.show.port;

import com.cineagent.common.config.GeminiProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Real LLM-backed query understanding. {@code @Primary} so it's preferred over {@link
 * DeterministicQueryInterpreter} whenever both beans exist (i.e. whenever it's enabled), but it
 * wraps that same deterministic instance as its own fallback — any failure (missing/invalid key,
 * network error, malformed response, timeout) degrades to the deterministic parser rather than
 * failing the request. The API key goes in a header, never the URL, so it can never leak into a
 * logged exception message via the request URI.
 */
@Component
@Primary
@ConditionalOnProperty(prefix = "app.ai.gemini", name = "enabled", havingValue = "true")
public class GeminiQueryInterpreter implements NlQueryInterpreter {

  private static final Logger log = LoggerFactory.getLogger(GeminiQueryInterpreter.class);
  private static final String ENDPOINT_TEMPLATE =
      "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";

  private final DeterministicQueryInterpreter fallback;
  private final GeminiProperties properties;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;

  public GeminiQueryInterpreter(DeterministicQueryInterpreter fallback, GeminiProperties properties, JsonMapper jsonMapper) {
    this.fallback = fallback;
    this.properties = properties;
    this.jsonMapper = jsonMapper;
    this.httpClient = HttpClient.newBuilder().connectTimeout(properties.timeout()).build();
  }

  @Override
  public ParsedQuery interpret(String query, List<String> knownCityNames) {
    try {
      String responseBody = callGemini(buildPrompt(query, knownCityNames));
      return parseResponse(responseBody, knownCityNames);
    } catch (Exception e) {
      log.warn("Gemini NL-search call failed, falling back to the deterministic parser: {}", e.toString());
      return fallback.interpret(query, knownCityNames);
    }
  }

  private String buildPrompt(String query, List<String> knownCityNames) {
    return """
        You are a search-query parser for a movie ticket booking system. Extract structured
        filters from the user's natural-language query. Respond with ONLY a JSON object, no
        markdown fencing, no explanation, matching exactly this schema:
        {"city": string or null, "dateRange": one of "today"|"tomorrow"|"weekend"|"week" or null, "movieKeyword": string or null}

        For "city", choose one value EXACTLY as spelled from this list, or null if none is
        mentioned or implied: %s

        Query: "%s"
        """
        .formatted(String.join(", ", knownCityNames), query == null ? "" : query.replace("\"", "'"));
  }

  private String callGemini(String prompt) throws Exception {
    ObjectNode root = jsonMapper.createObjectNode();
    ArrayNode contents = root.putArray("contents");
    ArrayNode parts = contents.addObject().putArray("parts");
    parts.addObject().put("text", prompt);
    root.putObject("generationConfig").put("responseMimeType", "application/json");

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(ENDPOINT_TEMPLATE.formatted(properties.model())))
            .timeout(properties.timeout())
            .header("Content-Type", "application/json")
            .header("x-goog-api-key", properties.apiKey())
            .POST(HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(root)))
            .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 200) {
      // Deliberately not including the response body -- Gemini error bodies can echo request
      // details back; the status code alone is enough to log safely.
      throw new IllegalStateException("Gemini API returned HTTP " + response.statusCode());
    }
    return response.body();
  }

  private ParsedQuery parseResponse(String responseBody, List<String> knownCityNames) {
    JsonNode root = jsonMapper.readTree(responseBody);
    String innerJson =
        root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asString();
    JsonNode parsed = jsonMapper.readTree(innerJson);

    String rawCity = textOrNull(parsed.path("city"));
    // Never trust the model's spelling blindly -- only accept a city that's an exact match
    // (case-insensitive) against what the system actually knows about.
    String city =
        rawCity != null && knownCityNames.stream().anyMatch(c -> c.equalsIgnoreCase(rawCity)) ? rawCity : null;
    String dateRange = textOrNull(parsed.path("dateRange"));
    String keyword = textOrNull(parsed.path("movieKeyword"));
    return new ParsedQuery(city, dateRange, keyword);
  }

  private String textOrNull(JsonNode node) {
    if (node.isNull() || node.isMissingNode()) {
      return null;
    }
    String text = node.asString();
    return (text == null || text.isBlank()) ? null : text;
  }
}
