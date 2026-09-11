package org.opentripplanner.apis.mcp;

import java.util.List;
import java.util.Objects;
import org.opentripplanner.routing.graphfinder.NearbyStop;
import org.opentripplanner.standalone.api.OtpServerRequestContext;

/** Finds stops using OTP's graph-aware finder, including straight-line lookup without streets. */
public final class McpNearestStopService {

  private McpNearestStopService() {}

  public static List<McpNearestStop> find(
    McpNearestStopRequest input,
    OtpServerRequestContext serverContext
  ) {
    var coordinate = input.coordinate().toWgsCoordinate();
    return serverContext
      .graphFinder()
      .findClosestStops(coordinate.asJtsCoordinate(), input.radiusMeters())
      .stream()
      .limit(input.maxResults())
      .map(McpNearestStopService::map)
      .toList();
  }

  private static McpNearestStop map(NearbyStop nearbyStop) {
    var stop = nearbyStop.stop;
    return new McpNearestStop(
      stop.getId(),
      Objects.toString(stop.getName(), ""),
      stop.getLat(),
      stop.getLon(),
      nearbyStop.distance
    );
  }
}
