package org.opentripplanner.apis.mcp;

import java.util.List;
import org.opentripplanner.api.model.geometry.EncodedPolyline;
import org.opentripplanner.model.plan.Itinerary;
import org.opentripplanner.model.plan.Leg;
import org.opentripplanner.model.plan.TransitLeg;
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
      itinerary.startTimeAsInstant().toString(),
      itinerary.endTimeAsInstant().toString(),
      itinerary.totalDuration().toSeconds(),
      itinerary.numberOfTransfers(),
      itinerary.legs().stream().map(McpRoutingResultMapper::mapLeg).toList()
    );
  }

  private static McpRoutingResult.McpLeg mapLeg(Leg leg) {
    var from = leg.from();
    var to = leg.to();
    var route = leg.route();
    var agency = leg.agency();
    var coordinate = from.coordinate;
    var fromPlace = new McpRoutingResult.McpPlace(
      from.stop == null ? null : from.stop.getId().toString(),
      from.name == null ? null : from.name.toString(),
      coordinate == null ? null : coordinate.latitude(),
      coordinate == null ? null : coordinate.longitude(),
      from.stop == null ? null : from.stop.getPlatformCode()
    );
    coordinate = to.coordinate;
    var toPlace = new McpRoutingResult.McpPlace(
      to.stop == null ? null : to.stop.getId().toString(),
      to.name == null ? null : to.name.toString(),
      coordinate == null ? null : coordinate.latitude(),
      coordinate == null ? null : coordinate.longitude(),
      to.stop == null ? null : to.stop.getPlatformCode()
    );
    var geometry = leg.legGeometry();
    return new McpRoutingResult.McpLeg(
      leg.isTransitLeg(),
      leg instanceof TransitLeg transitLeg ? transitLeg.mode().name() : "WALK",
      leg.startTime().toInstant().toString(),
      leg.endTime().toInstant().toString(),
      leg.duration().toSeconds(),
      fromPlace,
      toPlace,
      route == null ? null : route.getId().toString(),
      route == null ? null : route.getName().toString(),
      agency == null ? null : agency.getName(),
      geometry == null ? null : McpGeometryStore.store(EncodedPolyline.of(geometry).points())
    );
  }

  private static McpRoutingResult.McpRoutingError mapError(RoutingError error) {
    return new McpRoutingResult.McpRoutingError(
      error.code.name(),
      error.inputField == null ? null : error.inputField.name()
    );
  }
}
