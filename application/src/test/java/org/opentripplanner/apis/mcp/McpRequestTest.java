package org.opentripplanner.apis.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class McpRequestTest {

  private static final McpStopLocation ORIGIN = new McpStopLocation.IdOrName("@feed:origin");
  private static final McpStopLocation DESTINATION = new McpStopLocation.IdOrName(
    "@feed:destination"
  );

  @Test
  void routeRequestKeepsOrderedStopIds() {
    var request = new McpRouteRequest(
      ORIGIN,
      DESTINATION,
      List.of(
        new McpStopLocation.IdOrName("@feed:via1"),
        new McpStopLocation.IdOrName("@feed:via2")
      ),
      McpTimeDirection.ARRIVE_BY,
      Instant.parse("2026-09-11T10:00:00Z"),
      Duration.ofMinutes(20),
      3,
      Locale.ENGLISH,
      true
    );

    assertEquals(
      List.of("@feed:via1", "@feed:via2"),
      request
        .intermediateStops()
        .stream()
        .map(stop -> ((McpStopLocation.IdOrName) stop).value())
        .toList()
    );
    assertEquals(McpTimeDirection.ARRIVE_BY, request.timeDirection());
    assertEquals(true, request.includeShapeData());
  }

  @Test
  void routeRequestRejectsTooManyIntermediateStops() {
    var stops = java.util.stream.IntStream.range(0, McpRouteRequest.MAX_INTERMEDIATE_STOPS + 1)
      .mapToObj(index -> (McpStopLocation) new McpStopLocation.IdOrName("@feed:via" + index))
      .toList();

    assertThrows(IllegalArgumentException.class, () ->
      new McpRouteRequest(
        ORIGIN,
        DESTINATION,
        stops,
        McpTimeDirection.DEPART_AT,
        Instant.now(),
        Duration.ZERO,
        1,
        Locale.ENGLISH,
        false
      )
    );
  }

  @Test
  void coordinateAndNearestStopLimitsAreValidated() {
    assertThrows(IllegalArgumentException.class, () -> new McpCoordinate(91, 0));
    assertThrows(IllegalArgumentException.class, () ->
      new McpNearestStopRequest(new McpCoordinate(0, 0), 0, 1)
    );
    assertThrows(IllegalArgumentException.class, () ->
      new McpNearestStopRequest(new McpCoordinate(0, 0), 100, 21)
    );
  }
}
