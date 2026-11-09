package org.opentripplanner.apis.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
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
        "@agency:A",
        "destination",
        "@agency:B",
        "intermediateStops",
        List.of("@agency:C", "@agency:D"),
        "time",
        "2026-09-10T08:15:00Z",
        "timeDirection",
        "DEPART_AT",
        "searchWindowSeconds",
        1800,
        "maxItineraries",
        3,
        "includeShapeData",
        true
      )
    );

    assertNotNull(request);
    assertEquals("@agency:A", ((McpStopLocation.IdOrName) request.origin()).value());
    assertEquals("@agency:B", ((McpStopLocation.IdOrName) request.destination()).value());
    assertEquals(
      List.of("@agency:C", "@agency:D"),
      request
        .intermediateStops()
        .stream()
        .map(stop -> ((McpStopLocation.IdOrName) stop).value())
        .toList()
    );
    assertEquals(Instant.parse("2026-09-10T08:15:00Z"), request.time());
    assertEquals(McpTimeDirection.DEPART_AT, request.timeDirection());
    assertEquals(Duration.ofMinutes(30), request.searchWindow());
    assertEquals(3, request.maxItineraries());
    assertEquals(true, request.includeShapeData());
  }

  @Test
  void parseRouteRequestUsesDefaultsForOptionalArguments() {
    var beforeParsing = Instant.now();
    var request = McpResource.parseRouteRequest(
      Map.of("origin", "@agency:A", "destination", "@agency:B")
    );
    var afterParsing = Instant.now();

    assertEquals(McpTimeDirection.DEPART_AT, request.timeDirection());
    assertFalse(request.time().isBefore(beforeParsing));
    assertFalse(request.time().isAfter(afterParsing));
    assertEquals(Duration.ofHours(24), request.searchWindow());
    assertEquals(10, request.maxItineraries());
    assertEquals(false, request.includeShapeData());
  }

  @Test
  void parseRouteRequestRejectsNonBooleanShapeOption() {
    org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
      McpResource.parseRouteRequest(
        Map.of("origin", "@agency:A", "destination", "@agency:B", "includeShapeData", "true")
      )
    );
  }

  @Test
  void parseRouteRequestAcceptsStopNamesAndCoordinateObjects() {
    var request = McpResource.parseRouteRequest(
      Map.of(
        "origin",
        "Central Station",
        "destination",
        Map.of("latitude", 60.17, "longitude", 24.94),
        "intermediateStops",
        List.of("Harbor", Map.of("latitude", 60.16, "longitude", 24.95))
      )
    );

    assertEquals("Central Station", ((McpStopLocation.IdOrName) request.origin()).value());
    var destination = assertInstanceOf(McpStopLocation.Coordinate.class, request.destination());
    assertEquals(new McpCoordinate(60.17, 24.94), destination.value());
    assertInstanceOf(McpStopLocation.IdOrName.class, request.intermediateStops().get(0));
    assertInstanceOf(McpStopLocation.Coordinate.class, request.intermediateStops().get(1));
  }

  @Test
  void parseRouteRequestKeepsExplicitStopIdPrefix() {
    var request = McpResource.parseRouteRequest(
      Map.of("origin", "@agency:A", "destination", "Central Station")
    );

    assertEquals("@agency:A", ((McpStopLocation.IdOrName) request.origin()).value());
    assertEquals("Central Station", ((McpStopLocation.IdOrName) request.destination()).value());
  }

  @Test
  void routingResultSerializesMapAndDisplayFields() throws Exception {
    var place = new McpRoutingResult.McpPlace("feed:stop", "Central", 60.17, 24.94, "5");
    var departure = "2026-09-10T08:15:00Z";
    var arrival = "2026-09-10T09:00:00Z";
    var leg = new McpRoutingResult.McpLeg(
      true,
      "RAIL",
      departure,
      arrival,
      2700,
      place,
      place,
      "feed:route",
      "Regional train",
      "Transit Agency",
      "encoded-polyline"
    );
    var itinerary = new McpRoutingResult.McpItinerary(
      departure,
      arrival,
      leg.durationSeconds(),
      0,
      List.of(leg)
    );
    var result = new McpRoutingResult(List.of(itinerary), List.of(), false);
    var json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(result));

    var nearestStop = new McpNearestStop("1:fi357373", "Central", 60.17, 24.94, 12.5);
    var nearestStopJson = new ObjectMapper().valueToTree(nearestStop);

    var serializedLeg = json.path("itineraries").get(0).path("legs").get(0);
    assertEquals("1:fi357373", nearestStopJson.path("id").asText());
    assertEquals(false, nearestStopJson.path("id").isObject());
    assertEquals("RAIL", serializedLeg.path("mode").asText());
    assertEquals("Transit Agency", serializedLeg.path("agencyName").asText());
    assertEquals("encoded-polyline", serializedLeg.path("shape").asText());
    assertFalse(serializedLeg.has("geometryId"));
    assertEquals("Central", serializedLeg.path("from").path("name").asText());
    assertEquals("5", serializedLeg.path("from").path("platformCode").asText());
    assertEquals(departure, serializedLeg.path("departure").asText());
    assertEquals(arrival, serializedLeg.path("arrival").asText());
  }
}
