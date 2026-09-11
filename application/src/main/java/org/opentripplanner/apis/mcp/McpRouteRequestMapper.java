package org.opentripplanner.apis.mcp;

import java.util.List;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.model.GenericLocation;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.routing.api.request.via.PassThroughViaLocation;
import org.opentripplanner.routing.api.request.via.ViaLocation;
import org.opentripplanner.standalone.api.OtpServerRequestContext;

/** Maps the MCP routing contract to OTP's shared routing request. */
public final class McpRouteRequestMapper {

  private McpRouteRequestMapper() {}

  /** Create an OTP request while preserving the router's configured defaults. */
  public static RouteRequest map(McpRouteRequest input, OtpServerRequestContext serverContext) {
    var request = serverContext.defaultRouteRequest().copyOf();
    request
      .withFrom(toGenericLocation(input.origin()))
      .withTo(toGenericLocation(input.destination()))
      .withDateTime(input.time())
      .withArriveBy(input.timeDirection() == McpTimeDirection.ARRIVE_BY)
      .withSearchWindow(input.searchWindow())
      .withNumItineraries(input.maxItineraries())
      .withViaLocations(toViaLocations(input.intermediateStops()));
    request.withPreferences(preferences -> {
      preferences.withLocale(input.locale());
      preferences.withStreet(street ->
        street.withAccessEgress(accessEgress ->
          accessEgress.withDistanceOnlyStationTransfers(true, 10000)
        )
      );
    });
    return request.buildRequest();
  }

  private static GenericLocation toGenericLocation(FeedScopedId stopId) {
    return GenericLocation.fromStopId(stopId);
  }

  private static List<ViaLocation> toViaLocations(List<FeedScopedId> stopIds) {
    return stopIds
      .stream()
      .<ViaLocation>map(McpRouteRequestMapper::toPassThroughViaLocation)
      .toList();
  }

  private static PassThroughViaLocation toPassThroughViaLocation(FeedScopedId stopId) {
    return new PassThroughViaLocation(null, List.of(stopId));
  }
}
