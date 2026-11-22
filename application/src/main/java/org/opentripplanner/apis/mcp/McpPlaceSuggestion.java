package org.opentripplanner.apis.mcp;

/** A place-name suggestion resolved to a feed-scoped stop. */
public record McpPlaceSuggestion(
  String name,
  String placeId,
  String stopId,
  String countryCode,
  String countryName,
  long population
) {}
