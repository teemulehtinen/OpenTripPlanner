package org.opentripplanner.apis.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.id.FeedScopedId;

class McpRequestTest {

  private static final FeedScopedId ORIGIN = FeedScopedId.parse("feed:origin");
  private static final FeedScopedId DESTINATION = FeedScopedId.parse("feed:destination");

  @Test
  void routeRequestKeepsOrderedStopIds() {
    var request = new McpRouteRequest(
      ORIGIN,
      DESTINATION,
      List.of(FeedScopedId.parse("feed:via1"), FeedScopedId.parse("feed:via2")),
      McpTimeDirection.ARRIVE_BY,
      Instant.parse("2026-09-11T10:00:00Z"),
      Duration.ofMinutes(20),
      3,
      Locale.ENGLISH
    );

    assertEquals(
      List.of("feed:via1", "feed:via2"),
      request.intermediateStops().stream().map(stopId -> stopId.toString()).toList()
    );
    assertEquals(McpTimeDirection.ARRIVE_BY, request.timeDirection());
  }

  @Test
  void routeRequestRejectsTooManyIntermediateStops() {
    var stops = java.util.stream.IntStream.range(0, McpRouteRequest.MAX_INTERMEDIATE_STOPS + 1)
      .mapToObj(index -> FeedScopedId.parse("feed:via" + index))
      .toList();

    assertThrows(
      IllegalArgumentException.class,
      () -> new McpRouteRequest(
        ORIGIN,
        DESTINATION,
        stops,
        McpTimeDirection.DEPART_AT,
        Instant.now(),
        Duration.ZERO,
        1,
        Locale.ENGLISH
      )
    );
  }

  @Test
  void coordinateAndNearestStopLimitsAreValidated() {
    assertThrows(IllegalArgumentException.class, () -> new McpCoordinate(91, 0));
    assertThrows(
      IllegalArgumentException.class,
      () -> new McpNearestStopRequest(new McpCoordinate(0, 0), 0, 1)
    );
    assertThrows(
      IllegalArgumentException.class,
      () -> new McpNearestStopRequest(new McpCoordinate(0, 0), 100, 21)
    );
  }
}
