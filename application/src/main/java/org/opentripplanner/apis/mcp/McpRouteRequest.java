package org.opentripplanner.apis.mcp;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import org.opentripplanner.transit.model.basic.TransitMode;

/** Transport-independent input for the first MCP routing tool. */
public record McpRouteRequest(
  McpStopLocation origin,
  McpStopLocation destination,
  List<McpStopLocation> intermediateStops,
  McpTimeDirection timeDirection,
  Instant time,
  Duration searchWindow,
  int maxItineraries,
  List<TransitMode> transitModes,
  Locale locale,
  boolean includeShapeData
) {
  public static final int MAX_INTERMEDIATE_STOPS = 10;
  public static final int MAX_ITINERARIES = 10;
  public static final Duration MAX_SEARCH_WINDOW = Duration.ofHours(24);
  public static final Duration DEFAULT_SEARCH_WINDOW = MAX_SEARCH_WINDOW;
  public static final int DEFAULT_MAX_ITINERARIES = MAX_ITINERARIES;

  public McpRouteRequest {
    if (origin == null || destination == null) {
      throw new IllegalArgumentException("Origin and destination stops are required.");
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
    if (transitModes == null) {
      transitModes = List.of();
    } else {
      transitModes = List.copyOf(transitModes);
    }
    if (transitModes.size() > TransitMode.values().length) {
      throw new IllegalArgumentException(
        "At most %d transit modes are supported.".formatted(TransitMode.values().length)
      );
    }
    if (new HashSet<>(transitModes).size() != transitModes.size()) {
      throw new IllegalArgumentException("Transit modes must not contain duplicates.");
    }
    if (locale == null) {
      locale = Locale.ENGLISH;
    }
  }
}
