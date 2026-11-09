package org.opentripplanner.apis.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.opentripplanner.core.model.id.FeedScopedId;

/** Locates and resolves the compact stops.json resource in the OTP base directory. */
public final class McpStopIndex {

  public static final String RESOURCE_URI = "otp://stops.json";
  public static final String FILE_NAME = "stops.json";
  public static final double MAX_COORDINATE_DISTANCE_METERS = 10_000;

  private static final double EARTH_RADIUS_METERS = 6_371_000;
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final List<Stop> stops;

  private McpStopIndex(List<Stop> stops) {
    this.stops = List.copyOf(stops);
  }

  static McpStopIndex empty() {
    return new McpStopIndex(List.of());
  }

  static boolean isStopIdReference(String value) {
    return value.trim().startsWith("@");
  }

  public static Optional<Path> findResource(File baseDirectory) {
    try (var resources = Files.list(baseDirectory.toPath())) {
      return resources
        .filter(path -> path.getFileName().toString().equals(FILE_NAME))
        .filter(Files::isRegularFile)
        .findFirst();
    } catch (IOException e) {
      throw new IllegalStateException(
        "Unable to find the stops.json resource in " + baseDirectory,
        e
      );
    }
  }

  public static McpStopIndex load(File baseDirectory) {
    var path = findResource(baseDirectory).orElseThrow(() ->
      new IllegalArgumentException(
        "The MCP stops.json resource is not available in the OTP base directory: " + baseDirectory
      )
    );
    try {
      return parse(Files.readString(path));
    } catch (IOException e) {
      throw new IllegalStateException("Unable to read the MCP stops.json resource: " + path, e);
    }
  }

  public static String readResource(File baseDirectory, String uri) {
    if (!RESOURCE_URI.equals(uri)) {
      throw new IllegalArgumentException("Unknown MCP resource URI: " + uri);
    }
    var path = findResource(baseDirectory).orElseThrow(() ->
      new IllegalArgumentException("The MCP stops.json resource is not available.")
    );
    try {
      return Files.readString(path);
    } catch (IOException e) {
      throw new IllegalStateException("Unable to read the MCP stops.json resource: " + path, e);
    }
  }

  static McpStopIndex parse(String json) {
    try {
      var root = OBJECT_MAPPER.readTree(json);
      if (root == null || !root.isArray()) {
        throw new IllegalArgumentException("The MCP stops.json resource must contain an array.");
      }
      var stops = new ArrayList<Stop>(root.size());
      for (int i = 0; i < root.size(); i++) {
        var tuple = root.get(i);
        if (
          !tuple.isArray() ||
          tuple.size() != 4 ||
          !tuple.get(0).isTextual() ||
          tuple.get(0).asText().isBlank() ||
          !tuple.get(1).isTextual() ||
          tuple.get(1).asText().isBlank() ||
          !(tuple.get(2).isNumber() || tuple.get(2).isTextual()) ||
          !(tuple.get(3).isNumber() || tuple.get(3).isTextual())
        ) {
          throw new IllegalArgumentException(
            "Invalid stops.json tuple at index " + i + "; expected [name, id, lat, lon]."
          );
        }
        try {
          var id = new FeedScopedId("1", tuple.get(1).asText());
          var latitude = Double.parseDouble(tuple.get(2).asText());
          var longitude = Double.parseDouble(tuple.get(3).asText());
          stops.add(new Stop(tuple.get(0).asText(), id, new McpCoordinate(latitude, longitude)));
        } catch (IllegalArgumentException e) {
          throw new IllegalArgumentException("Invalid stops.json tuple at index " + i + ".", e);
        }
      }
      return new McpStopIndex(stops);
    } catch (IOException e) {
      throw new IllegalArgumentException("The MCP stops.json resource is not valid JSON.", e);
    }
  }

  FeedScopedId resolve(McpStopLocation location) {
    if (location instanceof McpStopLocation.IdOrName idOrName) {
      var value = idOrName.value().trim();
      if (isStopIdReference(value)) {
        var stopId = value.substring(1).trim();
        try {
          var parsedId = FeedScopedId.parse(stopId);
          if (parsedId == null) {
            throw new IllegalArgumentException("Stop ID cannot be empty.");
          }
          return parsedId;
        } catch (IllegalArgumentException e) {
          throw new IllegalArgumentException(
            "Invalid stop ID: " + value + ". Prefix a feed-scoped stop ID with '@'.",
            e
          );
        }
      }
      return resolveName(value);
    }

    var coordinate = ((McpStopLocation.Coordinate) location).value();
    return stops
      .stream()
      .map(stop -> new StopDistance(stop.id(), distanceMeters(stop.coordinate(), coordinate)))
      .filter(stop -> stop.distanceMeters() <= MAX_COORDINATE_DISTANCE_METERS)
      .min((left, right) -> Double.compare(left.distanceMeters(), right.distanceMeters()))
      .map(StopDistance::id)
      .orElseThrow(() ->
        new IllegalArgumentException(
          "No stop in stops.json is within %s meters of the requested coordinates.".formatted(
            MAX_COORDINATE_DISTANCE_METERS
          )
        )
      );
  }

  private FeedScopedId resolveName(String value) {
    var normalizedQuery = normalizeName(value);
    if (normalizedQuery.isEmpty()) {
      throw unknownStopName(value);
    }
    if (normalizedQuery.length() < 4) {
      return stops
        .stream()
        .filter(stop -> normalizeName(stop.name()).equals(normalizedQuery))
        .map(Stop::id)
        .findFirst()
        .orElseThrow(() -> unknownStopName(value));
    }

    var prefix = normalizedQuery.substring(0, 4);
    var querySuffix = normalizedQuery.substring(4);
    return stops
      .stream()
      .filter(stop -> normalizeName(stop.name()).startsWith(prefix))
      .map(stop -> {
        var normalizedStopName = normalizeName(stop.name());
        return new StopNameMatch(
          stop.id(),
          levenshteinDistance(querySuffix, normalizedStopName.substring(4))
        );
      })
      .reduce((best, candidate) -> candidate.distance() < best.distance() ? candidate : best)
      .map(StopNameMatch::id)
      .orElseThrow(() -> unknownStopName(value));
  }

  private static IllegalArgumentException unknownStopName(String value) {
    return new IllegalArgumentException(
      "No stop name matches the normalized name search: " + value
    );
  }

  private static String normalizeName(String value) {
    return Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
      .replaceAll("\\p{M}+", "")
      .toLowerCase(Locale.ROOT)
      .replaceAll("[^\\x00-\\x7F]", "");
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

  private record Stop(String name, FeedScopedId id, McpCoordinate coordinate) {}

  private record StopNameMatch(FeedScopedId id, int distance) {}

  private record StopDistance(FeedScopedId id, double distanceMeters) {}
}
