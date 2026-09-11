/** A latitude/longitude pair accepted by the MCP routing tool. */
package org.opentripplanner.apis.mcp;

import org.opentripplanner.street.geometry.WgsCoordinate;

/** A latitude/longitude pair accepted by the MCP routing tool. */
public record McpCoordinate(double latitude, double longitude) {
  public McpCoordinate {
    new WgsCoordinate(latitude, longitude);
  }

  WgsCoordinate toWgsCoordinate() {
    return new WgsCoordinate(latitude, longitude);
  }
}
