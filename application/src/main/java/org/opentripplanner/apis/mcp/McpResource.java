package org.opentripplanner.apis.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.standalone.api.OtpServerRequestContext;

/** Minimal MCP JSON-RPC endpoint for the initial routing tools. */
@Path("/mcp")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public final class McpResource {

  private static final String JSON_RPC_VERSION = "2.0";
  private static final String ROUTE_TOOL = "route";
  private static final String NEAREST_STOP_TOOL = "find_nearest_stop";

  private final McpRoutingService routingService;
  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  public McpResource(@Context OtpServerRequestContext serverContext) {
    this.routingService = new McpRoutingService(serverContext);
  }

  @POST
  public Response handle(
    Map<String, Object> request,
    @HeaderParam("MCP-Protocol-Version") String protocolVersion,
    @HeaderParam("Origin") String origin,
    @HeaderParam("Accept") String accept
  ) {
    if (!isAllowedOrigin(origin)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    if (!acceptsMcpResponse(accept)) {
      return Response.status(Response.Status.NOT_ACCEPTABLE).build();
    }
    if (request == null) {
      return Response.status(Response.Status.BAD_REQUEST).build();
    }
    Object id = request.get("id");
    String method = request.get("method") instanceof String value ? value : null;
    if (!JSON_RPC_VERSION.equals(request.get("jsonrpc")) || method == null) {
      return jsonRpcError(id, -32600, "Invalid Request");
    }
    if (!isSupportedProtocolVersion(protocolVersion)) {
      return jsonRpcError(id, -32602, "Unsupported MCP protocol version.");
    }

    // Notifications do not receive JSON-RPC response bodies.
    if (
      id == null &&
      (method.equals("notifications/initialized") || method.startsWith("notifications/"))
    ) {
      return Response.status(Response.Status.ACCEPTED).build();
    }

    try {
      return switch (method) {
        case "initialize" -> jsonRpcResult(
          id,
          Map.of(
            "protocolVersion",
            "2025-06-18",
            "capabilities",
            Map.of("tools", Map.of()),
            "serverInfo",
            Map.of("name", "opentripplanner", "version", "2.x")
          )
        );
        case "tools/list" -> jsonRpcResult(id, Map.of("tools", toolDefinitions()));
        case "tools/call" -> callTool(id, request.get("params"));
        default -> jsonRpcError(id, -32601, "Method not found: " + method);
      };
    } catch (IllegalArgumentException e) {
      return jsonRpcError(id, -32602, e.getMessage());
    } catch (RuntimeException e) {
      return jsonRpcError(id, -32603, "MCP tool execution failed");
    }
  }

  @GET
  public Response get() {
    return Response.status(Response.Status.METHOD_NOT_ALLOWED).build();
  }

  @DELETE
  public Response delete() {
    return Response.status(Response.Status.METHOD_NOT_ALLOWED).build();
  }

  private Response callTool(Object id, Object rawParams) {
    if (!(rawParams instanceof Map<?, ?> params)) {
      return jsonRpcError(id, -32602, "Tool parameters are required.");
    }
    var name = requireString(params, "name");
    if (!name.equals(ROUTE_TOOL) && !name.equals(NEAREST_STOP_TOOL)) {
      return jsonRpcError(id, -32602, "Unknown tool: " + name);
    }
    var arguments = params.get("arguments");
    if (!(arguments instanceof Map<?, ?> rawArguments)) {
      return jsonRpcError(id, -32602, "Tool arguments are required.");
    }
    @SuppressWarnings("unchecked")
    var typedArguments = (Map<String, Object>) rawArguments;
    try {
      Object result = switch (name) {
        case ROUTE_TOOL -> routingService.route(parseRouteRequest(typedArguments));
        case NEAREST_STOP_TOOL -> Map.of(
          "stops",
          routingService.findNearestStops(parseNearestStopRequest(typedArguments))
        );
        default -> throw new IllegalStateException("Unknown tool: " + name);
      };
      return toolResult(id, result, false);
    } catch (IllegalArgumentException e) {
      return toolResult(id, Map.of("message", e.getMessage()), true);
    } catch (RuntimeException e) {
      return toolResult(id, Map.of("message", "MCP tool execution failed"), true);
    }
  }

  private Response toolResult(Object id, Object result, boolean isError) {
    return jsonRpcResult(
      id,
      Map.of(
        "content",
        List.of(Map.of("type", "text", "text", serialize(result))),
        "structuredContent",
        result,
        "isError",
        isError
      )
    );
  }

  private static boolean isAllowedOrigin(String origin) {
    if (origin == null || origin.isBlank()) {
      return true;
    }
    try {
      var uri = new URI(origin);
      var host = uri.getHost();
      return (
        ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) &&
        ("localhost".equalsIgnoreCase(host) ||
          "127.0.0.1".equals(host) ||
          "[::1]".equals(host) ||
          "::1".equals(host)) &&
        uri.getPath().isEmpty() &&
        uri.getQuery() == null &&
        uri.getFragment() == null
      );
    } catch (URISyntaxException e) {
      return false;
    }
  }

  private static boolean acceptsMcpResponse(String accept) {
    if (accept == null || accept.isBlank()) {
      return false;
    }
    var normalized = accept.toLowerCase(Locale.ROOT);
    return (
      normalized.contains(MediaType.APPLICATION_JSON) &&
      normalized.contains(MediaType.SERVER_SENT_EVENTS)
    );
  }

  private McpNearestStopRequest parseNearestStopRequest(Map<String, Object> arguments) {
    var coordinate = new McpCoordinate(
      parseDouble(arguments, "latitude"),
      parseDouble(arguments, "longitude")
    );
    return new McpNearestStopRequest(
      coordinate,
      parseDouble(arguments, "radiusMeters"),
      parseInteger(arguments, "maxResults")
    );
  }

  private static List<Map<String, Object>> toolDefinitions() {
    return List.of(
      Map.of(
        "name",
        ROUTE_TOOL,
        "description",
        "Route between transit stop IDs with optional ordered pass-through stops.",
        "inputSchema",
        Map.of(
          "type",
          "object",
          "properties",
          Map.of(
            "origin",
            Map.of("type", "string", "description", "Feed-scoped stop ID."),
            "destination",
            Map.of("type", "string", "description", "Feed-scoped stop ID."),
            "intermediateStops",
            Map.of("type", "array", "items", Map.of("type", "string")),
            "time",
            Map.of("type", "string", "format", "date-time"),
            "timeDirection",
            Map.of("type", "string", "enum", List.of("DEPART_AT", "ARRIVE_BY")),
            "searchWindowSeconds",
            Map.of("type", "integer", "minimum", 0),
            "maxItineraries",
            Map.of("type", "integer", "minimum", 1, "maximum", 10),
            "locale",
            Map.of("type", "string")
          ),
          "required",
          List.of(
            "origin",
            "destination",
            "time",
            "timeDirection",
            "searchWindowSeconds",
            "maxItineraries"
          )
        ),
        "outputSchema",
        Map.of("type", "object")
      ),
      Map.of(
        "name",
        NEAREST_STOP_TOOL,
        "description",
        "Find transit stops near a coordinate using straight-line distance when needed.",
        "inputSchema",
        Map.of(
          "type",
          "object",
          "properties",
          Map.of(
            "latitude",
            Map.of("type", "number", "minimum", -90, "maximum", 90),
            "longitude",
            Map.of("type", "number", "minimum", -180, "maximum", 180),
            "radiusMeters",
            Map.of("type", "number", "exclusiveMinimum", 0),
            "maxResults",
            Map.of("type", "integer", "minimum", 1, "maximum", 20)
          ),
          "required",
          List.of("latitude", "longitude", "radiusMeters", "maxResults")
        ),
        "outputSchema",
        Map.of(
          "type",
          "object",
          "properties",
          Map.of("stops", Map.of("type", "array", "items", Map.of("type", "object"))),
          "required",
          List.of("stops")
        )
      )
    );
  }

  private static boolean isSupportedProtocolVersion(String protocolVersion) {
    return (
      protocolVersion == null ||
      protocolVersion.equals("2025-06-18") ||
      protocolVersion.equals("2025-03-26")
    );
  }

  private Response jsonRpcResult(Object id, Object result) {
    var response = new LinkedHashMap<String, Object>();
    response.put("jsonrpc", JSON_RPC_VERSION);
    response.put("id", id);
    response.put("result", result);
    return Response.ok(response).build();
  }

  private Response jsonRpcError(Object id, int code, String message) {
    var response = new LinkedHashMap<String, Object>();
    response.put("jsonrpc", JSON_RPC_VERSION);
    response.put("id", id);
    response.put(
      "error",
      Map.of("code", code, "message", message == null ? "Invalid request" : message)
    );
    return Response.ok(response).build();
  }

  private String serialize(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Unable to serialize MCP result", e);
    }
  }

  static McpRouteRequest parseRouteRequest(Map<String, Object> arguments) {
    return new McpRouteRequest(
      parseStopId(arguments, "origin"),
      parseStopId(arguments, "destination"),
      parseStopIds(arguments.get("intermediateStops")),
      parseEnum(arguments, "timeDirection", McpTimeDirection.class),
      parseInstant(arguments, "time"),
      parseSearchWindow(arguments.get("searchWindowSeconds")),
      parseInteger(arguments, "maxItineraries"),
      parseLocale(arguments.get("locale"))
    );
  }

  private static FeedScopedId parseStopId(Map<String, Object> arguments, String name) {
    var value = arguments.get(name);
    if (!(value instanceof String stopId)) {
      throw new IllegalArgumentException("'%s' must be a feed-scoped stop ID.".formatted(name));
    }
    try {
      var parsed = FeedScopedId.parse(stopId);
      if (parsed == null) {
        throw new IllegalArgumentException("'%s' must be a feed-scoped stop ID.".formatted(name));
      }
      return parsed;
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Invalid '%s' stop ID: %s".formatted(name, stopId), e);
    }
  }

  private static String requireString(Map<?, ?> values, String name) {
    var value = values.get(name);
    if (!(value instanceof String string) || string.isBlank()) {
      throw new IllegalArgumentException("'%s' must be a non-empty string.".formatted(name));
    }
    return string;
  }

  private static double parseDouble(Map<String, Object> arguments, String name) {
    var value = arguments.get(name);
    if (!(value instanceof Number number)) {
      throw new IllegalArgumentException("'%s' must be a number.".formatted(name));
    }
    var valueAsDouble = number.doubleValue();
    if (!Double.isFinite(valueAsDouble)) {
      throw new IllegalArgumentException("'%s' must be finite.".formatted(name));
    }
    return valueAsDouble;
  }

  private static List<FeedScopedId> parseStopIds(Object value) {
    if (value == null) {
      return List.of();
    }
    if (!(value instanceof List<?> values)) {
      throw new IllegalArgumentException("'intermediateStops' must be a list of stop IDs.");
    }
    return values
      .stream()
      .map(value1 -> {
        if (!(value1 instanceof String stopId)) {
          throw new IllegalArgumentException("Every intermediate stop must be a stop ID.");
        }
        var parsed = FeedScopedId.parse(stopId);
        if (parsed == null) {
          throw new IllegalArgumentException("Every intermediate stop must be a stop ID.");
        }
        return parsed;
      })
      .toList();
  }

  private static Instant parseInstant(Map<String, Object> arguments, String name) {
    var value = arguments.get(name);
    if (!(value instanceof String timestamp)) {
      throw new IllegalArgumentException("'%s' must be an ISO-8601 timestamp.".formatted(name));
    }
    try {
      return Instant.parse(timestamp);
    } catch (RuntimeException e) {
      throw new IllegalArgumentException(
        "Invalid '%s' timestamp: %s".formatted(name, timestamp),
        e
      );
    }
  }

  private static Duration parseSearchWindow(Object value) {
    if (!(value instanceof Number seconds)) {
      throw new IllegalArgumentException("'searchWindowSeconds' must be a number.");
    }
    var valueAsDouble = seconds.doubleValue();
    if (!Double.isFinite(valueAsDouble) || Math.rint(valueAsDouble) != valueAsDouble) {
      throw new IllegalArgumentException("'searchWindowSeconds' must be a finite integer.");
    }
    try {
      return Duration.ofSeconds(seconds.longValue());
    } catch (ArithmeticException e) {
      throw new IllegalArgumentException("'searchWindowSeconds' is out of range.", e);
    }
  }

  private static int parseInteger(Map<String, Object> arguments, String name) {
    var value = arguments.get(name);
    if (!(value instanceof Number number)) {
      throw new IllegalArgumentException("'%s' must be a number.".formatted(name));
    }
    var valueAsDouble = number.doubleValue();
    if (
      !Double.isFinite(valueAsDouble) ||
      Math.rint(valueAsDouble) != valueAsDouble ||
      valueAsDouble < Integer.MIN_VALUE ||
      valueAsDouble > Integer.MAX_VALUE
    ) {
      throw new IllegalArgumentException("'%s' must be an integer.".formatted(name));
    }
    return number.intValue();
  }

  private static <T extends Enum<T>> T parseEnum(
    Map<String, Object> arguments,
    String name,
    Class<T> enumType
  ) {
    var value = arguments.get(name);
    if (!(value instanceof String enumValue)) {
      throw new IllegalArgumentException("'%s' must be a string.".formatted(name));
    }
    try {
      return Enum.valueOf(enumType, enumValue);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Invalid '%s' value: %s".formatted(name, enumValue), e);
    }
  }

  private static Locale parseLocale(Object value) {
    if (value == null) {
      return Locale.ENGLISH;
    }
    if (!(value instanceof String locale)) {
      throw new IllegalArgumentException("'locale' must be a language tag.");
    }
    var parsed = Locale.forLanguageTag(locale);
    if (parsed.equals(Locale.ROOT) || parsed.toLanguageTag().equals("und")) {
      throw new IllegalArgumentException("'locale' must be a valid language tag.");
    }
    return parsed;
  }
}
