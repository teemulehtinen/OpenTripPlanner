package org.opentripplanner.apis.mcp;

/** Input for finding transit stops near a coordinate. */
public record McpNearestStopRequest(McpCoordinate coordinate, double radiusMeters, int maxResults) {
  public static final double MAX_RADIUS_METERS = 100_000;
  public static final int MAX_RESULTS = 20;

  public McpNearestStopRequest {
    if (coordinate == null) {
      throw new IllegalArgumentException("Coordinate is required.");
    }
    if (radiusMeters <= 0 || radiusMeters > MAX_RADIUS_METERS) {
      throw new IllegalArgumentException(
        "Radius must be greater than zero and at most %s meters.".formatted(MAX_RADIUS_METERS)
      );
    }
    if (maxResults < 1 || maxResults > MAX_RESULTS) {
      throw new IllegalArgumentException(
        "Max results must be between 1 and %d.".formatted(MAX_RESULTS)
      );
    }
  }
}
