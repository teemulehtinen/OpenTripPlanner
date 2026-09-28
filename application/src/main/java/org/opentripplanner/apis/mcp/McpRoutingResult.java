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
    String mode,
    Instant departure,
    Instant arrival,
    long durationSeconds,
    McpPlace from,
    McpPlace to,
    String routeId,
    String routeName,
    String agencyName,
    String geometry
  ) {}

  public record McpPlace(
    String stopId,
    String name,
    Double latitude,
    Double longitude,
    String platformCode
  ) {}

  public record McpRoutingError(String code, String inputField) {}
}
