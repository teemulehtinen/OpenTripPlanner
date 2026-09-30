package org.opentripplanner.apis.mcp;

/** A stop returned by the nearest-stop MCP tool. */
public record McpNearestStop(
  String id,
  String name,
  double latitude,
  double longitude,
  double distanceMeters
) {}
