package org.opentripplanner.apis.mcp;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.opentripplanner.core.model.id.FeedScopedId;

/** Transport-independent input for the first MCP routing tool. */
public record McpRouteRequest(
  FeedScopedId origin,
  FeedScopedId destination,
  List<FeedScopedId> intermediateStops,
  McpTimeDirection timeDirection,
  Instant time,
  Duration searchWindow,
  int maxItineraries,
  Locale locale
) {
  public static final int MAX_INTERMEDIATE_STOPS = 10;
  public static final int MAX_ITINERARIES = 10;
  public static final Duration MAX_SEARCH_WINDOW = Duration.ofHours(24);
  public static final Duration DEFAULT_SEARCH_WINDOW = MAX_SEARCH_WINDOW;
  public static final int DEFAULT_MAX_ITINERARIES = MAX_ITINERARIES;

  public McpRouteRequest {
    if (origin == null || destination == null) {
      throw new IllegalArgumentException("Origin and destination stop IDs are required.");
    }
    if (intermediateStops == null) {
      intermediateStops = List.of();
    } else {
      intermediateStops = List.copyOf(intermediateStops);
    }
    if (intermediateStops.size() > MAX_INTERMEDIATE_STOPS) {
      throw new IllegalArgumentException(
        "At most %d intermediate stops are supported.".formatted(MAX_INTERMEDIATE_STOPS)
      );
    }
    if (timeDirection == null) {
      timeDirection = McpTimeDirection.DEPART_AT;
    }
    if (time == null) {
      time = Instant.now();
    }
    if (searchWindow == null) {
      searchWindow = DEFAULT_SEARCH_WINDOW;
    }
    if (
      searchWindow == null ||
      searchWindow.isNegative() ||
      searchWindow.compareTo(MAX_SEARCH_WINDOW) > 0
    ) {
      throw new IllegalArgumentException(
        "Search window must be between zero and %s.".formatted(MAX_SEARCH_WINDOW)
      );
    }
    if (maxItineraries < 1 || maxItineraries > MAX_ITINERARIES) {
      throw new IllegalArgumentException(
        "Max itineraries must be between 1 and %d.".formatted(MAX_ITINERARIES)
      );
    }
    if (locale == null) {
      locale = Locale.ENGLISH;
    }
  }
}
