package org.opentripplanner.apis.mcp;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.opentripplanner.api.model.geometry.EncodedPolyline;
import org.opentripplanner.core.model.i18n.I18NString;
import org.opentripplanner.model.plan.Itinerary;
import org.opentripplanner.model.plan.Leg;
import org.opentripplanner.model.plan.Place;
import org.opentripplanner.model.plan.TransitLeg;
import org.opentripplanner.routing.api.response.RoutingError;
import org.opentripplanner.routing.api.response.RoutingResponse;
import org.opentripplanner.street.geometry.SphericalDistanceLibrary;
import org.opentripplanner.transit.model.organization.Agency;

/** Maps OTP's routing response to the bounded MCP route result. */
public final class McpRoutingResultMapper {

  private static final Duration MIN_TRANSFER_WALK_DURATION = Duration.ofMinutes(5);

  private McpRoutingResultMapper() {}

  public static McpRoutingResult map(RoutingResponse response, boolean includeShapeData) {
    var itineraries = response.getTripPlan() == null
      ? List.<McpRoutingResult.McpItinerary>of()
      : response
          .getTripPlan()
          .itineraries.stream()
          .map(itinerary -> mapItinerary(itinerary, includeShapeData))
          .toList();

    return new McpRoutingResult(
      itineraries,
      response.getRoutingErrors().stream().map(McpRoutingResultMapper::mapError).toList(),
      response.getNextPageCursor() != null
    );
  }

  private static McpRoutingResult.McpItinerary mapItinerary(
    Itinerary itinerary,
    boolean includeShapeData
  ) {
    return new McpRoutingResult.McpItinerary(
      itinerary.startTimeAsInstant().toString(),
      itinerary.endTimeAsInstant().toString(),
      itinerary.totalDuration().toSeconds(),
      itinerary.numberOfTransfers(),
      mapLegs(itinerary.legs(), includeShapeData)
    );
  }

  static List<McpRoutingResult.McpLeg> mapLegs(List<Leg> legs, boolean includeShapeData) {
    var mappedLegs = java.util.stream.IntStream.range(0, legs.size())
      .filter(index -> includeLeg(legs, index))
      .mapToObj(index -> mapLeg(legs.get(index), includeShapeData))
      .toList();
    return java.util.stream.IntStream.range(0, mappedLegs.size())
      .mapToObj(index -> withTransferTimeZones(mappedLegs, index))
      .toList();
  }

  private static McpRoutingResult.McpLeg withTransferTimeZones(
    List<McpRoutingResult.McpLeg> legs,
    int index
  ) {
    var leg = legs.get(index);
    if (!"TRANSFER".equals(leg.mode())) {
      return leg;
    }

    var from = leg.from();
    if (from.timeZone() == null) {
      var previousStop = adjacentTransitPlace(legs, index, -1);
      if (
        previousStop != null &&
        previousStop.timeZone() != null &&
        samePlaceName(from.name(), previousStop.name())
      ) {
        from = withTimeZone(from, previousStop.timeZone());
      }
    }

    var to = leg.to();
    if (to.timeZone() == null) {
      var nextStop = adjacentTransitPlace(legs, index, 1);
      if (
        nextStop != null && nextStop.timeZone() != null && samePlaceName(to.name(), nextStop.name())
      ) {
        to = withTimeZone(to, nextStop.timeZone());
      }
    }

    if (from == leg.from() && to == leg.to()) {
      return leg;
    }
    return new McpRoutingResult.McpLeg(
      leg.transit(),
      leg.mode(),
      leg.departure(),
      leg.arrival(),
      leg.durationSeconds(),
      leg.distanceMeters(),
      from,
      to,
      leg.routeId(),
      leg.tripId(),
      leg.routeName(),
      leg.agencyName(),
      leg.shape()
    );
  }

  private static McpRoutingResult.McpPlace adjacentTransitPlace(
    List<McpRoutingResult.McpLeg> legs,
    int index,
    int direction
  ) {
    for (
      int current = index + direction;
      current >= 0 && current < legs.size();
      current += direction
    ) {
      var leg = legs.get(current);
      if (leg.transit()) {
        return direction < 0 ? leg.to() : leg.from();
      }
    }
    return null;
  }

  private static boolean samePlaceName(String first, String second) {
    if (first == null || second == null) {
      return false;
    }

    var normalizedFirst = normalizePlaceName(first);
    return !normalizedFirst.isEmpty() && normalizedFirst.equals(normalizePlaceName(second));
  }

  private static String normalizePlaceName(String name) {
    return name.strip().toLowerCase(Locale.ROOT).replaceAll("[\\p{Punct}\\s]+", " ");
  }

  private static McpRoutingResult.McpPlace withTimeZone(
    McpRoutingResult.McpPlace place,
    String timeZone
  ) {
    return new McpRoutingResult.McpPlace(
      place.stopId(),
      place.name(),
      place.latitude(),
      place.longitude(),
      place.platformCode(),
      timeZone
    );
  }

  private static boolean includeLeg(List<Leg> legs, int index) {
    var leg = legs.get(index);
    if (!leg.isWalkingLeg()) {
      return true;
    }

    return (
      leg.duration().compareTo(MIN_TRANSFER_WALK_DURATION) > 0 &&
      legs.subList(0, index).stream().anyMatch(Leg::isTransitLeg) &&
      legs.subList(index + 1, legs.size()).stream().anyMatch(Leg::isTransitLeg)
    );
  }

  private static McpRoutingResult.McpLeg mapLeg(Leg leg, boolean includeShapeData) {
    var from = leg.from();
    var to = leg.to();
    var route = leg.route();
    var trip = leg.trip();
    var agency = leg.agency();
    var coordinate = from.coordinate;
    var fromPlace = new McpRoutingResult.McpPlace(
      from.stop == null ? null : from.stop.getId().toString(),
      placeName(from),
      coordinate == null ? null : coordinate.latitude(),
      coordinate == null ? null : coordinate.longitude(),
      from.stop == null ? null : from.stop.getPlatformCode(),
      timeZone(from, agency)
    );
    coordinate = to.coordinate;
    var toPlace = new McpRoutingResult.McpPlace(
      to.stop == null ? null : to.stop.getId().toString(),
      placeName(to),
      coordinate == null ? null : coordinate.latitude(),
      coordinate == null ? null : coordinate.longitude(),
      to.stop == null ? null : to.stop.getPlatformCode(),
      timeZone(to, agency)
    );
    var geometry = leg.legGeometry();
    var shape = includeShapeData && geometry != null ? EncodedPolyline.of(geometry).points() : null;
    var mode = leg instanceof TransitLeg transitLeg
      ? transitLeg.mode().name()
      : leg.isWalkingLeg()
        ? "TRANSFER"
        : "WALK";
    return new McpRoutingResult.McpLeg(
      leg.isTransitLeg(),
      mode,
      leg.startTime().toInstant().toString(),
      leg.endTime().toInstant().toString(),
      leg.duration().toSeconds(),
      distanceMeters(leg),
      fromPlace,
      toPlace,
      route == null ? null : route.getId().toString(),
      trip == null ? null : trip.getId().toString(),
      route == null ? null : route.getName().toString(),
      agency == null ? null : agency.getName(),
      shape
    );
  }

  private static String placeName(Place place) {
    var childName = place.name == null ? null : place.name.toString();
    if (place.stop == null) {
      return childName;
    }

    var parentStation = place.stop.getParentStation();
    I18NString stationName = parentStation == null ? null : parentStation.getName();
    if (stationName == null) {
      return childName;
    }

    var parent = stationName.toString().strip();
    var child = childName == null ? "" : childName.strip();
    if (parent.isEmpty()) {
      return childName;
    }
    if (child.isEmpty() || child.equalsIgnoreCase(parent)) {
      return parent;
    }

    if (
      child.regionMatches(true, 0, parent, 0, parent.length()) &&
      child.length() > parent.length() &&
      isNameSeparator(child.charAt(parent.length()))
    ) {
      child = child.substring(parent.length()).stripLeading();
      while (!child.isEmpty() && isNameSeparator(child.charAt(0))) {
        child = child.substring(1).stripLeading();
      }
      if (child.isEmpty()) {
        return parent;
      }
    }
    return parent + ", " + child;
  }

  private static boolean isNameSeparator(char character) {
    return (
      Character.isWhitespace(character) ||
      character == ',' ||
      character == ';' ||
      character == ':' ||
      character == '/' ||
      character == '(' ||
      character == ')' ||
      character == '-'
    );
  }

  private static String timeZone(Place place, Agency agency) {
    if (place.stop == null) {
      return null;
    }

    var parentStation = place.stop.getParentStation();
    var timeZone = parentStation == null ? place.stop.getTimeZone() : parentStation.getTimezone();
    if (timeZone == null && agency != null) {
      timeZone = agency.getTimezone();
    }
    return timeZone == null ? null : timeZone.getId();
  }

  private static double distanceMeters(Leg leg) {
    var distanceMeters = leg.distanceMeters();
    if (Double.isFinite(distanceMeters) && distanceMeters >= 0) {
      return distanceMeters;
    }

    var from = leg.from().coordinate;
    var to = leg.to().coordinate;
    if (from != null && to != null) {
      return SphericalDistanceLibrary.distance(
        from.latitude(),
        from.longitude(),
        to.latitude(),
        to.longitude()
      );
    }
    return distanceMeters;
  }

  private static McpRoutingResult.McpRoutingError mapError(RoutingError error) {
    return new McpRoutingResult.McpRoutingError(
      error.code.name(),
      error.inputField == null ? null : error.inputField.name()
    );
  }
}
