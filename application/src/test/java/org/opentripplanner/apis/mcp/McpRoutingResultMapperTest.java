package org.opentripplanner.apis.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.opentripplanner.model.plan.Place;
import org.opentripplanner.model.plan.TransitLeg;
import org.opentripplanner.model.plan.leg.StreetLeg;
import org.opentripplanner.street.search.TraverseMode;
import org.opentripplanner.transit.model.basic.TransitMode;

class McpRoutingResultMapperTest {

  @Test
  void onlyKeepsLongIntermediateWalksAndLabelsThemAsTransfers() {
    var legs = List.of(
      walk(301, 100),
      transit(500),
      walk(300, 200),
      walk(301, -1),
      transit(600),
      walk(301, 300)
    );

    var result = McpRoutingResultMapper.mapLegs(legs, false);

    assertEquals(
      List.of("BUS", "TRANSFER", "BUS"),
      result.stream().map(McpRoutingResult.McpLeg::mode).toList()
    );
    assertEquals(500, result.get(0).distanceMeters());
    assertTrue(result.get(1).distanceMeters() > 0);
    assertEquals(600, result.get(2).distanceMeters());
  }

  private static StreetLeg walk(long durationSeconds, double distanceMeters) {
    var start = Instant.parse("2026-09-10T08:00:00Z");
    return StreetLeg.of()
      .withMode(TraverseMode.WALK)
      .withStartTime(start.atZone(ZoneOffset.UTC))
      .withEndTime(start.plusSeconds(durationSeconds).atZone(ZoneOffset.UTC))
      .withFrom(Place.normal(60.0, 24.0, null))
      .withTo(Place.normal(60.0, 24.1, null))
      .withDistanceMeters(distanceMeters)
      .build();
  }

  private static TransitLeg transit(double distanceMeters) {
    var leg = Mockito.mock(TransitLeg.class);
    var start = Instant.parse("2026-09-10T08:00:00Z");
    when(leg.isTransitLeg()).thenReturn(true);
    when(leg.mode()).thenReturn(TransitMode.BUS);
    when(leg.startTime()).thenReturn(start.atZone(ZoneOffset.UTC));
    when(leg.endTime()).thenReturn(start.plusSeconds(600).atZone(ZoneOffset.UTC));
    when(leg.duration()).thenReturn(Duration.ofMinutes(10));
    when(leg.from()).thenReturn(Place.normal(60.0, 24.0, null));
    when(leg.to()).thenReturn(Place.normal(60.0, 24.1, null));
    when(leg.distanceMeters()).thenReturn(distanceMeters);
    return leg;
  }
}
