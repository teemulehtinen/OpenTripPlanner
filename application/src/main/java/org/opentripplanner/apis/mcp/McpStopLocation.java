package org.opentripplanner.apis.mcp;

/** A stop ID, name, or coordinate pair to resolve to a stop. */
public sealed interface McpStopLocation
  permits McpStopLocation.StopId, McpStopLocation.Name, McpStopLocation.Coordinate {
  record StopId(String value) implements McpStopLocation {
    public StopId {
      if (value == null || value.isBlank()) {
        throw new IllegalArgumentException("A stop ID is required.");
      }
      value = value.trim();
    }
  }

  record Name(String value) implements McpStopLocation {
    public Name {
      if (value == null || value.isBlank()) {
        throw new IllegalArgumentException("A stop name is required.");
      }
      value = value.trim();
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
