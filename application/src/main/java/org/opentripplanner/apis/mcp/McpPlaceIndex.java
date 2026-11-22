package org.opentripplanner.apis.mcp;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.opentripplanner.core.model.id.FeedScopedId;

/** Loads place names and routable-stop coordinates from the OTP base directory. */
public final class McpPlaceIndex {

  public static final String DIRECTORY_NAME = "places";
  public static final String FILE_NAME = "index.json";
  public static final double MAX_COORDINATE_DISTANCE_METERS = 10_000;
  public static final int MAX_SEARCH_RESULTS = 20;

  private static final double EARTH_RADIUS_METERS = 6_371_000;
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final ConcurrentMap<Path, McpPlaceIndex> CACHE = new ConcurrentHashMap<>();

  private final List<PlaceName> placeNames;
  private final List<Stop> stops;

  private McpPlaceIndex(List<Place> places, List<Stop> stops) {
    this.placeNames = places
      .stream()
      .flatMap(place ->
        place
          .names()
          .stream()
          .map(name -> new PlaceName(name, normalizeName(name), place))
      )
      .sorted(Comparator.comparing(PlaceName::normalizedName).thenComparing(PlaceName::name))
      .toList();
    this.stops = List.copyOf(stops);
  }

  static McpPlaceIndex empty() {
    return new McpPlaceIndex(List.of(), List.of());
  }

  static boolean isStopIdReference(String value) {
    return value.trim().startsWith("@");
  }

  public static McpPlaceIndex load(File baseDirectory) {
    var key = baseDirectory.toPath().toAbsolutePath().normalize();
    return CACHE.computeIfAbsent(key, McpPlaceIndex::readIndex);
  }

  static McpPlaceIndex parse(String json) {
    try (var parser = OBJECT_MAPPER.getFactory().createParser(json)) {
      return parse(parser);
    } catch (IOException e) {
      throw new IllegalArgumentException("The places index is not valid JSON.", e);
    }
  }

  List<McpPlaceSuggestion> search(String query, int limit) {
    if (query == null || query.trim().length() < 2) {
      throw new IllegalArgumentException(
        "Place search query must contain at least two characters."
      );
    }
    if (limit < 1 || limit > MAX_SEARCH_RESULTS) {
      throw new IllegalArgumentException(
        "Place search limit must be between 1 and " + MAX_SEARCH_RESULTS + "."
      );
    }

    var normalizedQuery = normalizeName(query);
    if (normalizedQuery.length() < 2) {
      return List.of();
    }
    var order = Comparator.comparing((PlaceName placeName) ->
      !placeName.normalizedName().startsWith(normalizedQuery)
    )
      .thenComparing(
        Comparator.comparingLong((PlaceName placeName) -> placeName.place().population()).reversed()
      )
      .thenComparing(placeName -> placeName.place().countryName())
      .thenComparing(PlaceName::normalizedName);
    var matches = new PriorityQueue<PlaceName>(limit, order.reversed());
    for (var placeName : placeNames) {
      if (!placeName.normalizedName().contains(normalizedQuery)) {
        continue;
      }
      if (matches.size() < limit) {
        matches.add(placeName);
      } else if (order.compare(placeName, matches.peek()) < 0) {
        matches.poll();
        matches.add(placeName);
      }
    }
    return matches
      .stream()
      .sorted(order)
      .map(placeName ->
        new McpPlaceSuggestion(
          placeName.name(),
          placeName.place().placeId(),
          placeName.place().stopId().toString(),
          placeName.place().countryCode(),
          placeName.place().countryName(),
          placeName.place().population()
        )
      )
      .toList();
  }

  FeedScopedId resolve(McpStopLocation location) {
    if (location instanceof McpStopLocation.StopId stopId) {
      return resolveStopId(stopId.value());
    }
    if (location instanceof McpStopLocation.Name name) {
      return resolveName(name.value());
    }

    var coordinate = ((McpStopLocation.Coordinate) location).value();
    return stops
      .stream()
      .map(stop -> new StopDistance(stop.id(), distanceMeters(stop.coordinate(), coordinate)))
      .filter(stop -> stop.distanceMeters() <= MAX_COORDINATE_DISTANCE_METERS)
      .min(Comparator.comparingDouble(StopDistance::distanceMeters))
      .map(StopDistance::id)
      .orElseThrow(() ->
        new IllegalArgumentException(
          "No stop in places/index.json is within %s meters of the requested coordinates.".formatted(
            MAX_COORDINATE_DISTANCE_METERS
          )
        )
      );
  }

  private static McpPlaceIndex readIndex(Path baseDirectory) {
    var path = baseDirectory.resolve(DIRECTORY_NAME).resolve(FILE_NAME);
    if (!Files.isRegularFile(path)) {
      throw new IllegalArgumentException(
        "The MCP places index is not available in the OTP base directory: " + path
      );
    }
    try (
      var input = Files.newInputStream(path);
      var parser = OBJECT_MAPPER.getFactory().createParser(input)
    ) {
      return parse(parser);
    } catch (IOException e) {
      throw new IllegalStateException("Unable to read the MCP places index: " + path, e);
    }
  }

  private static McpPlaceIndex parse(JsonParser parser) throws IOException {
    if (parser.nextToken() != JsonToken.START_OBJECT) {
      throw new IllegalArgumentException("The places index must contain a JSON object.");
    }
    var places = new ArrayList<Place>();
    var stops = new ArrayList<Stop>();
    Integer version = null;
    boolean foundPlaces = false;
    boolean foundStops = false;

    while (parser.nextToken() != JsonToken.END_OBJECT) {
      if (parser.currentToken() != JsonToken.FIELD_NAME) {
        throw new IllegalArgumentException("Invalid places index object.");
      }
      var field = parser.currentName();
      var token = parser.nextToken();
      switch (field) {
        case "version" -> {
          if (token != JsonToken.VALUE_NUMBER_INT) {
            throw new IllegalArgumentException("The places index version must be an integer.");
          }
          version = parser.getIntValue();
        }
        case "places" -> {
          if (token != JsonToken.START_ARRAY) {
            throw new IllegalArgumentException("The places index places field must be an array.");
          }
          foundPlaces = true;
          int index = 0;
          while (parser.nextToken() != JsonToken.END_ARRAY) {
            places.add(parsePlace(parser, index++));
          }
        }
        case "stops" -> {
          if (token != JsonToken.START_ARRAY) {
            throw new IllegalArgumentException("The places index stops field must be an array.");
          }
          foundStops = true;
          int index = 0;
          while (parser.nextToken() != JsonToken.END_ARRAY) {
            stops.add(parseStop(parser, index++));
          }
        }
        default -> parser.skipChildren();
      }
    }

    if (version == null || version != 1 || !foundPlaces || !foundStops) {
      throw new IllegalArgumentException(
        "The places index must contain version 1, places, and stops."
      );
    }
    return new McpPlaceIndex(places, stops);
  }

  private static Place parsePlace(JsonParser parser, int index) throws IOException {
    if (parser.currentToken() != JsonToken.START_OBJECT) {
      throw invalidPlace(index);
    }
    String placeId = null;
    String stopId = null;
    String countryCode = null;
    String countryName = null;
    Long population = null;
    List<String> names = null;
    while (parser.nextToken() != JsonToken.END_OBJECT) {
      if (parser.currentToken() != JsonToken.FIELD_NAME) {
        throw invalidPlace(index);
      }
      var field = parser.currentName();
      var token = parser.nextToken();
      switch (field) {
        case "placeId" -> placeId = readText(parser, token);
        case "stopId" -> stopId = readText(parser, token);
        case "countryCode" -> countryCode = readText(parser, token);
        case "countryName" -> countryName = readText(parser, token);
        case "names" -> names = readNames(parser, token, index);
        case "population" -> {
          if (token != JsonToken.VALUE_NUMBER_INT || parser.getLongValue() < 0) {
            throw invalidPlace(index);
          }
          population = parser.getLongValue();
        }
        default -> parser.skipChildren();
      }
    }
    if (
      isBlank(placeId) ||
      isBlank(stopId) ||
      isBlank(countryCode) ||
      isBlank(countryName) ||
      population == null ||
      names == null ||
      names.isEmpty()
    ) {
      throw invalidPlace(index);
    }
    try {
      return new Place(
        placeId,
        new FeedScopedId("1", stopId),
        countryCode,
        countryName,
        population,
        List.copyOf(names)
      );
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Invalid place index row at index " + index + ".", e);
    }
  }

  private static List<String> readNames(JsonParser parser, JsonToken token, int index)
    throws IOException {
    if (token != JsonToken.START_ARRAY) {
      throw invalidPlace(index);
    }
    var names = new ArrayList<String>();
    while (parser.nextToken() != JsonToken.END_ARRAY) {
      if (parser.currentToken() != JsonToken.VALUE_STRING || isBlank(parser.getText())) {
        throw invalidPlace(index);
      }
      names.add(parser.getText());
    }
    return names;
  }

  private static Stop parseStop(JsonParser parser, int index) throws IOException {
    if (parser.currentToken() != JsonToken.START_ARRAY) {
      throw invalidStop(index);
    }
    if (parser.nextToken() != JsonToken.VALUE_STRING) {
      throw invalidStop(index);
    }
    var stopId = parser.getText();
    var latitudeToken = parser.nextToken();
    if (
      isBlank(stopId) ||
      (latitudeToken != JsonToken.VALUE_NUMBER_FLOAT && latitudeToken != JsonToken.VALUE_NUMBER_INT)
    ) {
      throw invalidStop(index);
    }
    var latitude = parser.getDoubleValue();
    var longitudeToken = parser.nextToken();
    if (
      longitudeToken != JsonToken.VALUE_NUMBER_FLOAT && longitudeToken != JsonToken.VALUE_NUMBER_INT
    ) {
      throw invalidStop(index);
    }
    var longitude = parser.getDoubleValue();
    if (
      parser.nextToken() != JsonToken.END_ARRAY ||
      !Double.isFinite(latitude) ||
      latitude < -90 ||
      latitude > 90 ||
      !Double.isFinite(longitude) ||
      longitude < -180 ||
      longitude > 180
    ) {
      throw invalidStop(index);
    }
    try {
      return new Stop(new FeedScopedId("1", stopId), new McpCoordinate(latitude, longitude));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Invalid stop coordinate at index " + index + ".", e);
    }
  }

  private static String readText(JsonParser parser, JsonToken token) throws IOException {
    if (token != JsonToken.VALUE_STRING) {
      return null;
    }
    return parser.getText();
  }

  private FeedScopedId resolveStopId(String value) {
    var stopId = value.trim();
    if (isStopIdReference(stopId)) {
      stopId = stopId.substring(1).trim();
    }
    try {
      var parsedId = FeedScopedId.parse(stopId);
      if (parsedId == null) {
        throw new IllegalArgumentException("Stop ID cannot be empty.");
      }
      return parsedId;
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Invalid feed-scoped stop ID: " + value, e);
    }
  }

  private FeedScopedId resolveName(String value) {
    var normalizedQuery = normalizeName(value);
    if (normalizedQuery.isEmpty()) {
      throw unknownPlaceName(value);
    }
    var exactStart = lowerBound(normalizedQuery);
    PlaceName exactMatch = null;
    for (int i = exactStart; i < placeNames.size(); i++) {
      var placeName = placeNames.get(i);
      if (!placeName.normalizedName().equals(normalizedQuery)) {
        break;
      }
      if (exactMatch == null || placeName.place().population() > exactMatch.place().population()) {
        exactMatch = placeName;
      }
    }
    if (exactMatch != null) {
      return exactMatch.place().stopId();
    }
    if (normalizedQuery.length() < 4) {
      throw unknownPlaceName(value);
    }

    var prefix = normalizedQuery.substring(0, 4);
    var querySuffix = normalizedQuery.substring(4);
    var bestMatch = placeNames
      .subList(lowerBound(prefix), placeNames.size())
      .stream()
      .takeWhile(placeName -> placeName.normalizedName().startsWith(prefix))
      .map(placeName ->
        new PlaceNameMatch(
          placeName.place().stopId(),
          levenshteinDistance(querySuffix, placeName.normalizedName().substring(4)),
          placeName.place().population()
        )
      )
      .min(
        Comparator.comparingInt(PlaceNameMatch::distance).thenComparing(
          Comparator.comparingLong(PlaceNameMatch::population).reversed()
        )
      )
      .orElse(null);
    if (bestMatch == null) {
      throw unknownPlaceName(value);
    }
    return bestMatch.id();
  }

  private int lowerBound(String normalizedName) {
    var low = 0;
    var high = placeNames.size();
    while (low < high) {
      var middle = (low + high) >>> 1;
      if (placeNames.get(middle).normalizedName().compareTo(normalizedName) < 0) {
        low = middle + 1;
      } else {
        high = middle;
      }
    }
    return low;
  }

  private static IllegalArgumentException unknownPlaceName(String value) {
    return new IllegalArgumentException("No place name matches the normalized search: " + value);
  }

  private static String normalizeName(String value) {
    return Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
      .replaceAll("\\p{M}+", "")
      .toLowerCase(Locale.ROOT);
  }

  private static int levenshteinDistance(String left, String right) {
    var previous = new int[right.length() + 1];
    for (int j = 0; j <= right.length(); j++) {
      previous[j] = j;
    }

    for (int i = 1; i <= left.length(); i++) {
      var current = new int[right.length() + 1];
      current[0] = i;
      for (int j = 1; j <= right.length(); j++) {
        var substitutionCost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
        current[j] = Math.min(
          Math.min(current[j - 1] + 1, previous[j] + 1),
          previous[j - 1] + substitutionCost
        );
      }
      previous = current;
    }
    return previous[right.length()];
  }

  private static double distanceMeters(McpCoordinate from, McpCoordinate to) {
    var latitudeDelta = Math.toRadians(to.latitude() - from.latitude());
    var longitudeDelta = Math.toRadians(to.longitude() - from.longitude());
    var fromLatitude = Math.toRadians(from.latitude());
    var toLatitude = Math.toRadians(to.latitude());
    var a =
      Math.pow(Math.sin(latitudeDelta / 2), 2) +
      Math.cos(fromLatitude) * Math.cos(toLatitude) * Math.pow(Math.sin(longitudeDelta / 2), 2);
    return EARTH_RADIUS_METERS * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private static IllegalArgumentException invalidPlace(int index) {
    return new IllegalArgumentException("Invalid place index row at index " + index + ".");
  }

  private static IllegalArgumentException invalidStop(int index) {
    return new IllegalArgumentException("Invalid stop coordinate at index " + index + ".");
  }

  private record Place(
    String placeId,
    FeedScopedId stopId,
    String countryCode,
    String countryName,
    long population,
    List<String> names
  ) {}

  private record PlaceName(String name, String normalizedName, Place place) {}

  private record Stop(FeedScopedId id, McpCoordinate coordinate) {}

  private record PlaceNameMatch(FeedScopedId id, int distance, long population) {}

  private record StopDistance(FeedScopedId id, double distanceMeters) {}
}
