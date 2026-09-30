package org.opentripplanner.apis.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpGeometryResourceTest {

  @Test
  void returnsCachedGeometryForOpaqueId() throws Exception {
    var geometryId = McpGeometryStore.store("encoded-polyline");
    var response = new McpGeometryResource().getGeometries(
      Map.of("geometryIds", List.of(geometryId))
    );
    var json = new ObjectMapper().readTree(
      new ObjectMapper().writeValueAsString(response.getEntity())
    );

    assertEquals(200, response.getStatus());
    assertEquals("encoded-polyline", json.path("geometries").path(geometryId).asText());
  }

  @Test
  void rejectsMalformedGeometryIds() {
    var response = new McpGeometryResource().getGeometries(
      Map.of("geometryIds", List.of("not-an-id"))
    );

    assertEquals(400, response.getStatus());
  }
}
