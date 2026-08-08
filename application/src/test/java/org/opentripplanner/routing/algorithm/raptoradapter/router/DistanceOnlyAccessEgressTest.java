package org.opentripplanner.routing.algorithm.raptoradapter.router;

import static com.google.common.truth.Truth.assertThat;
import static org.opentripplanner.transit.model._data.TimetableRepositoryForTest.agency;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentripplanner.TestOtpModel;
import org.opentripplanner.TestServerContext;
import org.opentripplanner.model.GenericLocation;
import org.opentripplanner.model.calendar.CalendarServiceData;
import org.opentripplanner.model.plan.Itinerary;
import org.opentripplanner.routing.algorithm.GraphRoutingTest;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.routing.api.response.RoutingResponse;
import org.opentripplanner.standalone.api.OtpServerRequestContext;
import org.opentripplanner.street.geometry.WgsCoordinate;
import org.opentripplanner.street.model.StreetMode;
import org.opentripplanner.street.model.vertex.TransitStopVertex;
import org.opentripplanner.transfer.regular.model.PathTransfer;
import org.opentripplanner.transit.model._data.TimetableRepositoryForTest;
import org.opentripplanner.transit.model.basic.TransitMode;
import org.opentripplanner.transit.model.network.StopPattern;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.transit.model.timetable.ScheduledTripTimes;

/**
 * Integration test for TransitRouter with distance-only access/egress when no
 * street graph exists.
 *
 * This test verifies that when:
 * - The graph has no street network (graph.hasStreets = false)
 * - The distanceOnlyStationTransfers feature is enabled
 * Then:
 * - TransitRouter creates DistanceOnlyAccessEgress candidates
 * - Routing produces valid itineraries using straight-line distance between
 * stops
 */
class DistanceOnlyAccessEgressTest extends GraphRoutingTest {

  private static final WgsCoordinate STOP_A = new WgsCoordinate(47.500, 19.0);
  private static final WgsCoordinate STOP_B = new WgsCoordinate(47.550, 19.0);
  private static final WgsCoordinate STOP_C = new WgsCoordinate(47.5501, 19.0);
  private static final WgsCoordinate STOP_X = new WgsCoordinate(47.500, 19.0001);

  private OtpServerRequestContext serverContext;
  private TransitStopVertex stopA;
  private TransitStopVertex stopB;
  private TransitStopVertex stopC;
  private TransitStopVertex stopX;

  @BeforeEach
  void setUp() {
    TestOtpModel model = modelOf(
      new Builder() {
        @Override
        public void build() {
          // Create transit stops at different locations (no street network needed)
          stopA = stop("A", STOP_A);
          stopB = stop("B", STOP_B);
          stopC = stop("C", STOP_C);
          stopX = stop("X", STOP_X);

          // Create a transit route connecting stops A and B
          var agency = agency("Agency");
          timetableRepository().addAgency(agency);
          var serviceId = TimetableRepositoryForTest.id("WEEKDAY");

          // Register service code BEFORE building trip pattern
          LocalDate serviceDate = LocalDate.of(2024, 1, 15);
          int serviceCode = 1;
          timetableRepository().getServiceCodes().put(serviceId, serviceCode);

          var routeAB = route("R1", TransitMode.RAIL, agency);
          tripPattern(
            TripPattern.of(TimetableRepositoryForTest.id("TP1"))
              .withRoute(routeAB)
              .withStopPattern(new StopPattern(List.of(st(stopA), st(stopB))))
              .withScheduledTimeTableBuilder(builder ->
                builder.addTripTimes(
                  ScheduledTripTimes.of()
                    .withTrip(
                      TimetableRepositoryForTest.trip("T1").withServiceId(serviceId).build()
                    )
                    .withServiceCode(serviceCode)
                    // The request is evaluated in the transit timezone, so keep the first
                    // departure after the search start to ensure Raptor can find it.
                    .withDepartureTimes("10:00 11:00")
                    .build()
                )
              )
              .build()
          );

          // Configure calendar so trips run on 2024-01-15
          CalendarServiceData calendarServiceData = new CalendarServiceData();
          calendarServiceData.putServiceDatesForServiceId(serviceId, List.of(serviceDate));
          timetableRepository().updateCalendarServiceData(calendarServiceData);
        }
      }
    );

    // Use straight-line distance between stops instead of street network
    model.graph().hasStreets = false;

    // Add a direct transfers by distance manually
    Multimap<StopLocation, PathTransfer> transfersByStop = HashMultimap.create();
    connect(transfersByStop, model, stopB, stopC, 50);
    connect(transfersByStop, model, stopX, stopA, 50);
    model.transferRepository().addAllTransfersByStops(transfersByStop);

    // Index the model to ensure transit data is properly indexed
    model = model.index();

    serverContext = TestServerContext.createServerContext(
      model.graph(),
      model.timetableRepository(),
      model.transferRepository(),
      model.fareServiceFactory().makeFareService()
    );
  }

  private void connect(
    Multimap<StopLocation, PathTransfer> transfers,
    TestOtpModel model,
    TransitStopVertex a,
    TransitStopVertex b,
    float distance
  ) {
    var sr = model.timetableRepository().getSiteRepository();
    var sa = sr.getRegularStop(a.getId());
    var sb = sr.getRegularStop(b.getId());
    if (sa != null && sb != null) {
      transfers.put(sa, new PathTransfer(sa, sb, distance, null, EnumSet.of(StreetMode.WALK)));
      transfers.put(sb, new PathTransfer(sb, sa, distance, null, EnumSet.of(StreetMode.WALK)));
    }
  }

  @Test
  void testRoutingBaselineProducesResults() {
    /*
     * Debug: Check graph contents
     * System.out.println("DEBUG: Graph hasStreets: " +
     * serverContext.graph().hasStreets);
     * System.out.println("DEBUG: Vertex count: " +
     * serverContext.graph().getVertices().size());
     * System.out.println(
     * "DEBUG: Transit stop vertex count: " +
     * serverContext
     * .graph()
     * .getVertices()
     * .stream()
     * .filter(v -> v instanceof TransitStopVertex)
     * .count()
     * );
     * System.out.println("DEBUG: Graph edges count: " +
     * serverContext.graph().getEdges().size());
     * System.out.println("DEBUG: Graph edges: " +
     * serverContext.graph().getEdges());
     * System.out.println(
     * "DEBUG: Transit agencies: " +
     * serverContext
     * .transitService()
     * .listAgencies()
     * .stream()
     * .map(a -> a.getId())
     * .toList()
     * );
     * System.out.println(
     * "DEBUG: Transit routes: " +
     * serverContext
     * .transitService()
     * .listRoutes()
     * .stream()
     * .map(r -> r.getId())
     * .toList()
     * );
     * System.out.println(
     * "DEBUG: Trip patterns: " +
     * serverContext
     * .transitService()
     * .listTripPatterns()
     * .stream()
     * .map(p -> p.getId())
     * .toList()
     * );
     * System.out.println(
     * "DEBUG: Trips: " +
     * serverContext
     * .transitService()
     * .listTrips()
     * .stream()
     * .map(t -> t.getId())
     * .toList()
     * );
     * System.out.println(
     * "DEBUG: Test timeZone from transitService: " +
     * serverContext.transitService().getTimeZone()
     * );
     * System.out.println(
     * "DEBUG: Test trip patterns in transit service: " +
     * serverContext.transitService().listTripPatterns().size()
     * );
     * var raptorData = serverContext.transitService().getRaptorTransitData();
     * System.out.println(
     * "DEBUG: Test RaptorTransitData all trip patterns: " +
     * raptorData.getTripPatternsForRunningDate(LocalDate.of(2024, 1, 15)).size()
     * );
     */
    RouteRequest request = serverContext
      .defaultRouteRequest()
      .copyOf()
      .withDateTime(Instant.parse("2024-01-15T08:30:00Z"))
      .withFrom(new GenericLocation("A", stopA.getId(), STOP_A.latitude(), STOP_A.longitude()))
      .withTo(new GenericLocation("B", stopB.getId(), STOP_B.latitude(), STOP_B.longitude()))
      .withNumItineraries(1)
      .withSearchWindow(Duration.ofHours(1))
      .buildRequest();
    /*
     * Debug: Check request contents
     * System.out.println("DEBUG: Request from " + request.from() + " to " +
     * request.to());
     * System.out.println("DEBUG: Request dateTime: " + request.dateTime());
     * System.out.println("DEBUG: Request searchWindow: " + request.searchWindow());
     */

    RoutingResponse response = serverContext.routingService().route(request);
    /*
     * Debug: Check response contents
     * System.out.println(
     * "DEBUG: Response has errors " +
     * response
     * .getRoutingErrors()
     * .stream()
     * .map(e -> e.toString())
     * .collect(Collectors.joining(", "))
     * );
     * System.out.println(
     * "DEBUG: Response has " + response.getTripPlan().itineraries.size() +
     * " itineraries"
     * );
     * if (!response.getTripPlan().itineraries.isEmpty()) {
     * System.out.println(
     * "DEBUG: First itinerary has " +
     * response.getTripPlan().itineraries.get(0).legs().size() +
     * " legs"
     * );
     * } else {
     * System.out.println(
     * "DEBUG: No itineraries found. Response metadata: " + response.getMetadata()
     * );
     * }
     */

    assertThat(response.getTripPlan().itineraries).isNotEmpty();
    Itinerary itinerary = response.getTripPlan().itineraries.get(0);
    assertThat(itinerary.legs()).isNotEmpty();
  }

  @Test
  void testRoutingWithDistanceOnlyAccessEgressDisabledHasNoResults() {
    // When distance-only transfers are disabled (default),
    // and there's no street graph, routing without transit should fail
    RouteRequest request = serverContext
      .defaultRouteRequest()
      .copyOf()
      .withDateTime(Instant.parse("2024-01-15T08:30:00Z"))
      .withFrom(new GenericLocation("A", stopA.getId(), STOP_A.latitude(), STOP_A.longitude()))
      .withTo(new GenericLocation("C", stopC.getId(), STOP_C.latitude(), STOP_C.longitude()))
      .withNumItineraries(1)
      .withSearchWindow(Duration.ofHours(1))
      .withPreferences(pref ->
        pref.withStreet(street ->
          street.withAccessEgress(ae -> ae.withDistanceOnlyStationTransfers(false, 5000))
        )
      )
      .buildRequest();
    RoutingResponse response = serverContext.routingService().route(request);
    assertThat(response.getTripPlan().itineraries).isEmpty();
  }

  @Test
  void testRoutingWithDistanceOnlyAccessEgressEnabledProducesResults() {
    // When distance-only transfers are enabled and there's no street graph,
    // TransitRouter should create DistanceOnlyAccessEgress to nearyby stops
    RouteRequest request = serverContext
      .defaultRouteRequest()
      .copyOf()
      .withDateTime(Instant.parse("2024-01-15T08:30:00Z"))
      .withFrom(new GenericLocation("A", stopA.getId(), STOP_A.latitude(), STOP_A.longitude()))
      .withTo(new GenericLocation("C", stopC.getId(), STOP_C.latitude(), STOP_C.longitude()))
      .withNumItineraries(1)
      .withSearchWindow(Duration.ofHours(1))
      .withPreferences(pref ->
        pref.withStreet(street ->
          street.withAccessEgress(ae -> ae.withDistanceOnlyStationTransfers(true, 10000))
        )
      )
      .buildRequest();

    RoutingResponse response = serverContext.routingService().route(request);
    assertThat(response.getTripPlan().itineraries).isNotEmpty();
    Itinerary itinerary = response.getTripPlan().itineraries.get(0);
    assertThat(itinerary.legs()).isNotEmpty();
  }

  @Test
  void testRoutingWithDistanceOnlyFirstProducesResults() {
    RouteRequest request = serverContext
      .defaultRouteRequest()
      .copyOf()
      .withDateTime(Instant.parse("2024-01-15T07:00:00Z"))
      .withFrom(new GenericLocation("X", stopX.getId(), STOP_X.latitude(), STOP_X.longitude()))
      .withTo(new GenericLocation("C", stopC.getId(), STOP_C.latitude(), STOP_C.longitude()))
      .withNumItineraries(1)
      .withSearchWindow(Duration.ofHours(3))
      .withPreferences(pref ->
        pref.withStreet(street ->
          street.withAccessEgress(ae -> ae.withDistanceOnlyStationTransfers(true, 10000))
        )
      )
      .buildRequest();

    RoutingResponse response = serverContext.routingService().route(request);
    assertThat(response.getTripPlan().itineraries).isNotEmpty();
    Itinerary itinerary = response.getTripPlan().itineraries.get(0);
    assertThat(itinerary.legs()).isNotEmpty();
  }

  @Test
  void testRoutingWithDistanceOnlyAccessEgressMaxDistanceLimited() {
    // When max distance is smaller than nearby stop distance
    // the system should be unable to create access to nearby stops
    RouteRequest request = serverContext
      .defaultRouteRequest()
      .copyOf()
      .withDateTime(Instant.parse("2024-01-15T08:30:00Z"))
      .withFrom(new GenericLocation("A", stopA.getId(), STOP_A.latitude(), STOP_A.longitude()))
      .withTo(new GenericLocation("C", stopC.getId(), STOP_C.latitude(), STOP_C.longitude()))
      .withNumItineraries(1)
      .withSearchWindow(Duration.ofHours(1))
      .withPreferences(pref ->
        pref.withStreet(street ->
          street.withAccessEgress(ae -> ae.withDistanceOnlyStationTransfers(true, 10))
        )
      )
      .buildRequest();

    RoutingResponse response = serverContext.routingService().route(request);
    assertThat(response.getTripPlan().itineraries).isEmpty();
  }
}
