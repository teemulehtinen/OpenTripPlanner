package org.opentripplanner.apis.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentripplanner.core.model.id.FeedScopedId;

class McpStopIndexTest {

  @TempDir
  Path directory;

  @Test
  void resolvesClosestStopNameWithinMatchingAsciiLowercasePrefix() {
    var index = McpStopIndex.parse(
      """
      [
        ["Central", "first", 60.17, 24.94],
        ["Center", "second", 61.17, 24.94]
      ]
      """
    );

    assertEquals(
      new FeedScopedId("1", "first"),
      index.resolve(new McpStopLocation.IdOrName("centrall"))
    );
  }

  @Test
  void scopesRawStopIdsWithoutBreakingColonsInsideIds() {
    var index = McpStopIndex.parse(
      """
      [
        ["Bordeaux", "fr-83582StopPoint:OCECar TER-87581009", 44.82, -0.55]
      ]
      """
    );

    assertEquals(
      new FeedScopedId("1", "fr-83582StopPoint:OCECar TER-87581009"),
      index.resolve(new McpStopLocation.IdOrName("Bordeaux"))
    );
  }

  @Test
  void explicitlyPrefixedFeedScopedStopIdsDoNotRequireTheStopIndex() {
    var index = McpStopIndex.empty();

    assertEquals(
      FeedScopedId.parse("feed:central"),
      index.resolve(new McpStopLocation.IdOrName("@feed:central"))
    );
  }

  @Test
  void treatsUnprefixedFeedScopedIdsAsNamesAndRejectsInvalidPrefixedIds() {
    var index = McpStopIndex.empty();

    assertThrows(IllegalArgumentException.class, () ->
      index.resolve(new McpStopLocation.IdOrName("feed:central"))
    );
    assertThrows(IllegalArgumentException.class, () ->
      index.resolve(new McpStopLocation.IdOrName("@not-a-stop-id"))
    );
  }

  @Test
  void normalizesNamesToAsciiLowercaseBeforePrefixAndSuffixMatching() {
    var index = McpStopIndex.parse(
      """
      [
        ["München Hauptbahnhof", "munich", 48.14, 11.56],
        ["Munster Bahnhof", "munster", 51.98, 7.63]
      ]
      """
    );

    assertEquals(
      new FeedScopedId("1", "munich"),
      index.resolve(new McpStopLocation.IdOrName("MUNCHEN Hauptbahnhof"))
    );
  }

  @Test
  void namesShorterThanFourAsciiCharactersRequireAnExactNormalizedMatch() {
    var index = McpStopIndex.parse(
      """
      [
        ["NYC", "nyc", 40.71, -74.00],
        ["NY", "ny", 40.71, -74.00]
      ]
      """
    );

    assertEquals(new FeedScopedId("1", "nyc"), index.resolve(new McpStopLocation.IdOrName("nYc")));
    assertThrows(IllegalArgumentException.class, () ->
      index.resolve(new McpStopLocation.IdOrName("nx"))
    );
  }

  @Test
  void returnsNoMatchWhenNoStopSharesTheFourCharacterPrefix() {
    var index = McpStopIndex.parse("[[\"Central\", \"central\", 60.17, 24.94]]");

    assertThrows(IllegalArgumentException.class, () ->
      index.resolve(new McpStopLocation.IdOrName("Harbor"))
    );
  }

  @Test
  void resolvesCoordinateToClosestIndexedStopWithinTenKilometers() {
    var index = McpStopIndex.parse(
      """
      [
        ["Farther", "farther", 60.20, 24.94],
        ["Closest", "closest", 60.17, 24.94]
      ]
      """
    );

    assertEquals(
      new FeedScopedId("1", "closest"),
      index.resolve(new McpStopLocation.Coordinate(new McpCoordinate(60.171, 24.94)))
    );
  }

  @Test
  void parsesStringCoordinatesFromStopIndex() {
    var index = McpStopIndex.parse(
      """
      [["Closest", "closest", "60.171000", "24.940000"]]
      """
    );

    assertEquals(
      new FeedScopedId("1", "closest"),
      index.resolve(new McpStopLocation.Coordinate(new McpCoordinate(60.171, 24.94)))
    );
  }

  @Test
  void rejectsUnknownNamesAndCoordinatesOutsideSearchRadius() {
    var index = McpStopIndex.parse("[[\"Central\", \"central\", 60.17, 24.94]]");

    assertThrows(IllegalArgumentException.class, () ->
      index.resolve(new McpStopLocation.IdOrName("Unknown"))
    );
    assertThrows(IllegalArgumentException.class, () ->
      index.resolve(new McpStopLocation.Coordinate(new McpCoordinate(61, 25)))
    );
  }

  @Test
  void discoversAndReadsStopsJsonAsAnMcpResource() throws Exception {
    var content = "[[\"Central\",\"central\",60.17,24.94]]";
    Files.writeString(directory.resolve(McpStopIndex.FILE_NAME), content);

    assertEquals(1, McpStopIndex.findResource(directory.toFile()).stream().count());
    assertEquals(content, McpStopIndex.readResource(directory.toFile(), McpStopIndex.RESOURCE_URI));
  }

  @Test
  void rejectsMalformedStopIndexTuples() {
    assertThrows(IllegalArgumentException.class, () -> McpStopIndex.parse("[[\"Central\"]]"));
    assertThrows(IllegalArgumentException.class, () ->
      McpStopIndex.parse("[[\"Central\", \"central\", \"not-a-number\", \"24.94\"]]")
    );
  }
}
