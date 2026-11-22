package org.opentripplanner.apis.mcp;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.opentripplanner.routing.api.response.RoutingResponse;
import org.opentripplanner.standalone.api.OtpServerRequestContext;

/** Application service for the first MCP routing tools. */
public final class McpRoutingService {

  static final int MAX_SEARCH_ATTEMPTS = 3;

  private final OtpServerRequestContext serverContext;

  public McpRoutingService(OtpServerRequestContext serverContext) {
    this.serverContext = serverContext;
  }

  /** Route between existing transit stops, optionally visiting ordered intermediate stops. */
  public McpRoutingResult route(McpRouteRequest input) {
    var request = McpRouteRequestMapper.map(input, serverContext);
    var routingService = serverContext.routingService();
    var response = routingService.route(request);

    if (!hasItineraries(response) && !input.searchWindow().isZero()) {
      var searchOffset = input.searchWindow().dividedBy(2);
      var searchTime = input.time();
      for (var attempt = 1; attempt < MAX_SEARCH_ATTEMPTS; attempt++) {
        searchTime = shiftSearchTime(searchTime, searchOffset, input.timeDirection());
        request = request.copyOf().withDateTime(searchTime).buildRequest();
        response = routingService.route(request);
        if (hasItineraries(response)) {
          break;
        }
      }
    }

    return McpRoutingResultMapper.map(response, input.includeShapeData());
  }

  private static boolean hasItineraries(RoutingResponse response) {
    return response.getTripPlan() != null && !response.getTripPlan().itineraries.isEmpty();
  }

  static Instant shiftSearchTime(
    Instant time,
    Duration searchOffset,
    McpTimeDirection timeDirection
  ) {
    return timeDirection == McpTimeDirection.ARRIVE_BY
      ? time.minus(searchOffset)
      : time.plus(searchOffset);
  }

  /** Find transit stops near a coordinate using the graph's street-independent fallback if needed. */
  public List<McpNearestStop> findNearestStops(McpNearestStopRequest input) {
    return McpNearestStopService.find(input, serverContext);
  }

  /** Search the cached place index for autocomplete suggestions. */
  public List<McpPlaceSuggestion> searchPlaces(String query, int limit) {
    return McpPlaceIndex.load(serverContext.baseDirectory()).search(query, limit);
  }
}
