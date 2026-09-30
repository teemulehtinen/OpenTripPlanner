package org.opentripplanner.apis.mcp;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Short-lived storage for route leg geometries fetched separately from MCP tool results. */
public final class McpGeometryStore {

  private static final Cache<String, String> GEOMETRIES = CacheBuilder.newBuilder()
    .maximumSize(10_000)
    .expireAfterWrite(30, TimeUnit.MINUTES)
    .build();

  private McpGeometryStore() {}

  public static String store(String geometry) {
    var id = UUID.randomUUID().toString();
    GEOMETRIES.put(id, geometry);
    return id;
  }

  public static String get(String id) {
    return GEOMETRIES.getIfPresent(id);
  }
}
