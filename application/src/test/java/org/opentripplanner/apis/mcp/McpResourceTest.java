package org.opentripplanner.apis.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.opentripplanner.standalone.api.OtpServerRequestContext;
import org.opentripplanner.transit.model.basic.TransitMode;

class McpResourceTest {

  @TempDir
  Path directory;

  @Test
  void routeToolSchemaAdvertisesModesAndExplicitStopLocationChoices() {
    var serverContext = Mockito.mock(OtpServerRequestContext.class);
    when(serverContext.baseDirectory()).thenReturn(new File("."));
    var resource = new McpResource(serverContext);

    var response = resource.handle(
      Map.of("jsonrpc", "2.0", "id", 1, "method", "tools/list"),
      null,
      null,
      "application/json, text/event-stream"
    );

    var body = (Map<?, ?>) response.getEntity();
    var result = (Map<?, ?>) body.get("result");
    var tools = (List<?>) result.get("tools");
    var routeTool = (Map<?, ?>) tools
      .stream()
      .map(value -> (Map<?, ?>) value)
      .filter(tool -> "route".equals(tool.get("name")))
      .findFirst()
      .orElseThrow();
    var inputSchema = (Map<?, ?>) routeTool.get("inputSchema");
    var properties = (Map<?, ?>) inputSchema.get("properties");
    var transitMode = (Map<?, ?>) properties.get("transit_mode");
    var items = (Map<?, ?>) transitMode.get("items");

    assertEquals(
      List.of(
        "RAIL",
        "COACH",
        "SUBWAY",
        "BUS",
        "TRAM",
        "FERRY",
        "AIRPLANE",
        "CABLE_CAR",
        "GONDOLA",
        "FUNICULAR",
        "TROLLEYBUS",
        "MONORAIL",
        "CARPOOL",
        "TAXI",
        "SNOW_AND_ICE"
      ),
      items.get("enum")
    );
    assertFalse(((List<?>) inputSchema.get("required")).contains("transit_mode"));

    var origin = (Map<?, ?>) properties.get("origin");
    var locationProperties = (Map<?, ?>) origin.get("properties");
    assertEquals("object", origin.get("type"));
    assertEquals("string", ((Map<?, ?>) locationProperties.get("stopId")).get("type"));
    assertEquals("string", ((Map<?, ?>) locationProperties.get("name")).get("type"));
    assertEquals("number", ((Map<?, ?>) locationProperties.get("latitude")).get("type"));
    assertEquals("number", ((Map<?, ?>) locationProperties.get("longitude")).get("type"));
    assertEquals(
      List.of(
        Map.of("required", List.of("stopId")),
        Map.of("required", List.of("name")),
        Map.of("required", List.of("latitude", "longitude"))
      ),
      origin.get("oneOf")
    );

    var intermediateStops = (Map<?, ?>) properties.get("intermediateStops");
    var intermediateLocation = (Map<?, ?>) intermediateStops.get("items");
    assertEquals("object", intermediateLocation.get("type"));
    assertEquals(
      "string",
      ((Map<?, ?>) ((Map<?, ?>) intermediateLocation.get("properties")).get("stopId")).get("type")
    );
    assertEquals(
      "string",
      ((Map<?, ?>) ((Map<?, ?>) intermediateLocation.get("properties")).get("name")).get("type")
    );
  }

  @Test
  void advertisesPlaceSearchAndNoBulkIndexResource() {
    var serverContext = Mockito.mock(OtpServerRequestContext.class);
    when(serverContext.baseDirectory()).thenReturn(directory.toFile());
    var resource = new McpResource(serverContext);

    var toolsResponse = resource.handle(
      Map.of("jsonrpc", "2.0", "id", 1, "method", "tools/list"),
      null,
      null,
      "application/json, text/event-stream"
    );
    var toolsBody = (Map<?, ?>) toolsResponse.getEntity();
    var toolsResult = (Map<?, ?>) toolsBody.get("result");
    var tools = (List<?>) toolsResult.get("tools");
    assertTrue(
      tools
        .stream()
        .map(value -> (Map<?, ?>) value)
        .anyMatch(tool -> "search_places".equals(tool.get("name")))
    );

    var resourcesResponse = resource.handle(
      Map.of("jsonrpc", "2.0", "id", 2, "method", "resources/list"),
      null,
      null,
      "application/json, text/event-stream"
    );
    var resourcesBody = (Map<?, ?>) resourcesResponse.getEntity();
    var resourcesResult = (Map<?, ?>) resourcesBody.get("result");
    assertEquals(List.of(), ((Map<?, ?>) resourcesResult).get("resources"));
  }

  @Test
  void searchPlacesToolReturnsOnlyTheRequestedSuggestions() throws Exception {
    var indexDirectory = directory.resolve(McpPlaceIndex.DIRECTORY_NAME);
    Files.createDirectories(indexDirectory);
    Files.writeString(
      indexDirectory.resolve(McpPlaceIndex.FILE_NAME),
      """
      {
        "version": 1,
        "places": [
          {"placeId":"1","stopId":"helsinki","countryCode":"FI","countryName":"Finland","population":1000,"names":["Helsinki"]},
          {"placeId":"2","stopId":"helsingborg","countryCode":"SE","countryName":"Sweden","population":500,"names":["Helsingborg"]}
        ],
        "stops": []
      }
      """
    );
    var serverContext = Mockito.mock(OtpServerRequestContext.class);
    when(serverContext.baseDirectory()).thenReturn(directory.toFile());
    var resource = new McpResource(serverContext);

    var response = resource.handle(
      Map.of(
        "jsonrpc",
        "2.0",
        "id",
        3,
        "method",
        "tools/call",
        "params",
        Map.of("name", "search_places", "arguments", Map.of("query", "hel", "limit", 1))
      ),
      null,
      null,
      "application/json, text/event-stream"
    );
    var body = (Map<?, ?>) response.getEntity();
    var result = (Map<?, ?>) body.get("result");
    var structuredContent = (Map<?, ?>) result.get("structuredContent");
    var places = (List<?>) structuredContent.get("places");
    var place = (McpPlaceSuggestion) places.get(0);

    assertEquals(1, places.size());
    assertEquals("Helsinki", place.name());
    assertEquals("1:helsinki", place.stopId());
  }

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
        true,
        "transit_mode",
        List.of("RAIL", "FERRY")
      )
    );

    assertNotNull(request);
    assertEquals("agency:A", ((McpStopLocation.StopId) request.origin()).value());
    assertEquals("agency:B", ((McpStopLocation.StopId) request.destination()).value());
    assertEquals(
      List.of("agency:C", "agency:D"),
      request
        .intermediateStops()
        .stream()
        .map(stop -> ((McpStopLocation.StopId) stop).value())
        .toList()
    );
    assertEquals(Instant.parse("2026-09-10T08:15:00Z"), request.time());
    assertEquals(McpTimeDirection.DEPART_AT, request.timeDirection());
    assertEquals(Duration.ofMinutes(30), request.searchWindow());
    assertEquals(3, request.maxItineraries());
    assertEquals(List.of(TransitMode.RAIL, TransitMode.FERRY), request.transitModes());
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
    assertEquals(List.of(), request.transitModes());
    assertEquals(false, request.includeShapeData());
  }

  @Test
  void parseRouteRequestRejectsUnknownTransitMode() {
    org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
      McpResource.parseRouteRequest(
        Map.of(
          "origin",
          "@agency:A",
          "destination",
          "@agency:B",
          "transit_mode",
          List.of("SPACE_SHIP")
        )
      )
    );
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
  void parseRouteRequestAcceptsStopIdsNamesAndCoordinateObjects() {
    var request = McpResource.parseRouteRequest(
      Map.of(
        "origin",
        Map.of("name", "Central Station"),
        "destination",
        Map.of("latitude", 60.17, "longitude", 24.94),
        "intermediateStops",
        List.of(Map.of("stopId", "agency:harbor"), Map.of("latitude", 60.16, "longitude", 24.95))
      )
    );

    assertEquals("Central Station", ((McpStopLocation.Name) request.origin()).value());
    var destination = assertInstanceOf(McpStopLocation.Coordinate.class, request.destination());
    assertEquals(new McpCoordinate(60.17, 24.94), destination.value());
    assertEquals(
      "agency:harbor",
      ((McpStopLocation.StopId) request.intermediateStops().get(0)).value()
    );
    assertInstanceOf(McpStopLocation.Coordinate.class, request.intermediateStops().get(1));
  }

  @Test
  void parseRouteRequestRejectsMissingAndAmbiguousStopLocationChoices() {
    assertThrows(IllegalArgumentException.class, () ->
      McpResource.parseRouteRequest(
        Map.of(
          "origin",
          Map.of("stopId", "agency:central", "name", "Central Station"),
          "destination",
          "Harbor"
        )
      )
    );
    assertThrows(IllegalArgumentException.class, () ->
      McpResource.parseRouteRequest(
        Map.of("origin", Map.of("latitude", 60.17), "destination", Map.of("name", "Harbor"))
      )
    );
    assertThrows(IllegalArgumentException.class, () ->
      McpResource.parseRouteRequest(
        Map.of(
          "origin",
          Map.of("name", "Central Station", "latitude", 60.17, "longitude", 24.94),
          "destination",
          Map.of("name", "Harbor")
        )
      )
    );
  }

  @Test
  void parseRouteRequestKeepsLegacyStopLocations() {
    var request = McpResource.parseRouteRequest(
      Map.of("origin", "@agency:A", "destination", Map.of("stopIdOrName", "Central Station"))
    );

    assertEquals("agency:A", ((McpStopLocation.StopId) request.origin()).value());
    assertEquals("Central Station", ((McpStopLocation.Name) request.destination()).value());
  }

  @Test
  void parseRouteRequestKeepsExplicitStopIdPrefix() {
    var request = McpResource.parseRouteRequest(
      Map.of("origin", "@agency:A", "destination", "Central Station")
    );

    assertEquals("agency:A", ((McpStopLocation.StopId) request.origin()).value());
    assertEquals("Central Station", ((McpStopLocation.Name) request.destination()).value());
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
      1234.5,
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
    assertEquals(1234.5, serializedLeg.path("distanceMeters").asDouble());
    assertFalse(serializedLeg.has("geometryId"));
    assertEquals("Central", serializedLeg.path("from").path("name").asText());
    assertEquals("5", serializedLeg.path("from").path("platformCode").asText());
    assertEquals(departure, serializedLeg.path("departure").asText());
    assertEquals(arrival, serializedLeg.path("arrival").asText());
  }
}
