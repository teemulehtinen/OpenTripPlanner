package org.opentripplanner.apis.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.opentripplanner.routing.api.RoutingService;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.routing.api.response.RoutingResponse;
import org.opentripplanner.routing.framework.DebugTimingAggregator;
import org.opentripplanner.standalone.api.OtpServerRequestContext;

class McpRoutingServiceTest {

  private static final Instant SEARCH_TIME = Instant.parse("2026-09-10T08:15:00Z");

  @Test
  void retriesDepartAtSearchAtHalfTheSearchWindow() {
    var routingService = mock(RoutingService.class);
    var emptyResponse = emptyResponse();
    when(routingService.route(org.mockito.ArgumentMatchers.any(RouteRequest.class))).thenReturn(
      emptyResponse
    );
    var service = new McpRoutingService(serverContext(routingService));

    service.route(routeRequest(McpTimeDirection.DEPART_AT, Duration.ofHours(24)));

    var requests = ArgumentCaptor.forClass(RouteRequest.class);
    verify(routingService, times(3)).route(requests.capture());
    assertEquals(Instant.parse("2026-09-10T20:15:00Z"), requests.getAllValues().get(1).dateTime());
    assertEquals(Instant.parse("2026-09-11T08:15:00Z"), requests.getAllValues().get(2).dateTime());
  }

  @Test
  void retriesArriveBySearchAtHalfTheSearchWindow() {
    assertEquals(
      Instant.parse("2026-09-09T20:15:00Z"),
      McpRoutingService.shiftSearchTime(
        SEARCH_TIME,
        Duration.ofHours(12),
        McpTimeDirection.ARRIVE_BY
      )
    );
  }

  @Test
  void doesNotRetryAZeroLengthSearchWindow() {
    var routingService = mock(RoutingService.class);
    when(routingService.route(org.mockito.ArgumentMatchers.any(RouteRequest.class))).thenReturn(
      emptyResponse()
    );
    var service = new McpRoutingService(serverContext(routingService));

    service.route(routeRequest(McpTimeDirection.DEPART_AT, Duration.ZERO));

    verify(routingService).route(org.mockito.ArgumentMatchers.any(RouteRequest.class));
  }

  private static OtpServerRequestContext serverContext(RoutingService routingService) {
    var serverContext = mock(OtpServerRequestContext.class);
    when(serverContext.defaultRouteRequest()).thenReturn(RouteRequest.defaultValue());
    when(serverContext.baseDirectory()).thenReturn(new File("."));
    when(serverContext.routingService()).thenReturn(routingService);
    return serverContext;
  }

  private static McpRouteRequest routeRequest(
    McpTimeDirection timeDirection,
    Duration searchWindow
  ) {
    return new McpRouteRequest(
      new McpStopLocation.StopId("agency:A"),
      new McpStopLocation.StopId("agency:B"),
      List.of(),
      timeDirection,
      SEARCH_TIME,
      searchWindow,
      10,
      List.of(),
      Locale.ENGLISH,
      false
    );
  }

  private static RoutingResponse emptyResponse() {
    return new RoutingResponse(null, null, null, null, List.of(), new DebugTimingAggregator());
  }
}
