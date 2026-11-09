package org.opentripplanner.apis.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.when;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.routing.api.request.request.filter.TransitFilterRequest;
import org.opentripplanner.standalone.api.OtpServerRequestContext;
import org.opentripplanner.transit.model.basic.MainAndSubMode;
import org.opentripplanner.transit.model.basic.TransitMode;

class McpRouteRequestMapperTest {

  @Test
  void mapsTransitModesToAnOtpTransitFilter() {
    var request = McpRouteRequestMapper.map(
      routeRequest(List.of(TransitMode.RAIL, TransitMode.FERRY)),
      serverContext()
    );
    var filter = assertInstanceOf(
      TransitFilterRequest.class,
      request.journey().transit().filters().get(0)
    );

    assertEquals(
      List.of(new MainAndSubMode(TransitMode.RAIL), new MainAndSubMode(TransitMode.FERRY)),
      filter.select().get(0).transportModes()
    );
  }

  @Test
  void keepsConfiguredTransitFiltersWhenNoModeIsSpecified() {
    var defaultRequest = RouteRequest.defaultValue();
    var request = McpRouteRequestMapper.map(routeRequest(List.of()), serverContext());

    assertEquals(
      defaultRequest.journey().transit().filters(),
      request.journey().transit().filters()
    );
  }

  private static OtpServerRequestContext serverContext() {
    var serverContext = Mockito.mock(OtpServerRequestContext.class);
    when(serverContext.defaultRouteRequest()).thenReturn(RouteRequest.defaultValue());
    when(serverContext.baseDirectory()).thenReturn(new File("."));
    return serverContext;
  }

  private static McpRouteRequest routeRequest(List<TransitMode> transitModes) {
    return new McpRouteRequest(
      new McpStopLocation.IdOrName("@agency:A"),
      new McpStopLocation.IdOrName("@agency:B"),
      List.of(),
      McpTimeDirection.DEPART_AT,
      Instant.parse("2026-09-10T08:15:00Z"),
      Duration.ofHours(24),
      10,
      transitModes,
      Locale.ENGLISH,
      false
    );
  }
}
