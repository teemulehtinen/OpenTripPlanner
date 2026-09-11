package org.opentripplanner.apis.mcp;

import java.util.List;
import org.opentripplanner.model.plan.Itinerary;
import org.opentripplanner.model.plan.Leg;
import org.opentripplanner.routing.api.response.RoutingError;
import org.opentripplanner.routing.api.response.RoutingResponse;

/** Maps OTP's routing response to the bounded MCP route result. */
public final class McpRoutingResultMapper {

  private McpRoutingResultMapper() {}

  public static McpRoutingResult map(RoutingResponse response) {
    var itineraries = response.getTripPlan() == null
      ? List.<McpRoutingResult.McpItinerary>of()
      : response
          .getTripPlan()
          .itineraries.stream()
          .map(McpRoutingResultMapper::mapItinerary)
          .toList();

    return new McpRoutingResult(
      itineraries,
      response.getRoutingErrors().stream().map(McpRoutingResultMapper::mapError).toList(),
      response.getNextPageCursor() != null
    );
  }

  private static McpRoutingResult.McpItinerary mapItinerary(Itinerary itinerary) {
    return new McpRoutingResult.McpItinerary(
      itinerary.startTimeAsInstant(),
      itinerary.endTimeAsInstant(),
      itinerary.totalDuration().toSeconds(),
      itinerary.numberOfTransfers(),
      itinerary.legs().stream().map(McpRoutingResultMapper::mapLeg).toList()
    );
  }

  private static McpRoutingResult.McpLeg mapLeg(Leg leg) {
    var from = leg.from();
    var to = leg.to();
    var fromStopId = from.stop == null ? null : from.stop.getId().toString();
    var toStopId = to.stop == null ? null : to.stop.getId().toString();
    var route = leg.route();
    return new McpRoutingResult.McpLeg(
      leg.isTransitLeg(),
      leg.startTime().toInstant(),
      leg.endTime().toInstant(),
      leg.duration().toSeconds(),
      fromStopId,
      toStopId,
      route == null ? null : route.getId().toString(),
      route == null ? null : route.getName().toString()
    );
  }

  private static McpRoutingResult.McpRoutingError mapError(RoutingError error) {
    return new McpRoutingResult.McpRoutingError(
      error.code.name(),
      error.inputField == null ? null : error.inputField.name()
    );
  }
}
