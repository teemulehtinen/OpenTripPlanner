package org.opentripplanner.apis.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
      request
        .intermediateStops()
        .stream()
        .map(stopId -> stopId.toString())
        .toList()
    );
    assertEquals(Instant.parse("2026-09-10T08:15:00Z"), request.time());
    assertEquals(McpTimeDirection.DEPART_AT, request.timeDirection());
    assertEquals(Duration.ofMinutes(30), request.searchWindow());
    assertEquals(3, request.maxItineraries());
  }

  @Test
  void parseRouteRequestUsesDefaultsForOptionalArguments() {
    var beforeParsing = Instant.now();
    var request = McpResource.parseRouteRequest(
      Map.of("origin", "agency:A", "destination", "agency:B")
    );
    var afterParsing = Instant.now();

    assertEquals(McpTimeDirection.DEPART_AT, request.timeDirection());
    assertFalse(request.time().isBefore(beforeParsing));
    assertFalse(request.time().isAfter(afterParsing));
    assertEquals(Duration.ofHours(24), request.searchWindow());
    assertEquals(10, request.maxItineraries());
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
      "a82a8794-15d6-4f2a-b9b2-6db4337e88f7"
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
    assertEquals("a82a8794-15d6-4f2a-b9b2-6db4337e88f7", serializedLeg.path("geometryId").asText());
    assertFalse(serializedLeg.has("geometry"));
    assertEquals("Central", serializedLeg.path("from").path("name").asText());
    assertEquals("5", serializedLeg.path("from").path("platformCode").asText());
    assertEquals(departure, serializedLeg.path("departure").asText());
    assertEquals(arrival, serializedLeg.path("arrival").asText());
  }
}
