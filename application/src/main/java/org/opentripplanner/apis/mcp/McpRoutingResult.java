package org.opentripplanner.apis.mcp;

import java.time.Instant;
import java.util.List;

/** Stable, transport-independent output for the MCP route tool. */
public record McpRoutingResult(
  List<McpItinerary> itineraries,
  List<McpRoutingError> errors,
  boolean hasMoreResults
) {
  public McpRoutingResult {
    itineraries = List.copyOf(itineraries);
    errors = List.copyOf(errors);
  }

  public record McpItinerary(
    Instant departure,
    Instant arrival,
    long durationSeconds,
    int transfers,
    List<McpLeg> legs
  ) {
    public McpItinerary {
      legs = List.copyOf(legs);
    }
  }

  public record McpLeg(
    boolean transit,
    Instant departure,
    Instant arrival,
    long durationSeconds,
    String fromStopId,
    String toStopId,
    String routeId,
    String routeName
  ) {}

  public record McpRoutingError(String code, String inputField) {}
}
