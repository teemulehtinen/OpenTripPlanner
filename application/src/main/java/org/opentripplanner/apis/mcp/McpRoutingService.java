package org.opentripplanner.apis.mcp;

import java.util.List;
import org.opentripplanner.standalone.api.OtpServerRequestContext;

/** Application service for the first MCP routing tools. */
public final class McpRoutingService {

  private final OtpServerRequestContext serverContext;

  public McpRoutingService(OtpServerRequestContext serverContext) {
    this.serverContext = serverContext;
  }

  /** Route between existing transit stops, optionally visiting ordered intermediate stops. */
  public McpRoutingResult route(McpRouteRequest input) {
    var request = McpRouteRequestMapper.map(input, serverContext);
    return McpRoutingResultMapper.map(serverContext.routingService().route(request));
  }

  /** Find transit stops near a coordinate using the graph's street-independent fallback if needed. */
  public List<McpNearestStop> findNearestStops(McpNearestStopRequest input) {
    return McpNearestStopService.find(input, serverContext);
  }
}
