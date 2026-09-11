package org.opentripplanner.apis.mcp;

import org.opentripplanner.core.model.id.FeedScopedId;

/** A stop returned by the nearest-stop MCP tool. */
public record McpNearestStop(
  FeedScopedId id,
  String name,
  double latitude,
  double longitude,
  double distanceMeters
) {}
