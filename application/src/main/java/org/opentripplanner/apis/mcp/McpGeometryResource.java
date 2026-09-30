package org.opentripplanner.apis.mcp;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** HTTP endpoint for map clients to fetch geometries referenced by MCP route results. */
@Path("/route-geometry")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public final class McpGeometryResource {

  private static final int MAX_GEOMETRY_IDS = 100;

  @POST
  public Response getGeometries(Map<String, Object> request) {
    if (request == null || !(request.get("geometryIds") instanceof List<?> geometryIds)) {
      return Response.status(Response.Status.BAD_REQUEST).build();
    }
    if (geometryIds.size() > MAX_GEOMETRY_IDS) {
      return Response.status(Response.Status.BAD_REQUEST).build();
    }

    var geometries = new LinkedHashMap<String, String>();
    for (var value : geometryIds) {
      if (!(value instanceof String id) || !isUuid(id)) {
        return Response.status(Response.Status.BAD_REQUEST).build();
      }
      var geometry = McpGeometryStore.get(id);
      if (geometry != null) {
        geometries.put(id, geometry);
      }
    }
    return Response.ok(Map.of("geometries", geometries)).build();
  }

  private static boolean isUuid(String id) {
    try {
      UUID.fromString(id);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
