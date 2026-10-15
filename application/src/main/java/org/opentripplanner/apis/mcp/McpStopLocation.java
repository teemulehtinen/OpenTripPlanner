package org.opentripplanner.apis.mcp;

/** A stop ID or name, or a coordinate to resolve to a stop. */
public sealed interface McpStopLocation
  permits McpStopLocation.IdOrName, McpStopLocation.Coordinate {
  record IdOrName(String value) implements McpStopLocation {
    public IdOrName {
      if (value == null || value.isBlank()) {
        throw new IllegalArgumentException("A stop ID or name is required.");
      }
    }
  }

  record Coordinate(McpCoordinate value) implements McpStopLocation {
    public Coordinate {
      if (value == null) {
        throw new IllegalArgumentException("A stop coordinate is required.");
      }
    }
  }
}
