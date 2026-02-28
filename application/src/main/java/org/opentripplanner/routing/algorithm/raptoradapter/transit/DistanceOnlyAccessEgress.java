package org.opentripplanner.routing.algorithm.raptoradapter.transit;

import java.util.Objects;
import org.opentripplanner.framework.model.TimeAndCost;
import org.opentripplanner.raptor.api.model.RaptorConstants;
import org.opentripplanner.street.search.state.State;

/**
 * Distance-only access/egress candidate used when no street-level path/state is available.
 */
public final class DistanceOnlyAccessEgress implements RoutingAccessEgress {

  private final int stop;
  private final int anchorStop;
  private final int durationInSeconds;
  private final int generalizedCost;
  private final int distanceMeters;
  private final TimeAndCost penalty;

  public DistanceOnlyAccessEgress(
    int stop,
    int anchorStop,
    int durationInSeconds,
    int generalizedCost,
    int distanceMeters,
    TimeAndCost penalty
  ) {
    this.stop = stop;
    this.anchorStop = anchorStop;
    this.durationInSeconds = durationInSeconds;
    this.generalizedCost = generalizedCost;
    this.distanceMeters = distanceMeters;
    this.penalty = Objects.requireNonNull(penalty);
  }

  public int anchorStop() {
    return anchorStop;
  }

  public int distanceMeters() {
    return distanceMeters;
  }

  @Override
  public RoutingAccessEgress withPenalty(TimeAndCost penalty) {
    return new DistanceOnlyAccessEgress(
      stop,
      anchorStop,
      durationInSeconds,
      generalizedCost + penalty.cost().toCentiSeconds(),
      distanceMeters,
      penalty
    );
  }

  @Override
  public State getLastState() {
    throw new UnsupportedOperationException("Distance-only access/egress has no street state");
  }

  @Override
  public boolean isWalkOnly() {
    return true;
  }

  @Override
  public TimeAndCost penalty() {
    return penalty;
  }

  @Override
  public int stop() {
    return stop;
  }

  @Override
  public int c1() {
    return generalizedCost;
  }

  @Override
  public int durationInSeconds() {
    return durationInSeconds;
  }

  @Override
  public int timePenalty() {
    return penalty.isZero() ? RaptorConstants.TIME_NOT_SET : penalty.timeInSeconds();
  }

  @Override
  public int earliestDepartureTime(int requestedDepartureTime) {
    return requestedDepartureTime;
  }

  @Override
  public int latestArrivalTime(int requestedArrivalTime) {
    return requestedArrivalTime;
  }

  @Override
  public boolean hasOpeningHours() {
    return false;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof DistanceOnlyAccessEgress that)) {
      return false;
    }
    return (
      stop == that.stop &&
      anchorStop == that.anchorStop &&
      durationInSeconds == that.durationInSeconds &&
      generalizedCost == that.generalizedCost &&
      distanceMeters == that.distanceMeters &&
      penalty.equals(that.penalty)
    );
  }

  @Override
  public int hashCode() {
    return Objects.hash(
      stop,
      anchorStop,
      durationInSeconds,
      generalizedCost,
      distanceMeters,
      penalty
    );
  }
}
