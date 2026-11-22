package org.opentripplanner.apis.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentripplanner.core.model.id.FeedScopedId;

class McpPlaceIndexTest {

  @TempDir
  Path directory;

  @Test
  void resolvesExactAndAlternatePlaceNamesToFeedScopedStops() {
    var index = McpPlaceIndex.parse(
      """
      {
        "version": 1,
        "places": [
          {"placeId":"1","stopId":"munich","countryCode":"DE","countryName":"Germany","population":1000,"names":["München","Munich"]}
        ],
        "stops": []
      }
      """
    );

    assertEquals(
      new FeedScopedId("1", "munich"),
      index.resolve(new McpStopLocation.Name("MUNCHEN"))
    );
    assertEquals(
      new FeedScopedId("1", "munich"),
      index.resolve(new McpStopLocation.Name("Munich"))
    );
  }

  @Test
  void resolvesDuplicatePlaceNamesToTheMostPopulousEntry() {
    var index = McpPlaceIndex.parse(
      """
      {
        "version": 1,
        "places": [
          {"placeId":"small","stopId":"small-stop","countryCode":"US","countryName":"United States","population":20,"names":["Springfield"]},
          {"placeId":"large","stopId":"large-stop","countryCode":"US","countryName":"United States","population":200,"names":["Springfield"]}
        ],
        "stops": []
      }
      """
    );

    assertEquals(
      new FeedScopedId("1", "large-stop"),
      index.resolve(new McpStopLocation.Name("Springfield"))
    );
    assertEquals(2, index.search("Spring", 10).size());
  }

  @Test
  void searchesPrefixesBeforeSubstringMatchesAndHonorsTheResultLimit() {
    var index = McpPlaceIndex.parse(
      """
      {
        "version": 1,
        "places": [
          {"placeId":"1","stopId":"harbor","countryCode":"FI","countryName":"Finland","population":100,"names":["North Harbor"]},
          {"placeId":"2","stopId":"city","countryCode":"SE","countryName":"Sweden","population":1000,"names":["Harbor City"]},
          {"placeId":"3","stopId":"town","countryCode":"NO","countryName":"Norway","population":500,"names":["Harbor Town"]}
        ],
        "stops": []
      }
      """
    );

    var results = index.search("harbor", 2);

    assertEquals(2, results.size());
    assertEquals("Harbor City", results.get(0).name());
    assertEquals("SE", results.get(0).countryCode());
    assertEquals("1:city", results.get(0).stopId());
    assertEquals("Harbor Town", results.get(1).name());
  }

  @Test
  void resolvesCoordinateLocationsFromTheStopCoordinateSection() {
    var index = McpPlaceIndex.parse(
      """
      {
        "version": 1,
        "places": [],
        "stops": [["far",60.20,24.94],["closest",60.17,24.94]]
      }
      """
    );

    assertEquals(
      new FeedScopedId("1", "closest"),
      index.resolve(new McpStopLocation.Coordinate(new McpCoordinate(60.171, 24.94)))
    );
  }

  @Test
  void cachesTheValidatedIndexForTheOtpBaseDirectory() throws Exception {
    var path = directory.resolve(McpPlaceIndex.DIRECTORY_NAME);
    Files.createDirectories(path);
    Files.writeString(
      path.resolve(McpPlaceIndex.FILE_NAME),
      """
      {"version":1,"places":[],"stops":[]}
      """
    );

    var first = McpPlaceIndex.load(directory.toFile());
    Files.delete(path.resolve(McpPlaceIndex.FILE_NAME));
    var second = McpPlaceIndex.load(directory.toFile());

    assertSame(first, second);
  }

  @Test
  void rejectsInvalidEntriesAndSearchArguments() {
    assertThrows(IllegalArgumentException.class, () ->
      McpPlaceIndex.parse("{\"version\":1,\"places\":[{}],\"stops\":[]}")
    );
    assertThrows(IllegalArgumentException.class, () ->
      McpPlaceIndex.parse("{\"version\":2,\"places\":[],\"stops\":[]}")
    );
    var index = McpPlaceIndex.parse("{\"version\":1,\"places\":[],\"stops\":[]}");
    assertThrows(IllegalArgumentException.class, () -> index.search("a", 10));
    assertThrows(IllegalArgumentException.class, () -> index.search("test", 21));
  }
}
