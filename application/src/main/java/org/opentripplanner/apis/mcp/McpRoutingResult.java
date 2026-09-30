package org.opentripplanner.apis.mcp;

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
    String departure,
    String arrival,
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
    String departure,
    String arrival,
    long durationSeconds,
    McpPlace from,
    McpPlace to,
    String routeId,
    String routeName,
    String agencyName,
    String geometryId
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
