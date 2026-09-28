package org.opentripplanner.apis.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
  void routingResultSerializesMapAndDisplayFields() throws Exception {
    var place = new McpRoutingResult.McpPlace("feed:stop", "Central", 60.17, 24.94, "5");
    var leg = new McpRoutingResult.McpLeg(
      true,
      "RAIL",
      Instant.parse("2026-09-10T08:15:00Z"),
      Instant.parse("2026-09-10T09:00:00Z"),
      2700,
      place,
      place,
      "feed:route",
      "Regional train",
      "Transit Agency",
      "encoded-polyline"
    );
    var itinerary = new McpRoutingResult.McpItinerary(
      leg.departure(),
      leg.arrival(),
      leg.durationSeconds(),
      0,
      List.of(leg)
    );
    var result = new McpRoutingResult(List.of(itinerary), List.of(), false);
    var json = new ObjectMapper()
      .registerModule(new JavaTimeModule())
      .readTree(new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsString(result));

    var serializedLeg = json.path("itineraries").get(0).path("legs").get(0);
    assertEquals("RAIL", serializedLeg.path("mode").asText());
    assertEquals("Transit Agency", serializedLeg.path("agencyName").asText());
    assertEquals("encoded-polyline", serializedLeg.path("geometry").asText());
    assertEquals("Central", serializedLeg.path("from").path("name").asText());
    assertEquals("5", serializedLeg.path("from").path("platformCode").asText());
  }
}
