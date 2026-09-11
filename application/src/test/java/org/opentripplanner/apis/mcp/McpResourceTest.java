package org.opentripplanner.apis.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpResourceTest {

  @Test
  void parseRouteRequestFromToolArguments() {
    var request = McpResource.parseRouteRequest(
      Map.of(
        "origin",
        "agency:A",
        "destination",
        "agency:B",
        "intermediateStops",
        List.of("agency:C", "agency:D"),
        "time",
        "2026-09-10T08:15:00Z",
        "timeDirection",
        "DEPART_AT",
        "searchWindowSeconds",
        1800,
        "maxItineraries",
        3
      )
    );

    assertNotNull(request);
    assertEquals("agency:A", request.origin().toString());
    assertEquals("agency:B", request.destination().toString());
    assertEquals(
      List.of("agency:C", "agency:D"),
      request.intermediateStops().stream().map(stopId -> stopId.toString()).toList()
    );
    assertEquals(Instant.parse("2026-09-10T08:15:00Z"), request.time());
    assertEquals(McpTimeDirection.DEPART_AT, request.timeDirection());
    assertEquals(Duration.ofMinutes(30), request.searchWindow());
    assertEquals(3, request.maxItineraries());
  }
}
